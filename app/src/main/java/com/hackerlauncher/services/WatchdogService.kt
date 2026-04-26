package com.hackerlauncher.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.hackerlauncher.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * WatchdogService - Service watchdog that restarts dead services.
 *
 * Periodically checks if critical services (DaemonService, HackerForegroundService,
 * AppLockService, OverlayService) are running every 60 seconds. If any service
 * is found dead, it restarts it. Uses AlarmManager for exact alarm scheduling
 * and a BroadcastReceiver for self-healing on service crash.
 */
class WatchdogService : Service() {

    companion object {
        const val TAG = "WatchdogService"
        const val CHANNEL_ID = "watchdog_service"
        const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.hackerlauncher.ACTION_START_WATCHDOG"
        const val ACTION_STOP = "com.hackerlauncher.ACTION_STOP_WATCHDOG"
        const val ACTION_CHECK_SERVICES = "com.hackerlauncher.ACTION_CHECK_SERVICES"
        const val ACTION_SELF_HEAL = "com.hackerlauncher.ACTION_SELF_HEAL"
        const val ACTION_ALARM_CHECK = "com.hackerlauncher.ACTION_ALARM_WATCHDOG"

        const val CHECK_INTERVAL_MS = 60_000L
        const val ALARM_INTERVAL_MS = 120_000L

        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var watchdogJob: Job? = null
    private var notificationUpdateJob: Job? = null
    private var restartCount = 0
    private var lastCheckTime: Long = 0L

    private data class WatchedService(
        val serviceClass: Class<*>,
        val name: String,
        val companionIsRunning: Boolean = true
    )

    private val watchedServices = listOf(
        WatchedService(DaemonService::class.java, "DaemonService"),
        WatchedService(KeepAliveService::class.java, "KeepAliveService"),
        WatchedService(NetworkMonitorService::class.java, "NetworkMonitorService"),
        WatchedService(LocationTrackerService::class.java, "LocationTrackerService"),
        WatchedService(ProcessMonitorService::class.java, "ProcessMonitorService"),
        WatchedService(SystemMonitorService::class.java, "SystemMonitorService")
    )

    private val selfHealReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SELF_HEAL -> {
                    Logger.w(TAG, "Self-heal triggered, restarting watchdog")
                    context?.let {
                        val restartIntent = Intent(it, WatchdogService::class.java).apply {
                            action = ACTION_START
                        }
                        it.startForegroundService(restartIntent)
                    }
                }
                ACTION_ALARM_CHECK -> {
                    Logger.d(TAG, "Alarm check triggered")
                    if (!isRunning) {
                        Logger.w(TAG, "WatchdogService was dead, restarting via alarm")
                        context?.let {
                            val restartIntent = Intent(it, WatchdogService::class.java).apply {
                                action = ACTION_START
                            }
                            it.startForegroundService(restartIntent)
                        }
                    } else {
                        checkAllServices()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "WatchdogService onCreate")
        createNotificationChannel()
        registerReceivers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "WatchdogService onStartCommand, action=${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopWatchdog()
                return START_NOT_STICKY
            }
            ACTION_CHECK_SERVICES -> {
                checkAllServices()
            }
            ACTION_START, null -> {
                startWatchdog()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "WatchdogService onDestroy")
        stopWatchdog()
        try {
            unregisterReceiver(selfHealReceiver)
        } catch (e: Exception) {
            Logger.e(TAG, "Error unregistering receiver", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.w(TAG, "WatchdogService task removed, scheduling restart")
        scheduleRestartViaAlarm()
        super.onTaskRemoved(rootIntent)
    }

    private fun startWatchdog() {
        if (isRunning) {
            Logger.d(TAG, "WatchdogService already running")
            return
        }

        isRunning = true
        Logger.i(TAG, "WatchdogService starting...")

        startForeground(NOTIFICATION_ID, buildNotification("Starting watchdog..."))
        scheduleAlarmChecks()

        watchdogJob = serviceScope.launch {
            while (isActive) {
                checkAllServices()
                delay(CHECK_INTERVAL_MS)
            }
        }

        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                updateNotification()
                delay(CHECK_INTERVAL_MS)
            }
        }

        Logger.i(TAG, "WatchdogService started successfully")
    }

    private fun stopWatchdog() {
        Logger.i(TAG, "WatchdogService stopping...")
        isRunning = false
        watchdogJob?.cancel()
        notificationUpdateJob?.cancel()
        cancelAlarmChecks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Logger.i(TAG, "WatchdogService stopped")
    }

    private fun checkAllServices() {
        lastCheckTime = System.currentTimeMillis()
        Logger.d(TAG, "Checking all services...")

        for (watched in watchedServices) {
            val isAlive = checkServiceAlive(watched.serviceClass)
            if (!isAlive) {
                Logger.w(TAG, "${watched.name} is dead, attempting restart...")
                restartService(watched.serviceClass, watched.name)
            } else {
                Logger.d(TAG, "${watched.name} is alive")
            }
        }

        updateNotification()
    }

    private fun checkServiceAlive(serviceClass: Class<*>): Boolean {
        return try {
            val companion = serviceClass.getDeclaredField("Companion").get(null)
            val method = companion.javaClass.getMethod("isServiceRunning")
            method.invoke(companion) as? Boolean ?: false
        } catch (e: NoSuchFieldException) {
            // Service doesn't have a companion isServiceRunning, try other approaches
            try {
                val field = serviceClass.getDeclaredField("isRunning")
                field.isAccessible = true
                field.getBoolean(null)
            } catch (e2: Exception) {
                Logger.d(TAG, "Cannot check ${serviceClass.simpleName}, assuming dead")
                false
            }
        } catch (e: Exception) {
            Logger.d(TAG, "Cannot check ${serviceClass.simpleName}: ${e.message}")
            false
        }
    }

    private fun restartService(serviceClass: Class<*>, name: String) {
        try {
            val intent = Intent(this, serviceClass)
            startForegroundService(intent)
            restartCount++
            Logger.i(TAG, "$name restart initiated (total restarts: $restartCount)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to restart $name", e)
        }
    }

    // ─── AlarmManager Scheduling ──────────────────────────────────────

    private fun scheduleAlarmChecks() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, WatchdogService::class.java).apply {
                action = ACTION_ALARM_CHECK
            }
            val pendingIntent = PendingIntent.getService(
                this, 2002, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }

            Logger.d(TAG, "Alarm check scheduled")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to schedule alarm check", e)
        }
    }

    private fun cancelAlarmChecks() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, WatchdogService::class.java).apply {
                action = ACTION_ALARM_CHECK
            }
            val pendingIntent = PendingIntent.getService(
                this, 2002, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            Logger.d(TAG, "Alarm checks cancelled")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to cancel alarm checks", e)
        }
    }

    private fun scheduleRestartViaAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, WatchdogService::class.java).apply {
                action = ACTION_SELF_HEAL
            }
            val pendingIntent = PendingIntent.getService(
                this, 2003, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = SystemClock.elapsedRealtime() + 10_000L // 10 seconds

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to schedule restart alarm", e)
        }
    }

    // ─── Receivers ────────────────────────────────────────────────────

    private fun registerReceivers() {
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_SELF_HEAL)
                addAction(ACTION_ALARM_CHECK)
            }
            registerReceiver(selfHealReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            Logger.d(TAG, "Receivers registered")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register receivers", e)
        }
    }

    // ─── Notification ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Watchdog Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "HackerLauncher watchdog service status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, WatchdogService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val checkIntent = Intent(this, WatchdogService::class.java).apply {
            action = ACTION_CHECK_SERVICES
        }
        val checkPendingIntent = PendingIntent.getService(
            this, 1, checkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HackerLauncher Watchdog")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_search,
                "Check Now",
                checkPendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        try {
            val aliveCount = watchedServices.count { checkServiceAlive(it.serviceClass) }
            val totalCount = watchedServices.size
            val lastCheck = if (lastCheckTime > 0) {
                val elapsed = (System.currentTimeMillis() - lastCheckTime) / 1000
                "${elapsed}s ago"
            } else "never"

            val text = "Services: $aliveCount/$totalCount alive | Restarts: $restartCount | Last check: $lastCheck"
            val notification = buildNotification(text)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update notification", e)
        }
    }
}
