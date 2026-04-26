package com.hackerlauncher.services

import com.hackerlauncher.R

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
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.hackerlauncher.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DaemonService - Always-running daemon service for HackerLauncher.
 *
 * Runs 24/7 in the background with a WakeLock, performs periodic health checks
 * every 30 seconds, auto-restarts all other services if they die, monitors
 * system health (CPU, RAM, battery), sends heartbeat broadcasts every minute,
 * and creates a persistent foreground notification.
 */
class DaemonService : Service() {

    companion object {
        const val TAG = "DaemonService"
        const val CHANNEL_ID = "daemon_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.hackerlauncher.ACTION_START_DAEMON"
        const val ACTION_STOP = "com.hackerlauncher.ACTION_STOP_DAEMON"
        const val ACTION_RESTART = "com.hackerlauncher.ACTION_RESTART_DAEMON"
        const val ACTION_HEALTH_CHECK = "com.hackerlauncher.ACTION_HEALTH_CHECK"
        const val ACTION_HEARTBEAT = "com.hackerlauncher.ACTION_HEARTBEAT"
        const val ACTION_ALARM_KEEPALIVE = "com.hackerlauncher.ACTION_ALARM_KEEPALIVE"

        const val EXTRA_UPTIME = "uptime"
        const val EXTRA_SERVICES_COUNT = "services_count"
        const val EXTRA_CPU_USAGE = "cpu_usage"
        const val EXTRA_RAM_USAGE = "ram_usage"
        const val EXTRA_BATTERY_LEVEL = "battery_level"

        const val HEALTH_CHECK_INTERVAL_MS = 30_000L
        const val HEARTBEAT_INTERVAL_MS = 60_000L
        const val ALARM_INTERVAL_MS = 5 * 60_000L

        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var startTime: Long = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private var healthCheckJob: Job? = null
    private var heartbeatJob: Job? = null
    private var notificationUpdateJob: Job? = null

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_ALARM_KEEPALIVE -> {
                    Logger.d(TAG, "Alarm keep-alive triggered")
                    if (!isRunning) {
                        Logger.w(TAG, "DaemonService was dead, restarting via alarm")
                        val restartIntent = Intent(context, DaemonService::class.java).apply {
                            action = ACTION_START
                        }
                        context?.startForegroundService(restartIntent)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startTime = System.currentTimeMillis()
        Logger.i(TAG, "DaemonService onCreate")
        createNotificationChannel()
        registerAlarmReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "DaemonService onStartCommand, action=${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopDaemon()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                Logger.i(TAG, "Restarting DaemonService")
                stopDaemon()
                startDaemon()
            }
            ACTION_HEALTH_CHECK -> {
                performHealthCheck()
            }
            ACTION_START, null -> {
                startDaemon()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "DaemonService onDestroy")
        stopDaemon()
        try {
            unregisterReceiver(alarmReceiver)
        } catch (e: Exception) {
            Logger.e(TAG, "Error unregistering alarm receiver", e)
        }
    }

    private fun startDaemon() {
        if (isRunning) {
            Logger.d(TAG, "DaemonService already running")
            updateNotification()
            return
        }

        isRunning = true
        startTime = System.currentTimeMillis()
        Logger.i(TAG, "DaemonService starting...")

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification("Starting daemon..."))
        scheduleAlarmKeepAlive()

        healthCheckJob = serviceScope.launch {
            while (isActive) {
                performHealthCheck()
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
        }

        heartbeatJob = serviceScope.launch {
            while (isActive) {
                sendHeartbeatBroadcast()
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }

        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                updateNotification()
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
        }

        Logger.i(TAG, "DaemonService started successfully")
    }

    private fun stopDaemon() {
        Logger.i(TAG, "DaemonService stopping...")
        isRunning = false
        healthCheckJob?.cancel()
        heartbeatJob?.cancel()
        notificationUpdateJob?.cancel()
        releaseWakeLock()
        cancelAlarmKeepAlive()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Logger.i(TAG, "DaemonService stopped")
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HackerLauncher::DaemonWakeLock"
            ).apply {
                acquire(12 * 60 * 60 * 1000L) // 12 hours max, will re-acquire
            }
            Logger.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Logger.d(TAG, "WakeLock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to release WakeLock", e)
        }
    }

    private fun performHealthCheck() {
        Logger.d(TAG, "Performing health check...")

        val cpuUsage = getCpuUsage()
        val ramUsage = getRamUsage()
        val batteryLevel = getBatteryLevel()

        Logger.d(TAG, "Health: CPU=${cpuUsage}%, RAM=${ramUsage}%, Battery=${batteryLevel}%")

        restartDeadServices()

        if (cpuUsage > 90.0) {
            Logger.w(TAG, "High CPU usage detected: $cpuUsage%")
        }
        if (ramUsage > 90.0) {
            Logger.w(TAG, "High RAM usage detected: $ramUsage%")
        }
        if (batteryLevel in 1..10) {
            Logger.w(TAG, "Low battery: $batteryLevel%")
        }
    }

    private fun restartDeadServices() {
        val servicesToMonitor = listOf(
            WatchdogService::class.java,
            KeepAliveService::class.java,
            NetworkMonitorService::class.java,
            LocationTrackerService::class.java,
            ProcessMonitorService::class.java,
            SystemMonitorService::class.java
        )

        for (serviceClass in servicesToMonitor) {
            if (!isServiceRunningCompat(serviceClass)) {
                Logger.w(TAG, "Service ${serviceClass.simpleName} is dead, restarting...")
                try {
                    val intent = Intent(this, serviceClass)
                    startForegroundService(intent)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to restart ${serviceClass.simpleName}", e)
                }
            }
        }
    }

    private fun isServiceRunningCompat(serviceClass: Class<*>): Boolean {
        // Check via reflection on the companion object's isServiceRunning if available
        return try {
            val companion = serviceClass.getDeclaredField("Companion")
                .get(null)
            val method = companion.javaClass.getMethod("isServiceRunning")
            method.invoke(companion) as? Boolean ?: false
        } catch (e: Exception) {
            // If the service doesn't have a companion isServiceRunning, assume it's dead
            Logger.d(TAG, "Cannot check status of ${serviceClass.simpleName}, assuming dead")
            false
        }
    }

    private fun sendHeartbeatBroadcast() {
        val uptime = System.currentTimeMillis() - startTime
        val intent = Intent(ACTION_HEARTBEAT).apply {
            putExtra(EXTRA_UPTIME, uptime)
            putExtra(EXTRA_SERVICES_COUNT, countRunningServices())
            putExtra(EXTRA_CPU_USAGE, getCpuUsage())
            putExtra(EXTRA_RAM_USAGE, getRamUsage())
            putExtra(EXTRA_BATTERY_LEVEL, getBatteryLevel())
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Logger.d(TAG, "Heartbeat sent: uptime=${formatUptime(uptime)}")
    }

    private fun countRunningServices(): Int {
        var count = 1 // Self
        val services = listOf(
            WatchdogService::class.java,
            KeepAliveService::class.java,
            NetworkMonitorService::class.java,
            LocationTrackerService::class.java,
            ProcessMonitorService::class.java,
            SystemMonitorService::class.java
        )
        for (serviceClass in services) {
            if (isServiceRunningCompat(serviceClass)) {
                count++
            }
        }
        return count
    }

    // ─── System Metrics ───────────────────────────────────────────────

    private fun getCpuUsage(): Double {
        return try {
            val statFile = RandomAccessFile("/proc/stat", "r")
            val line = statFile.readLine()
            statFile.close()

            val parts = line.split("\\s+".toRegex())
            if (parts.size > 8) {
                val idle = parts[4].toLong()
                val total = parts.subList(1, 8).sumOf { it.toLong() }
                if (total > 0) {
                    val usage = (1.0 - idle.toDouble() / total.toDouble()) * 100.0
                    String.format("%.1f", usage).toDouble()
                } else 0.0
            } else 0.0
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read CPU usage", e)
            0.0
        }
    }

    private fun getRamUsage(): Double {
        return try {
            val memFile = RandomAccessFile("/proc/meminfo", "r")
            var totalMem = 0L
            var freeMem = 0L
            var buffers = 0L
            var cached = 0L

            var line: String?
            while (memFile.readLine().also { line = it } != null) {
                val l = line ?: break
                when {
                    l.startsWith("MemTotal:") -> totalMem = extractMemValue(l)
                    l.startsWith("MemFree:") -> freeMem = extractMemValue(l)
                    l.startsWith("Buffers:") -> buffers = extractMemValue(l)
                    l.startsWith("Cached:") -> cached = extractMemValue(l)
                }
            }
            memFile.close()

            if (totalMem > 0) {
                val usedMem = totalMem - freeMem - buffers - cached
                (usedMem.toDouble() / totalMem.toDouble()) * 100.0
            } else 0.0
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read RAM usage", e)
            0.0
        }
    }

    private fun extractMemValue(line: String): Long {
        return line.split("\\s+".toRegex())[1].toLongOrNull() ?: 0L
    }

    private fun getBatteryLevel(): Int {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = registerReceiver(null, intentFilter)
            batteryStatus?.let {
                val level = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    (level * 100) / scale
                } else 0
            } ?: 0
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get battery level", e)
            0
        }
    }

    // ─── Notification ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daemon Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "HackerLauncher daemon service status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, DaemonService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val restartIntent = Intent(this, DaemonService::class.java).apply {
            action = ACTION_RESTART
        }
        val restartPendingIntent = PendingIntent.getService(
            this, 1, restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HackerLauncher Daemon")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_rotate,
                "Restart",
                restartPendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        try {
            val uptime = formatUptime(System.currentTimeMillis() - startTime)
            val servicesCount = countRunningServices()
            val cpuUsage = getCpuUsage()
            val ramUsage = getRamUsage()
            val text = "Up: $uptime | Svc: $servicesCount | CPU: ${"%.0f".format(cpuUsage)}% | RAM: ${"%.0f".format(ramUsage)}%"
            val notification = buildNotification(text)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update notification", e)
        }
    }

    // ─── AlarmManager Keep-Alive ──────────────────────────────────────

    private fun scheduleAlarmKeepAlive() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, DaemonService::class.java).apply {
                action = ACTION_ALARM_KEEPALIVE
            }
            val pendingIntent = PendingIntent.getService(
                this, 2001, intent,
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

            Logger.d(TAG, "Alarm keep-alive scheduled")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to schedule alarm keep-alive", e)
        }
    }

    private fun cancelAlarmKeepAlive() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, DaemonService::class.java).apply {
                action = ACTION_ALARM_KEEPALIVE
            }
            val pendingIntent = PendingIntent.getService(
                this, 2001, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            Logger.d(TAG, "Alarm keep-alive cancelled")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to cancel alarm keep-alive", e)
        }
    }

    private fun registerAlarmReceiver() {
        try {
            val filter = IntentFilter(ACTION_ALARM_KEEPALIVE)
            registerReceiver(alarmReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            Logger.d(TAG, "Alarm receiver registered")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register alarm receiver", e)
        }
    }

    // ─── Utility ──────────────────────────────────────────────────────

    private fun formatUptime(uptimeMs: Long): String {
        val seconds = uptimeMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
