package com.hackerlauncher.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hackerlauncher.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ProcessMonitorService - Always-running process monitor for HackerLauncher.
 *
 * Monitors all running processes, tracks foreground app changes,
 * monitors app usage time, detects suspicious processes (high CPU/RAM),
 * supports auto-kill of configurable apps, process blacklist/whitelist,
 * CPU and memory usage per process, alerts on new process installation,
 * alerts on background camera/mic usage, and stores process history.
 */
class ProcessMonitorService : Service() {

    companion object {
        const val TAG = "ProcessMonitorService"
        const val CHANNEL_ID = "process_monitor_service"
        const val NOTIFICATION_ID = 1006
        const val ACTION_START = "com.hackerlauncher.ACTION_START_PROCESS_MONITOR"
        const val ACTION_STOP = "com.hackerlauncher.ACTION_STOP_PROCESS_MONITOR"
        const val ACTION_KILL_PROCESS = "com.hackerlauncher.ACTION_KILL_PROCESS"
        const val ACTION_ADD_TO_BLACKLIST = "com.hackerlauncher.ACTION_ADD_TO_BLACKLIST"
        const val ACTION_REMOVE_FROM_BLACKLIST = "com.hackerlauncher.ACTION_REMOVE_FROM_BLACKLIST"
        const val ACTION_ADD_TO_WHITELIST = "com.hackerlauncher.ACTION_ADD_TO_WHITELIST"
        const val ACTION_REMOVE_FROM_WHITELIST = "com.hackerlauncher.ACTION_REMOVE_FROM_WHITELIST"

        const val ACTION_FOREGROUND_APP_CHANGED = "com.hackerlauncher.ACTION_FOREGROUND_APP_CHANGED"
        const val ACTION_SUSPICIOUS_PROCESS = "com.hackerlauncher.ACTION_SUSPICIOUS_PROCESS"
        const val ACTION_NEW_APP_INSTALLED = "com.hackerlauncher.ACTION_NEW_APP_INSTALLED"
        const val ACTION_CAMERA_MIC_USAGE = "com.hackerlauncher.ACTION_CAMERA_MIC_USAGE"

        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_PROCESS_NAME = "process_name"
        const val EXTRA_CPU_USAGE = "cpu_usage"
        const val EXTRA_MEMORY_USAGE = "memory_usage"
        const val EXTRA_SENSOR_TYPE = "sensor_type"

        const val PREFS_NAME = "process_monitor_prefs"
        const val KEY_BLACKLIST = "process_blacklist"
        const val KEY_WHITELIST = "process_whitelist"
        const val KEY_KILL_LIST = "auto_kill_list"
        const val KEY_HISTORY = "process_history"
        const val KEY_INSTALLED_APPS = "installed_apps"
        const val KEY_FOREGROUND_APP = "foreground_app"
        const val KEY_APP_USAGE = "app_usage"
        const val MAX_HISTORY_ENTRIES = 5000

        const val CPU_THRESHOLD = 50.0  // Alert if > 50%
        const val RAM_THRESHOLD = 200L   // Alert if > 200MB
        const val MONITOR_INTERVAL_MS = 5_000L
        const val USAGE_STATS_INTERVAL_MS = 60_000L

        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var monitorJob: Job? = null
    private var usageStatsJob: Job? = null
    private var notificationUpdateJob: Job? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var packageManager: PackageManager

    private var lastForegroundApp = ""
    private var foregroundAppChangeTime = 0L
    private val appUsageMap = mutableMapOf<String, Long>() // packageName -> usageTimeMs

    data class ProcessInfo(
        val pid: Int,
        val name: String,
        val user: String,
        val cpuUsage: Double,
        val memoryMb: Long,
        val state: String
    )

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "ProcessMonitorService onCreate")
        createNotificationChannel()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        packageManager = applicationContext.packageManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "ProcessMonitorService onStartCommand, action=${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitor()
                return START_NOT_STICKY
            }
            ACTION_KILL_PROCESS -> {
                val processName = intent.getStringExtra(EXTRA_PROCESS_NAME) ?: return START_STICKY
                killProcess(processName)
            }
            ACTION_ADD_TO_BLACKLIST -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_STICKY
                addToBlacklist(pkg)
            }
            ACTION_REMOVE_FROM_BLACKLIST -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_STICKY
                removeFromBlacklist(pkg)
            }
            ACTION_ADD_TO_WHITELIST -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_STICKY
                addToWhitelist(pkg)
            }
            ACTION_REMOVE_FROM_WHITELIST -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_STICKY
                removeFromWhitelist(pkg)
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
        Logger.i(TAG, "ProcessMonitorService onDestroy")
        stopMonitor()
    }

    // ─── Service Lifecycle ────────────────────────────────────────────

    private fun startMonitor() {
        if (isRunning) {
            Logger.d(TAG, "ProcessMonitorService already running")
            return
        }

        isRunning = true
        Logger.i(TAG, "ProcessMonitorService starting...")

        startForeground(NOTIFICATION_ID, buildNotification("Initializing process monitor..."))
        snapshotInstalledApps()

        monitorJob = serviceScope.launch {
            while (isActive) {
                monitorProcesses()
                delay(MONITOR_INTERVAL_MS)
            }
        }

        usageStatsJob = serviceScope.launch {
            while (isActive) {
                updateUsageStats()
                detectForegroundAppChange()
                checkForNewApps()
                checkCameraMicUsage()
                delay(USAGE_STATS_INTERVAL_MS)
            }
        }

        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                updateNotification()
                delay(5_000L)
            }
        }

        Logger.i(TAG, "ProcessMonitorService started successfully")
    }

    private fun stopMonitor() {
        Logger.i(TAG, "ProcessMonitorService stopping...")
        isRunning = false
        monitorJob?.cancel()
        usageStatsJob?.cancel()
        notificationUpdateJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Logger.i(TAG, "ProcessMonitorService stopped")
    }

    // ─── Process Monitoring ───────────────────────────────────────────

    private fun monitorProcesses() {
        val processes = getRunningProcesses()
        val blacklist = getListFromPrefs(KEY_BLACKLIST)
        val killList = getListFromPrefs(KEY_KILL_LIST)
        val whitelist = getListFromPrefs(KEY_WHITELIST)

        for (process in processes) {
            // Check for suspicious processes
            if (process.cpuUsage > CPU_THRESHOLD || process.memoryMb > RAM_THRESHOLD) {
                if (process.name !in whitelist) {
                    Logger.w(TAG, "Suspicious process: ${process.name} " +
                            "(CPU: ${"%.1f".format(process.cpuUsage)}%, RAM: ${process.memoryMb}MB)")
                    sendSuspiciousProcessBroadcast(process)
                }
            }

            // Auto-kill blacklisted or kill-listed processes
            val pkgName = extractPackageName(process.name)
            if (pkgName in blacklist || pkgName in killList) {
                Logger.i(TAG, "Auto-killing process: ${process.name}")
                killProcess(process.name)
            }
        }

        recordProcessHistory(processes)
    }

    private fun getRunningProcesses(): List<ProcessInfo> {
        val processes = mutableListOf<ProcessInfo>()
        try {
            val processBuilder = ProcessBuilder("ps", "-e", "-o", "PID,USER,NAME,%CPU,%MEM,STAT")
            val proc = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            var lineNum = 0

            while (reader.readLine().also { line = it } != null) {
                lineNum++
                if (lineNum <= 1) continue // Skip header

                val l = line ?: continue
                val parts = l.trim().split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val pid = parts[0].toIntOrNull() ?: 0
                    val user = parts[1]
                    val name = if (parts.size >= 6) parts[5] else parts[2]

                    // Read CPU/Memory from /proc/<pid>/stat
                    val cpuAndMem = getCpuAndMemForPid(pid)

                    processes.add(ProcessInfo(
                        pid = pid,
                        name = name,
                        user = user,
                        cpuUsage = cpuAndMem.first,
                        memoryMb = cpuAndMem.second,
                        state = if (parts.size >= 4) parts[3] else "S"
                    ))
                }
            }
            reader.close()
            proc.waitFor()
        } catch (e: Exception) {
            // Fallback: read from /proc directly
            processes.addAll(readProcessesFromProc())
        }
        return processes
    }

    private fun readProcessesFromProc(): List<ProcessInfo> {
        val processes = mutableListOf<ProcessInfo>()
        try {
            val procDir = File("/proc")
            val dirs = procDir.listFiles { file -> file.isDirectory && file.name.matches("\\d+".toRegex()) }
                ?: return processes

            for (pidDir in dirs) {
                try {
                    val pid = pidDir.name.toInt()
                    val statFile = File(pidDir, "stat")
                    val cmdlineFile = File(pidDir, "cmdline")

                    if (!statFile.exists()) continue

                    val stat = statFile.readText()
                    val cmdline = if (cmdlineFile.exists()) cmdlineFile.readText().replace('\u0000', ' ').trim() else ""

                    val name = if (cmdline.isNotEmpty()) cmdline else extractCommFromStat(stat)

                    val cpuAndMem = parseStatForCpuMem(stat)

                    processes.add(ProcessInfo(
                        pid = pid,
                        name = name,
                        user = "",
                        cpuUsage = cpuAndMem.first,
                        memoryMb = cpuAndMem.second,
                        state = extractStateFromStat(stat)
                    ))
                } catch (e: Exception) {
                    // Skip this process
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read processes from /proc", e)
        }
        return processes
    }

    private fun getCpuAndMemForPid(pid: Int): Pair<Double, Long> {
        return try {
            val statFile = File("/proc/$pid/stat")
            if (statFile.exists()) {
                parseStatForCpuMem(statFile.readText())
            } else {
                Pair(0.0, 0L)
            }
        } catch (e: Exception) {
            Pair(0.0, 0L)
        }
    }

    private fun parseStatForCpuMem(stat: String): Pair<Double, Long> {
        return try {
            // /proc/<pid>/stat format: pid (comm) state utime stime ... rss
            // Find closing paren to handle comm with spaces
            val closeParen = stat.lastIndexOf(')')
            if (closeParen < 0) return Pair(0.0, 0L)

            val afterComm = stat.substring(closeParen + 2).trim()
            val fields = afterComm.split("\\s+".toRegex())

            // utime is field 14 (index 11 after comm), stime is field 15 (index 12 after comm)
            // rss is field 24 (index 21 after comm) - in pages
            val utime = fields.getOrNull(11)?.toLongOrNull() ?: 0L
            val stime = fields.getOrNull(12)?.toLongOrNull() ?: 0L
            val rss = fields.getOrNull(21)?.toLongOrNull() ?: 0L

            val cpuUsage = ((utime + stime).toDouble() / 100.0) // Approximate percentage
            val memoryMb = rss * 4L / 1024L // Convert pages to MB (4KB per page)

            Pair(cpuUsage, memoryMb)
        } catch (e: Exception) {
            Pair(0.0, 0L)
        }
    }

    private fun extractCommFromStat(stat: String): String {
        return try {
            val start = stat.indexOf('(')
            val end = stat.lastIndexOf(')')
            if (start >= 0 && end > start) stat.substring(start + 1, end) else "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun extractStateFromStat(stat: String): String {
        return try {
            val closeParen = stat.lastIndexOf(')')
            if (closeParen < 0) return "S"
            val afterComm = stat.substring(closeParen + 2).trim()
            afterComm.split("\\s+".toRegex()).getOrNull(0) ?: "S"
        } catch (e: Exception) {
            "S"
        }
    }

    private fun extractPackageName(processName: String): String {
        // Extract package from process name like "com.example.app:some_service"
        return processName.split(":").firstOrNull()?.trim() ?: processName
    }

    // ─── Process Kill ─────────────────────────────────────────────────

    private fun killProcess(processName: String) {
        try {
            val pid = findPidForProcess(processName)
            if (pid > 0) {
                val proc = Runtime.getRuntime().exec(arrayOf("kill", "-9", pid.toString()))
                proc.waitFor()
                Logger.i(TAG, "Killed process: $processName (PID: $pid)")
            } else {
                // Try force-stop via am
                val pkg = extractPackageName(processName)
                val proc = Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg))
                proc.waitFor()
                Logger.i(TAG, "Force-stopped package: $pkg")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to kill process: $processName", e)
        }
    }

    private fun findPidForProcess(processName: String): Int {
        try {
            val procDir = File("/proc")
            val dirs = procDir.listFiles { file -> file.isDirectory && file.name.matches("\\d+".toRegex()) }
                ?: return 0

            for (pidDir in dirs) {
                try {
                    val cmdlineFile = File(pidDir, "cmdline")
                    if (cmdlineFile.exists()) {
                        val cmdline = cmdlineFile.readText().replace('\u0000', ' ').trim()
                        if (cmdline.contains(processName)) {
                            return pidDir.name.toInt()
                        }
                    }
                } catch (e: Exception) {
                    // Skip
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to find PID for process: $processName", e)
        }
        return 0
    }

    // ─── Foreground App Detection ─────────────────────────────────────

    private fun detectForegroundAppChange() {
        try {
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 10_000L // Last 10 seconds

            val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
            var currentApp = ""

            while (usageEvents.hasNextEvent()) {
                val event = UsageEvents.Event()
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    currentApp = event.packageName
                }
            }

            if (currentApp.isNotEmpty() && currentApp != lastForegroundApp) {
                Logger.i(TAG, "Foreground app changed: $lastForegroundApp -> $currentApp")
                val previousApp = lastForegroundApp
                lastForegroundApp = currentApp
                foregroundAppChangeTime = System.currentTimeMillis()

                prefs.edit().putString(KEY_FOREGROUND_APP, currentApp).apply()

                // Send broadcast
                val intent = Intent(ACTION_FOREGROUND_APP_CHANGED).apply {
                    putExtra(EXTRA_PACKAGE_NAME, currentApp)
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to detect foreground app change", e)
        }
    }

    // ─── App Usage Stats ──────────────────────────────────────────────

    private fun updateUsageStats() {
        try {
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 60_000L // Last minute

            val usageStatsMap = usageStatsManager.queryAndAggregateUsageStats(beginTime, endTime)
            for ((packageName, usageStats) in usageStatsMap) {
                val totalTime = usageStats.totalTimeInForeground
                val existingTime = appUsageMap[packageName] ?: 0L
                appUsageMap[packageName] = existingTime + totalTime
            }

            // Persist usage stats
            saveAppUsageToPrefs()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update usage stats", e)
        }
    }

    private fun saveAppUsageToPrefs() {
        try {
            val usageJson = JSONObject()
            for ((pkg, time) in appUsageMap) {
                usageJson.put(pkg, time)
            }
            prefs.edit().putString(KEY_APP_USAGE, usageJson.toString()).apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save app usage", e)
        }
    }

    // ─── New App Detection ────────────────────────────────────────────

    private fun snapshotInstalledApps() {
        try {
            val apps = JSONArray()
            val installed = packageManager.getInstalledApplications(0)
            for (appInfo in installed) {
                apps.put(appInfo.packageName)
            }
            prefs.edit().putString(KEY_INSTALLED_APPS, apps.toString()).apply()
            Logger.d(TAG, "Installed apps snapshot: ${apps.length()} apps")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to snapshot installed apps", e)
        }
    }

    private fun checkForNewApps() {
        try {
            val previousAppsStr = prefs.getString(KEY_INSTALLED_APPS, "[]") ?: "[]"
            val previousApps = JSONArray(previousAppsStr)
            val previousSet = mutableSetOf<String>()
            for (i in 0 until previousApps.length()) {
                previousSet.add(previousApps.getString(i))
            }

            val installed = packageManager.getInstalledApplications(0)
            for (appInfo in installed) {
                if (appInfo.packageName !in previousSet) {
                    Logger.i(TAG, "New app installed: ${appInfo.packageName}")
                    sendNewAppBroadcast(appInfo.packageName)
                }
            }

            // Update snapshot
            snapshotInstalledApps()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check for new apps", e)
        }
    }

    // ─── Camera/Mic Usage Detection ───────────────────────────────────

    private fun checkCameraMicUsage() {
        try {
            // Check camera usage via /proc
            val cameraPids = findProcessesUsingDevice("camera")
            if (cameraPids.isNotEmpty()) {
                for ((pid, name) in cameraPids) {
                    Logger.w(TAG, "Camera in use by: $name (PID: $pid)")
                    sendCameraMicBroadcast("camera", name)
                }
            }

            // Check mic usage via /proc
            val micPids = findProcessesUsingDevice("mic")
            if (micPids.isNotEmpty()) {
                for ((pid, name) in micPids) {
                    Logger.w(TAG, "Microphone in use by: $name (PID: $pid)")
                    sendCameraMicBroadcast("microphone", name)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check camera/mic usage", e)
        }
    }

    private fun findProcessesUsingDevice(deviceType: String): List<Pair<Int, String>> {
        val results = mutableListOf<Pair<Int, String>>()
        try {
            val devicePaths = when (deviceType) {
                "camera" -> listOf("/dev/video0", "/dev/video1", "/dev/video2")
                "mic" -> listOf("/dev/snd/pcmC0D0c", "/dev/snd/pcmC1D0c")
                else -> emptyList()
            }

            for (devicePath in devicePaths) {
                val deviceFile = File(devicePath)
                if (!deviceFile.exists()) continue

                // Check /proc/<pid>/fd for references to the device
                val procDir = File("/proc")
                val dirs = procDir.listFiles { file ->
                    file.isDirectory && file.name.matches("\\d+".toRegex())
                } ?: continue

                for (pidDir in dirs) {
                    try {
                        val fdDir = File(pidDir, "fd")
                        if (!fdDir.exists()) continue

                        val fds = fdDir.listFiles() ?: continue
                        for (fd in fds) {
                            try {
                                val linkTarget = fd.canonicalPath
                                if (linkTarget.startsWith(devicePath)) {
                                    val cmdlineFile = File(pidDir, "cmdline")
                                    val name = if (cmdlineFile.exists()) {
                                        cmdlineFile.readText().replace('\u0000', ' ').trim()
                                    } else {
                                        "PID:${pidDir.name}"
                                    }
                                    results.add(Pair(pidDir.name.toInt(), name))
                                    break
                                }
                            } catch (e: Exception) {
                                // Skip
                            }
                        }
                    } catch (e: Exception) {
                        // Skip
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to find processes using $deviceType", e)
        }
        return results.distinctBy { it.first }
    }

    // ─── Blacklist / Whitelist ────────────────────────────────────────

    private fun addToBlacklist(packageName: String) {
        val list = getListFromPrefs(KEY_BLACKLIST).toMutableSet()
        list.add(packageName)
        saveListToPrefs(KEY_BLACKLIST, list)
        Logger.i(TAG, "Added to blacklist: $packageName")
    }

    private fun removeFromBlacklist(packageName: String) {
        val list = getListFromPrefs(KEY_BLACKLIST).toMutableSet()
        list.remove(packageName)
        saveListToPrefs(KEY_BLACKLIST, list)
        Logger.i(TAG, "Removed from blacklist: $packageName")
    }

    private fun addToWhitelist(packageName: String) {
        val list = getListFromPrefs(KEY_WHITELIST).toMutableSet()
        list.add(packageName)
        saveListToPrefs(KEY_WHITELIST, list)
        Logger.i(TAG, "Added to whitelist: $packageName")
    }

    private fun removeFromWhitelist(packageName: String) {
        val list = getListFromPrefs(KEY_WHITELIST).toMutableSet()
        list.remove(packageName)
        saveListToPrefs(KEY_WHITELIST, list)
        Logger.i(TAG, "Removed from whitelist: $packageName")
    }

    private fun getListFromPrefs(key: String): Set<String> {
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    private fun saveListToPrefs(key: String, set: Set<String>) {
        prefs.edit().putStringSet(key, set).apply()
    }

    // ─── History ──────────────────────────────────────────────────────

    private fun recordProcessHistory(processes: List<ProcessInfo>) {
        try {
            val history = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")

            val snapshot = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("processCount", processes.size)
                val processArray = JSONArray()
                // Only record top 20 by CPU
                processes.sortedByDescending { it.cpuUsage }.take(20).forEach { proc ->
                    processArray.put(JSONObject().apply {
                        put("name", proc.name)
                        put("pid", proc.pid)
                        put("cpuUsage", proc.cpuUsage)
                        put("memoryMb", proc.memoryMb)
                        put("state", proc.state)
                    })
                }
                put("topProcesses", processArray)
            }

            history.put(snapshot)

            while (history.length() > MAX_HISTORY_ENTRIES) {
                history.remove(0)
            }

            prefs.edit().putString(KEY_HISTORY, history.toString()).apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to record process history", e)
        }
    }

    // ─── Broadcasts ───────────────────────────────────────────────────

    private fun sendSuspiciousProcessBroadcast(process: ProcessInfo) {
        val intent = Intent(ACTION_SUSPICIOUS_PROCESS).apply {
            putExtra(EXTRA_PROCESS_NAME, process.name)
            putExtra(EXTRA_CPU_USAGE, process.cpuUsage)
            putExtra(EXTRA_MEMORY_USAGE, process.memoryMb)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun sendNewAppBroadcast(packageName: String) {
        val intent = Intent(ACTION_NEW_APP_INSTALLED).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun sendCameraMicBroadcast(sensorType: String, processName: String) {
        val intent = Intent(ACTION_CAMERA_MIC_USAGE).apply {
            putExtra(EXTRA_SENSOR_TYPE, sensorType)
            putExtra(EXTRA_PROCESS_NAME, processName)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ─── Notification ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Process Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "HackerLauncher process monitor status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, ProcessMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Process Monitor")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
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
            val processCount = getRunningProcesses().size
            val fgApp = if (lastForegroundApp.isNotEmpty()) lastForegroundApp else "None"
            val text = "Processes: $processCount | Foreground: $fgApp"
            val notification = buildNotification(text)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update notification", e)
        }
    }
}
