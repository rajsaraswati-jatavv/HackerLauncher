package com.hackerlauncher.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hackerlauncher.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * KeepAliveService - Multi-layer keep-alive mechanism for HackerLauncher.
 *
 * Layer 1: Foreground notification with START_STICKY
 * Layer 2: AlarmManager repeating every 5 minutes
 * Layer 3: JobScheduler periodic job as backup
 * Layer 4: WorkManager PeriodicWorkRequest as another backup
 * Layer 5: BroadcastReceiver on various system events
 * Layer 6: ContentObserver watching Settings changes
 *
 * If any layer detects service dead, it restarts it.
 * Syncs keep-alive state across layers with minimal battery impact.
 */
class KeepAliveService : Service() {

    companion object {
        const val TAG = "KeepAliveService"
        const val CHANNEL_ID = "keepalive_service"
        const val NOTIFICATION_ID = 1003
        const val ACTION_START = "com.hackerlauncher.ACTION_START_KEEPALIVE"
        const val ACTION_STOP = "com.hackerlauncher.ACTION_STOP_KEEPALIVE"
        const val ACTION_ALARM_CHECK = "com.hackerlauncher.ACTION_ALARM_KEEPALIVE"
        const val ACTION_RESTART_SERVICES = "com.hackerlauncher.ACTION_RESTART_SERVICES"

        const val ALARM_INTERVAL_MS = 5 * 60_000L // 5 minutes
        const val JOB_ID = 3001
        const val WORK_NAME = "keepalive_work"

        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var monitorJob: Job? = null
    private var settingsObserver: ContentObserver? = null
    private var activeLayers = mutableSetOf<Int>()

    // Layer 5: System events receiver
    private val systemEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Logger.d(TAG, "System event received: $action")

            when (action) {
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF,
                ConnectivityManager.CONNECTIVITY_ACTION,
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> {
                    ensureServicesAlive(context)
                    rescheduleAllLayers(context)
                }
                ACTION_ALARM_CHECK -> {
                    Logger.d(TAG, "Alarm keep-alive check triggered")
                    ensureServicesAlive(context)
                    rescheduleAllLayers(context)
                }
                ACTION_RESTART_SERVICES -> {
                    Logger.d(TAG, "Restart services action received")
                    ensureServicesAlive(context)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "KeepAliveService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "KeepAliveService onStartCommand, action=${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopKeepAlive()
                return START_NOT_STICKY
            }
            ACTION_ALARM_CHECK -> {
                ensureServicesAlive(this)
                rescheduleAllLayers(this)
            }
            ACTION_START, null -> {
                startKeepAlive()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "KeepAliveService onDestroy")
        stopKeepAlive()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.w(TAG, "KeepAliveService task removed, rescheduling all layers")
        rescheduleAllLayers(this)
        super.onTaskRemoved(rootIntent)
    }

    // ─── Keep-Alive Lifecycle ─────────────────────────────────────────

    private fun startKeepAlive() {
        if (isRunning) {
            Logger.d(TAG, "KeepAliveService already running")
            return
        }

        isRunning = true
        Logger.i(TAG, "KeepAliveService starting all layers...")

        // Layer 1: Foreground notification + START_STICKY
        startForeground(NOTIFICATION_ID, buildNotification("Initializing keep-alive layers..."))
        activeLayers.add(1)
        Logger.i(TAG, "Layer 1 (Foreground + START_STICKY): ACTIVE")

        // Layer 2: AlarmManager
        setupAlarmManager()
        activeLayers.add(2)

        // Layer 3: JobScheduler
        setupJobScheduler()
        activeLayers.add(3)

        // Layer 4: WorkManager
        setupWorkManager()
        activeLayers.add(4)

        // Layer 5: System event receivers
        registerSystemEventReceivers()
        activeLayers.add(5)

        // Layer 6: ContentObserver
        setupContentObserver()
        activeLayers.add(6)

        // Internal monitor coroutine
        monitorJob = serviceScope.launch {
            while (isActive) {
                syncKeepAliveState()
                delay(60_000L) // Sync every minute
            }
        }

        updateNotification()
        Logger.i(TAG, "All ${activeLayers.size} keep-alive layers activated")
    }

    private fun stopKeepAlive() {
        Logger.i(TAG, "KeepAliveService stopping all layers...")
        isRunning = false
        monitorJob?.cancel()

        // Remove all layers
        cancelAlarmManager()
        cancelJobScheduler()
        cancelWorkManager()
        unregisterSystemEventReceivers()
        removeContentObserver()
        activeLayers.clear()

        stopForeground(STOP_FOREGROUND_REMOVE)
        Logger.i(TAG, "All keep-alive layers deactivated")
    }

    // ─── Layer 2: AlarmManager ────────────────────────────────────────

    private fun setupAlarmManager() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, KeepAliveService::class.java).apply {
                action = ACTION_ALARM_CHECK
            }
            val pendingIntent = PendingIntent.getService(
                this, 2004, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS

            // Use setExactAndAllowWhileIdle for reliability on Doze mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    ALARM_INTERVAL_MS,
                    pendingIntent
                )
            }

            Logger.i(TAG, "Layer 2 (AlarmManager): ACTIVE - interval ${ALARM_INTERVAL_MS / 1000}s")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to setup AlarmManager", e)
            activeLayers.remove(2)
        }
    }

    private fun cancelAlarmManager() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, KeepAliveService::class.java).apply {
                action = ACTION_ALARM_CHECK
            }
            val pendingIntent = PendingIntent.getService(
                this, 2004, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            Logger.d(TAG, "Layer 2 (AlarmManager): CANCELLED")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to cancel AlarmManager", e)
        }
    }

    // ─── Layer 3: JobScheduler ────────────────────────────────────────

    private fun setupJobScheduler() {
        try {
            val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

            // Cancel existing job if any
            jobScheduler.cancel(JOB_ID)

            val componentName = android.content.ComponentName(this, KeepAliveJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID, componentName)
                .setPeriodic(15 * 60_000L, 5 * 60_000L) // 15 min interval, 5 min flex
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .build()

            val result = jobScheduler.schedule(jobInfo)
            if (result == JobScheduler.RESULT_SUCCESS) {
                Logger.i(TAG, "Layer 3 (JobScheduler): ACTIVE - periodic 15min")
            } else {
                Logger.e(TAG, "Layer 3 (JobScheduler): FAILED to schedule")
                activeLayers.remove(3)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to setup JobScheduler", e)
            activeLayers.remove(3)
        }
    }

    private fun cancelJobScheduler() {
        try {
            val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            Logger.d(TAG, "Layer 3 (JobScheduler): CANCELLED")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to cancel JobScheduler", e)
        }
    }

    // ─── Layer 4: WorkManager ─────────────────────────────────────────

    private fun setupWorkManager() {
        try {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val workRequest = PeriodicWorkRequest.Builder(
                KeepAliveWorker::class.java,
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Logger.i(TAG, "Layer 4 (WorkManager): ACTIVE - periodic 15min")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to setup WorkManager", e)
            activeLayers.remove(4)
        }
    }

    private fun cancelWorkManager() {
        try {
            WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME)
            Logger.d(TAG, "Layer 4 (WorkManager): CANCELLED")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to cancel WorkManager", e)
        }
    }

    // ─── Layer 5: System Event Receivers ──────────────────────────────

    private fun registerSystemEventReceivers() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BOOT_COMPLETED)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(ACTION_ALARM_CHECK)
                addAction(ACTION_RESTART_SERVICES)
            }
            registerReceiver(systemEventsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            Logger.i(TAG, "Layer 5 (System Events): ACTIVE - ${filter.countActions()} events")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register system event receivers", e)
            activeLayers.remove(5)
        }
    }

    private fun unregisterSystemEventReceivers() {
        try {
            unregisterReceiver(systemEventsReceiver)
            Logger.d(TAG, "Layer 5 (System Events): CANCELLED")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to unregister system event receivers", e)
        }
    }

    // ─── Layer 6: ContentObserver ─────────────────────────────────────

    private fun setupContentObserver() {
        try {
            settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    Logger.d(TAG, "Layer 6 (ContentObserver): Settings change detected")
                    ensureServicesAlive(this@KeepAliveService)
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    Logger.d(TAG, "Layer 6 (ContentObserver): Settings change at $uri")
                    ensureServicesAlive(this@KeepAliveService)
                }
            }

            contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                settingsObserver!!
            )

            contentResolver.registerContentObserver(
                Settings.Secure.CONTENT_URI,
                true,
                settingsObserver!!
            )

            contentResolver.registerContentObserver(
                Settings.Global.CONTENT_URI,
                true,
                settingsObserver!!
            )

            Logger.i(TAG, "Layer 6 (ContentObserver): ACTIVE - watching Settings")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to setup ContentObserver", e)
            activeLayers.remove(6)
        }
    }

    private fun removeContentObserver() {
        try {
            settingsObserver?.let {
                contentResolver.unregisterContentObserver(it)
            }
            settingsObserver = null
            Logger.d(TAG, "Layer 6 (ContentObserver): CANCELLED")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to remove ContentObserver", e)
        }
    }

    // ─── Service Health ───────────────────────────────────────────────

    private fun ensureServicesAlive(context: Context?) {
        val criticalServices = listOf(
            DaemonService::class.java to "DaemonService",
            WatchdogService::class.java to "WatchdogService",
            NetworkMonitorService::class.java to "NetworkMonitorService",
            ProcessMonitorService::class.java to "ProcessMonitorService",
            SystemMonitorService::class.java to "SystemMonitorService"
        )

        for ((serviceClass, name) in criticalServices) {
            if (!checkServiceAlive(serviceClass)) {
                Logger.w(TAG, "$name is dead, restarting via keep-alive layer")
                try {
                    context?.let {
                        val intent = Intent(it, serviceClass)
                        it.startForegroundService(intent)
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to restart $name via keep-alive", e)
                }
            }
        }
    }

    private fun checkServiceAlive(serviceClass: Class<*>): Boolean {
        return try {
            val companion = serviceClass.getDeclaredField("Companion").get(null)
            val method = companion.javaClass.getMethod("isServiceRunning")
            method.invoke(companion) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun syncKeepAliveState() {
        // Verify all layers are still active, re-activate if needed
        val layersStatus = mutableListOf<String>()

        // Layer 1 check
        if (isRunning) {
            layersStatus.add("1:OK")
        } else {
            layersStatus.add("1:DEAD")
        }

        // Layer 2 check - verify alarm is still set
        val alarmIntent = PendingIntent.getService(
            this, 2004,
            Intent(this, KeepAliveService::class.java).apply { action = ACTION_ALARM_CHECK },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (alarmIntent != null) {
            layersStatus.add("2:OK")
        } else {
            layersStatus.add("2:DEAD")
            setupAlarmManager() // Re-setup
        }

        // Layer 3 check
        val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val job = jobScheduler.getPendingJob(JOB_ID)
        if (job != null) {
            layersStatus.add("3:OK")
        } else {
            layersStatus.add("3:DEAD")
            setupJobScheduler() // Re-setup
        }

        // Layer 4 check - WorkManager is managed by the system
        layersStatus.add("4:MANAGED")

        // Layer 5 & 6 assumed OK if we're running
        layersStatus.add("5:OK")
        layersStatus.add("6:OK")

        Logger.d(TAG, "Keep-alive layer sync: $layersStatus")
        updateNotification()

        // Re-acquire alarm for next cycle (exact alarms are one-shot on M+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setupAlarmManager()
        }
    }

    private fun rescheduleAllLayers(context: Context?) {
        Logger.d(TAG, "Rescheduling all keep-alive layers")
        setupAlarmManager()
        setupJobScheduler()
        setupWorkManager()
    }

    // ─── Notification ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Keep-Alive Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "HackerLauncher keep-alive service status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, KeepAliveService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HackerLauncher Keep-Alive")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        try {
            val layersInfo = "Layers active: ${activeLayers.size}/6"
            val notification = buildNotification(layersInfo)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update notification", e)
        }
    }

    // ─── JobService for Layer 3 ───────────────────────────────────────

    /**
     * JobScheduler job service for keep-alive Layer 3.
     */
    class KeepAliveJobService : android.app.job.JobService() {
        override fun onStartJob(params: android.app.job.JobParameters?): Boolean {
            Logger.d(TAG, "KeepAliveJobService started")
            try {
                val intent = Intent(this, KeepAliveService::class.java).apply {
                    action = ACTION_RESTART_SERVICES
                }
                startForegroundService(intent)
            } catch (e: Exception) {
                Logger.e(TAG, "KeepAliveJobService failed to restart services", e)
            }
            jobFinished(params, false)
            return false
        }

        override fun onStopJob(params: android.app.job.JobParameters?): Boolean {
            Logger.d(TAG, "KeepAliveJobService stopped")
            return true // Reschedule
        }
    }
}

/**
 * WorkManager worker for keep-alive Layer 4.
 */
class KeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val TAG = "KeepAliveWorker"
    }

    override fun doWork(): Result {
        Logger.d(TAG, "KeepAliveWorker doWork triggered")
        try {
            val intent = Intent(applicationContext, KeepAliveService::class.java).apply {
                action = KeepAliveService.ACTION_RESTART_SERVICES
            }
            applicationContext.startForegroundService(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "KeepAliveWorker failed to restart services", e)
        }
        return Result.success()
    }
}
