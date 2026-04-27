package com.hackerlauncher.services

import com.hackerlauncher.R

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import androidx.core.app.NotificationCompat
import com.hackerlauncher.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SystemMonitorService - Always-running system monitor for HackerLauncher.
 *
 * Monitors system metrics 24/7 including battery level/temperature/voltage/
 * charging status (every 30s), CPU frequency and usage (every 5s), RAM usage
 * (every 10s), storage space (every 60s), network speed up/down (every 2s),
 * screen on/off time tracking, uptime tracking, thermal status monitoring.
 * Sends alerts on: low battery, high CPU, low storage, high temperature.
 * Creates system health reports and shows key metrics in a persistent
 * notification.
 */
class SystemMonitorService : Service() {

    companion object {
        const val TAG = "SystemMonitorService"
        const val CHANNEL_ID = "system_monitor"
        const val NOTIFICATION_ID = 1007
        const val ACTION_START = "com.hackerlauncher.ACTION_START_SYSTEM_MONITOR"
        const val ACTION_STOP = "com.hackerlauncher.ACTION_STOP_SYSTEM_MONITOR"
        const val ACTION_GET_REPORT = "com.hackerlauncher.ACTION_GET_REPORT"

        const val ACTION_SYSTEM_ALERT = "com.hackerlauncher.ACTION_SYSTEM_ALERT"
        const val ACTION_METRICS_UPDATE = "com.hackerlauncher.ACTION_METRICS_UPDATE"

        const val EXTRA_ALERT_TYPE = "alert_type"
        const val EXTRA_ALERT_MESSAGE = "alert_message"
        const val EXTRA_BATTERY_LEVEL = "battery_level"
        const val EXTRA_BATTERY_TEMP = "battery_temp"
        const val EXTRA_CPU_USAGE = "cpu_usage"
        const val EXTRA_RAM_USAGE = "ram_usage"
        const val EXTRA_STORAGE_FREE = "storage_free"
        const val EXTRA_NETWORK_DOWN = "network_down"
        const val EXTRA_NETWORK_UP = "network_up"
        const val EXTRA_UPTIME = "uptime"

        const val ALERT_LOW_BATTERY = "low_battery"
        const val ALERT_HIGH_CPU = "high_cpu"
        const val ALERT_LOW_STORAGE = "low_storage"
        const val ALERT_HIGH_TEMP = "high_temp"
        const val ALERT_THERMAL = "thermal"

        const val PREFS_NAME = "system_monitor_prefs"
        const val KEY_METRICS_HISTORY = "metrics_history"
        const val KEY_SCREEN_ON_TIME = "screen_on_time"
        const val KEY_SCREEN_OFF_TIME = "screen_off_time"
        const val KEY_LAST_SCREEN_ON = "last_screen_on"
        const val KEY_IS_SCREEN_ON = "is_screen_on"
        const val MAX_HISTORY_ENTRIES = 2880 // 24h at 30s intervals

        // Thresholds
        const val BATTERY_LOW_THRESHOLD = 15
        const val CPU_HIGH_THRESHOLD = 80.0
        const val STORAGE_LOW_THRESHOLD_MB = 500L // 500MB
        const val TEMP_HIGH_THRESHOLD = 40.0 // Celsius
        const val BATTERY_TEMP_ALERT = 45.0

        // Monitoring intervals
        const val BATTERY_INTERVAL_MS = 30_000L
        const val CPU_INTERVAL_MS = 5_000L
        const val RAM_INTERVAL_MS = 10_000L
        const val STORAGE_INTERVAL_MS = 60_000L
        const val NETWORK_SPEED_INTERVAL_MS = 2_000L
        const val NOTIFICATION_UPDATE_MS = 5_000L
        const val REPORT_INTERVAL_MS = 300_000L // 5 minutes

        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var batteryJob: Job? = null
    private var cpuJob: Job? = null
    private var ramJob: Job? = null
    private var storageJob: Job? = null
    private var networkSpeedJob: Job? = null
    private var notificationJob: Job? = null
    private var reportJob: Job? = null
    private var uptimeJob: Job? = null
    private lateinit var prefs: SharedPreferences

    // Current metrics
    private var batteryLevel = 0
    private var batteryTemp = 0.0
    private var batteryVoltage = 0
    private var isCharging = false
    private var cpuUsage = 0.0
    private var cpuFreq = 0L
    private var ramUsedPercent = 0.0
    private var ramUsedMb = 0L
    private var ramTotalMb = 0L
    private var storageFreeMb = 0L
    private var storageTotalMb = 0L
    private var networkDownSpeed = 0.0
    private var networkUpSpeed = 0.0
    private var uptimeSeconds = 0L
    private var thermalStatus = 0

    // Screen tracking
    private var screenOnTimeMs = 0L
    private var screenOffTimeMs = 0L
    private var lastScreenStateChange = 0L
    private var isScreenOn = true

    // CPU tracking
    private var prevCpuTotal = 0L
    private var prevCpuIdle = 0L

    // Network speed tracking
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastSpeedCheckTime = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    val now = System.currentTimeMillis()
                    if (!isScreenOn && lastScreenStateChange > 0) {
                        screenOffTimeMs += now - lastScreenStateChange
                    }
                    isScreenOn = true
                    lastScreenStateChange = now
                    prefs.edit().putBoolean(KEY_IS_SCREEN_ON, true).apply()
                    Logger.d(TAG, "Screen ON")
                }
                Intent.ACTION_SCREEN_OFF -> {
                    val now = System.currentTimeMillis()
                    if (isScreenOn && lastScreenStateChange > 0) {
                        screenOnTimeMs += now - lastScreenStateChange
                    }
                    isScreenOn = false
                    lastScreenStateChange = now
                    prefs.edit().putBoolean(KEY_IS_SCREEN_ON, false).apply()
                    Logger.d(TAG, "Screen OFF")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "SystemMonitorService onCreate")
        createNotificationChannel()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Restore screen tracking state
        isScreenOn = prefs.getBoolean(KEY_IS_SCREEN_ON, true)
        screenOnTimeMs = prefs.getLong(KEY_SCREEN_ON_TIME, 0L)
        screenOffTimeMs = prefs.getLong(KEY_SCREEN_OFF_TIME, 0L)
        lastScreenStateChange = System.currentTimeMillis()
        lastSpeedCheckTime = System.currentTimeMillis()
        lastRxBytes = readNetworkBytes("rx")
        lastTxBytes = readNetworkBytes("tx")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "SystemMonitorService onStartCommand, action=${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitor()
                return START_NOT_STICKY
            }
            ACTION_GET_REPORT -> {
                generateAndSaveReport()
            }
            ACTION_START, null -> {
                startMonitor()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "SystemMonitorService onDestroy")
        stopMonitor()
        serviceScope.cancel()
    }

    // ─── Service Lifecycle ────────────────────────────────────────────

    private fun startMonitor() {
        if (isRunning) {
            Logger.d(TAG, "SystemMonitorService already running")
            return
        }

        isRunning = true
        Logger.i(TAG, "SystemMonitorService starting...")

        startForeground(NOTIFICATION_ID, buildNotification("Initializing system monitor..."))
        registerScreenReceiver()

        // Battery monitoring (every 30s)
        batteryJob = serviceScope.launch {
            while (isActive) {
                updateBatteryMetrics()
                checkThermalStatus()
                delay(BATTERY_INTERVAL_MS)
            }
        }

        // CPU monitoring (every 5s)
        cpuJob = serviceScope.launch {
            while (isActive) {
                updateCpuMetrics()
                delay(CPU_INTERVAL_MS)
            }
        }

        // RAM monitoring (every 10s)
        ramJob = serviceScope.launch {
            while (isActive) {
                updateRamMetrics()
                delay(RAM_INTERVAL_MS)
            }
        }

        // Storage monitoring (every 60s)
        storageJob = serviceScope.launch {
            while (isActive) {
                updateStorageMetrics()
                delay(STORAGE_INTERVAL_MS)
            }
        }

        // Network speed monitoring (every 2s)
        networkSpeedJob = serviceScope.launch {
            while (isActive) {
                updateNetworkSpeedMetrics()
                delay(NETWORK_SPEED_INTERVAL_MS)
            }
        }

        // Uptime tracking
        uptimeJob = serviceScope.launch {
            while (isActive) {
                uptimeSeconds = android.os.SystemClock.elapsedRealtime() / 1000
                delay(1_000L)
            }
        }

        // Notification update
        notificationJob = serviceScope.launch {
            while (isActive) {
                updateNotification()
                sendMetricsBroadcast()
                delay(NOTIFICATION_UPDATE_MS)
            }
        }

        // Periodic report
        reportJob = serviceScope.launch {
            while (isActive) {
                generateAndSaveReport()
                delay(REPORT_INTERVAL_MS)
            }
        }

        Logger.i(TAG, "SystemMonitorService started successfully")
    }

    private fun stopMonitor() {
        Logger.i(TAG, "SystemMonitorService stopping...")
        isRunning = false

        // Save screen tracking state
        val now = System.currentTimeMillis()
        if (lastScreenStateChange > 0) {
            if (isScreenOn) {
                screenOnTimeMs += now - lastScreenStateChange
            } else {
                screenOffTimeMs += now - lastScreenStateChange
            }
        }
        prefs.edit()
            .putLong(KEY_SCREEN_ON_TIME, screenOnTimeMs)
            .putLong(KEY_SCREEN_OFF_TIME, screenOffTimeMs)
            .apply()

        batteryJob?.cancel()
        cpuJob?.cancel()
        ramJob?.cancel()
        storageJob?.cancel()
        networkSpeedJob?.cancel()
        uptimeJob?.cancel()
        notificationJob?.cancel()
        reportJob?.cancel()

        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Logger.e(TAG, "Error unregistering screen receiver", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        Logger.i(TAG, "SystemMonitorService stopped")
    }

    // ─── Battery Metrics ──────────────────────────────────────────────

    private fun updateBatteryMetrics() {
        try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = registerReceiver(null, intentFilter)

            batteryStatus?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                batteryLevel = if (level >= 0 && scale > 0) (level * 100) / scale else 0

                batteryTemp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
                batteryVoltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                // Alerts
                if (batteryLevel <= BATTERY_LOW_THRESHOLD && !isCharging) {
                    sendAlert(ALERT_LOW_BATTERY, "Low battery: $batteryLevel%")
                }
                if (batteryTemp >= BATTERY_TEMP_ALERT) {
                    sendAlert(ALERT_HIGH_TEMP, "High battery temperature: ${"%.1f".format(batteryTemp)}°C")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update battery metrics", e)
        }
    }

    // ─── CPU Metrics ──────────────────────────────────────────────────

    private fun updateCpuMetrics() {
        try {
            val statFile = RandomAccessFile("/proc/stat", "r")
            val line = statFile.readLine()
            statFile.close()

            val parts = line.split("\\s+".toRegex())
            if (parts.size > 8) {
                val idle = parts[4].toLong()
                val iowait = parts[5].toLong()
                val totalIdle = idle + iowait
                val total = parts.subList(1, 8).sumOf { it.toLong() }

                if (prevCpuTotal > 0) {
                    val diffTotal = total - prevCpuTotal
                    val diffIdle = totalIdle - prevCpuIdle

                    if (diffTotal > 0) {
                        cpuUsage = (1.0 - diffIdle.toDouble() / diffTotal.toDouble()) * 100.0
                    }
                }

                prevCpuTotal = total
                prevCpuIdle = totalIdle
            }

            // CPU frequency
            cpuFreq = readCpuFrequency()

            // Alert on high CPU
            if (cpuUsage > CPU_HIGH_THRESHOLD) {
                sendAlert(ALERT_HIGH_CPU, "High CPU usage: ${"%.1f".format(cpuUsage)}%")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update CPU metrics", e)
        }
    }

    private fun readCpuFrequency(): Long {
        return try {
            // Read max frequency from first CPU core
            val freqFile = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            if (freqFile.exists()) {
                freqFile.readText().trim().toLong() / 1000 // Convert to MHz
            } else {
                // Try reading from /proc/cpuinfo
                val cpuinfo = File("/proc/cpuinfo")
                if (cpuinfo.exists()) {
                    val lines = cpuinfo.readLines()
                    for (line in lines) {
                        if (line.startsWith("cpu MHz")) {
                            return line.split(":")[1].trim().toDouble().toLong()
                        }
                    }
                }
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    // ─── RAM Metrics ──────────────────────────────────────────────────

    private fun updateRamMetrics() {
        try {
            val memFile = RandomAccessFile("/proc/meminfo", "r")
            var totalMem = 0L
            var freeMem = 0L
            var buffers = 0L
            var cached = 0L
            var memAvailable = 0L

            var line: String?
            while (memFile.readLine().also { line = it } != null) {
                val l = line ?: break
                when {
                    l.startsWith("MemTotal:") -> totalMem = extractMemValue(l)
                    l.startsWith("MemFree:") -> freeMem = extractMemValue(l)
                    l.startsWith("Buffers:") -> buffers = extractMemValue(l)
                    l.startsWith("Cached:") -> cached = extractMemValue(l)
                    l.startsWith("MemAvailable:") -> memAvailable = extractMemValue(l)
                }
            }
            memFile.close()

            ramTotalMb = totalMem / 1024
            val usedMem = if (memAvailable > 0) {
                totalMem - memAvailable
            } else {
                totalMem - freeMem - buffers - cached
            }
            ramUsedMb = usedMem / 1024

            if (totalMem > 0) {
                ramUsedPercent = (usedMem.toDouble() / totalMem.toDouble()) * 100.0
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update RAM metrics", e)
        }
    }

    private fun extractMemValue(line: String): Long {
        return line.split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() ?: 0L
    }

    // ─── Storage Metrics ──────────────────────────────────────────────

    private fun updateStorageMetrics() {
        try {
            // Internal storage
            val dataDir = android.os.Environment.getDataDirectory()
            val stat = StatFs(dataDir.path)

            storageTotalMb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                (stat.totalBytes / (1024 * 1024))
            } else {
                @Suppress("DEPRECATION")
                (stat.blockCount.toLong() * stat.blockSize.toLong()) / (1024 * 1024)
            }

            storageFreeMb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                (stat.availableBytes / (1024 * 1024))
            } else {
                @Suppress("DEPRECATION")
                (stat.availableBlocks.toLong() * stat.blockSize.toLong()) / (1024 * 1024)
            }

            // Alert on low storage
            if (storageFreeMb < STORAGE_LOW_THRESHOLD_MB) {
                sendAlert(ALERT_LOW_STORAGE, "Low storage: ${storageFreeMb}MB free")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update storage metrics", e)
        }
    }

    // ─── Network Speed Metrics ────────────────────────────────────────

    private fun updateNetworkSpeedMetrics() {
        try {
            val currentRxBytes = readNetworkBytes("rx")
            val currentTxBytes = readNetworkBytes("tx")
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - lastSpeedCheckTime

            if (elapsed > 0) {
                val rxDiff = currentRxBytes - lastRxBytes
                val txDiff = currentTxBytes - lastTxBytes

                networkDownSpeed = (rxDiff.toDouble() / elapsed) * 1000.0 // bytes/sec
                networkUpSpeed = (txDiff.toDouble() / elapsed) * 1000.0 // bytes/sec
            }

            lastRxBytes = currentRxBytes
            lastTxBytes = currentTxBytes
            lastSpeedCheckTime = currentTime
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update network speed metrics", e)
        }
    }

    private fun readNetworkBytes(direction: String): Long {
        return try {
            val lines = File("/proc/net/dev").readLines()
            var total = 0L
            for (line in lines.drop(2)) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size > 9) {
                    val iface = parts[0].removeSuffix(":")
                    if (iface != "lo") {
                        total += if (direction == "rx") {
                            parts[1].toLongOrNull() ?: 0L
                        } else {
                            parts[9].toLongOrNull() ?: 0L
                        }
                    }
                }
            }
            total
        } catch (e: Exception) {
            0L
        }
    }

    // ─── Thermal Status ───────────────────────────────────────────────

    private fun checkThermalStatus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                thermalStatus = powerManager.currentThermalStatus

                if (thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) {
                    val statusLabel = when (thermalStatus) {
                        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
                        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
                        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
                        else -> "Unknown"
                    }
                    sendAlert(ALERT_THERMAL, "Thermal status: $statusLabel ($thermalStatus)")
                }
            } else {
                // Use battery temperature as proxy
                if (batteryTemp >= TEMP_HIGH_THRESHOLD) {
                    thermalStatus = 1
                    sendAlert(ALERT_HIGH_TEMP, "High temperature: ${"%.1f".format(batteryTemp)}°C")
                } else {
                    thermalStatus = 0
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check thermal status", e)
        }
    }

    // ─── Screen Time Tracking ─────────────────────────────────────────

    private fun registerScreenReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            // ACTION_SCREEN_ON/OFF are system broadcasts - need RECEIVER_EXPORTED on Android 14+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            }
            Logger.d(TAG, "Screen receiver registered")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register screen receiver", e)
        }
    }

    fun getScreenOnTime(): Long {
        var total = screenOnTimeMs
        if (isScreenOn && lastScreenStateChange > 0) {
            total += System.currentTimeMillis() - lastScreenStateChange
        }
        return total
    }

    fun getScreenOffTime(): Long {
        var total = screenOffTimeMs
        if (!isScreenOn && lastScreenStateChange > 0) {
            total += System.currentTimeMillis() - lastScreenStateChange
        }
        return total
    }

    // ─── Alerts ───────────────────────────────────────────────────────

    private fun sendAlert(alertType: String, message: String) {
        Logger.w(TAG, "System Alert [$alertType]: $message")

        val intent = Intent(ACTION_SYSTEM_ALERT).apply {
            putExtra(EXTRA_ALERT_TYPE, alertType)
            putExtra(EXTRA_ALERT_MESSAGE, message)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ─── Metrics Broadcast ────────────────────────────────────────────

    private fun sendMetricsBroadcast() {
        val intent = Intent(ACTION_METRICS_UPDATE).apply {
            putExtra(EXTRA_BATTERY_LEVEL, batteryLevel)
            putExtra(EXTRA_BATTERY_TEMP, batteryTemp)
            putExtra(EXTRA_CPU_USAGE, cpuUsage)
            putExtra(EXTRA_RAM_USAGE, ramUsedPercent)
            putExtra(EXTRA_STORAGE_FREE, storageFreeMb)
            putExtra(EXTRA_NETWORK_DOWN, networkDownSpeed)
            putExtra(EXTRA_NETWORK_UP, networkUpSpeed)
            putExtra(EXTRA_UPTIME, uptimeSeconds)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ─── Health Report ────────────────────────────────────────────────

    private fun generateAndSaveReport() {
        try {
            val report = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

                put("battery", JSONObject().apply {
                    put("level", batteryLevel)
                    put("temperature", batteryTemp)
                    put("voltage", batteryVoltage)
                    put("charging", isCharging)
                })

                put("cpu", JSONObject().apply {
                    put("usage", String.format("%.1f", cpuUsage).toDouble())
                    put("frequencyMHz", cpuFreq)
                })

                put("ram", JSONObject().apply {
                    put("usedMb", ramUsedMb)
                    put("totalMb", ramTotalMb)
                    put("usedPercent", String.format("%.1f", ramUsedPercent).toDouble())
                })

                put("storage", JSONObject().apply {
                    put("freeMb", storageFreeMb)
                    put("totalMb", storageTotalMb)
                })

                put("network", JSONObject().apply {
                    put("downloadSpeed", networkDownSpeed)
                    put("uploadSpeed", networkUpSpeed)
                })

                put("screen", JSONObject().apply {
                    put("onTimeMs", getScreenOnTime())
                    put("offTimeMs", getScreenOffTime())
                })

                put("uptime", JSONObject().apply {
                    put("seconds", uptimeSeconds)
                    put("formatted", formatUptime(uptimeSeconds))
                })

                put("thermal", JSONObject().apply {
                    put("status", thermalStatus)
                    put("temperatureC", batteryTemp)
                })
            }

            // Save to history
            val history = JSONArray(prefs.getString(KEY_METRICS_HISTORY, "[]") ?: "[]")
            history.put(report)

            while (history.length() > MAX_HISTORY_ENTRIES) {
                history.remove(0)
            }

            prefs.edit().putString(KEY_METRICS_HISTORY, history.toString()).apply()

            Logger.d(TAG, "System health report generated")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to generate health report", e)
        }
    }

    // ─── Notification ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "HackerLauncher system monitor status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, SystemMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reportIntent = Intent(this, SystemMonitorService::class.java).apply {
            action = ACTION_GET_REPORT
        }
        val reportPendingIntent = PendingIntent.getService(
            this, 1, reportIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Monitor")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .addLine(contentText)
            )
            .addAction(
                android.R.drawable.ic_menu_agenda,
                "Report",
                reportPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        try {
            val batteryIcon = if (isCharging) "⚡" else ""
            val cpuFreqStr = if (cpuFreq > 0) "${cpuFreq}MHz" else ""
            val downStr = formatSpeed(networkDownSpeed)
            val upStr = formatSpeed(networkUpSpeed)

            val line1 = "🔋$batteryLevel%$batteryIcon ${"%.1f".format(batteryTemp)}°C | CPU: ${"%.0f".format(cpuUsage)}% $cpuFreqStr"
            val line2 = "RAM: ${ramUsedMb}/${ramTotalMb}MB (${ "%.0f".format(ramUsedPercent)}%) | Disk: ${storageFreeMb}MB free"
            val line3 = "↓$downStr ↑$upStr | Up: ${formatUptime(uptimeSeconds)}"

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System Monitor")
                .setContentText(line1)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setStyle(
                    NotificationCompat.InboxStyle()
                        .addLine(line1)
                        .addLine(line2)
                        .addLine(line3)
                )
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update notification", e)
        }
    }

    // ─── Utility ──────────────────────────────────────────────────────

    private fun formatUptime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 24 -> "${hours / 24}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1_000_000 -> String.format("%.1fM", bytesPerSec / 1_000_000)
            bytesPerSec >= 1_000 -> String.format("%.0fK", bytesPerSec / 1_000)
            else -> String.format("%.0fB", bytesPerSec)
        }
    }
}
