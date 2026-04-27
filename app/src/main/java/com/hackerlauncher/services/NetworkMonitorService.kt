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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
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
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NetworkMonitorService - Always-running network monitor for HackerLauncher.
 *
 * Monitors network state 24/7, detects WiFi connections/disconnections,
 * logs all network changes with timestamps, tracks data usage per app,
 * monitors WiFi signal strength, detects network type switches,
 * performs background speed tests, monitors DNS, computes connection
 * quality scores, sends local broadcasts on network events, stores
 * network history in SharedPreferences, and shows current network
 * status in a persistent notification.
 */
class NetworkMonitorService : Service() {

    companion object {
        const val TAG = "NetworkMonitorService"
        const val CHANNEL_ID = "network_monitor"
        const val NOTIFICATION_ID = 1004
        const val ACTION_START = "com.hackerlauncher.ACTION_START_NETWORK_MONITOR"
        const val ACTION_STOP = "com.hackerlauncher.ACTION_STOP_NETWORK_MONITOR"
        const val ACTION_SPEED_TEST = "com.hackerlauncher.ACTION_SPEED_TEST"

        const val ACTION_NETWORK_EVENT = "com.hackerlauncher.ACTION_NETWORK_EVENT"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_NETWORK_TYPE = "network_type"
        const val EXTRA_WIFI_SSID = "wifi_ssid"
        const val EXTRA_SIGNAL_STRENGTH = "signal_strength"
        const val EXTRA_CONNECTION_QUALITY = "connection_quality"
        const val EXTRA_DOWNLOAD_SPEED = "download_speed"
        const val EXTRA_UPLOAD_SPEED = "upload_speed"

        const val EVENT_WIFI_CONNECTED = "wifi_connected"
        const val EVENT_WIFI_DISCONNECTED = "wifi_disconnected"
        const val EVENT_NETWORK_SWITCH = "network_switch"
        const val EVENT_NO_NETWORK = "no_network"
        const val EVENT_MOBILE_CONNECTED = "mobile_connected"
        const val EVENT_SPEED_TEST_COMPLETE = "speed_test_complete"

        const val PREFS_NAME = "network_monitor_history"
        const val KEY_HISTORY = "history"
        const val KEY_LAST_NETWORK_TYPE = "last_network_type"
        const val KEY_TOTAL_RX = "total_rx"
        const val KEY_TOTAL_TX = "total_tx"
        const val MAX_HISTORY_ENTRIES = 500

        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var monitorJob: Job? = null
    private var speedMonitorJob: Job? = null
    private var notificationUpdateJob: Job? = null
    private lateinit var connectivityManager: ConnectivityManager
    private var wifiManager: WifiManager? = null
    private lateinit var prefs: SharedPreferences

    private var currentNetworkType = "none"
    private var previousNetworkType = "none"
    private var currentSsid = ""
    private var currentSignalStrength = 0
    private var connectionQuality = 0
    private var lastDownloadSpeed = 0.0
    private var lastUploadSpeed = 0.0
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastSpeedCheckTime = 0L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Logger.d(TAG, "Network available: $network")
            updateNetworkState()
        }

        override fun onLost(network: Network) {
            Logger.d(TAG, "Network lost: $network")
            updateNetworkState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            Logger.d(TAG, "Network capabilities changed: $network")
            updateNetworkState()
        }
    }

    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION,
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    updateNetworkState()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "NetworkMonitorService onCreate")
        createNotificationChannel()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        lastRxBytes = getTotalRxBytes()
        lastTxBytes = getTotalTxBytes()
        lastSpeedCheckTime = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "NetworkMonitorService onStartCommand, action=${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitor()
                return START_NOT_STICKY
            }
            ACTION_SPEED_TEST -> {
                performSpeedTest()
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
        Logger.i(TAG, "NetworkMonitorService onDestroy")
        stopMonitor()
        serviceScope.cancel()
    }

    // ─── Service Lifecycle ────────────────────────────────────────────

    private fun startMonitor() {
        if (isRunning) {
            Logger.d(TAG, "NetworkMonitorService already running")
            return
        }

        isRunning = true
        Logger.i(TAG, "NetworkMonitorService starting...")

        startForeground(NOTIFICATION_ID, buildNotification("Initializing network monitor..."))
        registerNetworkCallback()
        registerConnectivityReceiver()
        updateNetworkState()

        // Periodic network state check
        monitorJob = serviceScope.launch {
            while (isActive) {
                updateNetworkState()
                calculateConnectionQuality()
                delay(10_000L) // Every 10 seconds
            }
        }

        // Speed monitoring
        speedMonitorJob = serviceScope.launch {
            while (isActive) {
                updateNetworkSpeed()
                delay(2_000L) // Every 2 seconds
            }
        }

        // Notification update
        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                updateNotification()
                delay(5_000L) // Every 5 seconds
            }
        }

        Logger.i(TAG, "NetworkMonitorService started successfully")
    }

    private fun stopMonitor() {
        Logger.i(TAG, "NetworkMonitorService stopping...")
        isRunning = false
        monitorJob?.cancel()
        speedMonitorJob?.cancel()
        notificationUpdateJob?.cancel()
        unregisterNetworkCallback()
        unregisterConnectivityReceiver()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Logger.i(TAG, "NetworkMonitorService stopped")
    }

    // ─── Network State ────────────────────────────────────────────────

    private fun updateNetworkState() {
        previousNetworkType = currentNetworkType
        currentNetworkType = getCurrentNetworkType()

        val wifiInfo = getWifiInfo()
        currentSsid = wifiInfo.first
        currentSignalStrength = wifiInfo.second

        // Detect network switch
        if (previousNetworkType != currentNetworkType && previousNetworkType != "none") {
            Logger.i(TAG, "Network switch: $previousNetworkType -> $currentNetworkType")
            recordNetworkEvent(EVENT_NETWORK_SWITCH, "Switched from $previousNetworkType to $currentNetworkType")
            sendNetworkEventBroadcast(EVENT_NETWORK_SWITCH)
        }

        // Detect specific events
        when {
            currentNetworkType == "wifi" && previousNetworkType != "wifi" -> {
                Logger.i(TAG, "WiFi connected: $currentSsid")
                recordNetworkEvent(EVENT_WIFI_CONNECTED, "Connected to $currentSsid (signal: $currentSignalStrength%)")
                sendNetworkEventBroadcast(EVENT_WIFI_CONNECTED)
            }
            currentNetworkType != "wifi" && previousNetworkType == "wifi" -> {
                Logger.i(TAG, "WiFi disconnected")
                recordNetworkEvent(EVENT_WIFI_DISCONNECTED, "WiFi disconnected")
                sendNetworkEventBroadcast(EVENT_WIFI_DISCONNECTED)
            }
            currentNetworkType == "mobile" && previousNetworkType != "mobile" -> {
                Logger.i(TAG, "Mobile data connected")
                recordNetworkEvent(EVENT_MOBILE_CONNECTED, "Mobile data connected")
                sendNetworkEventBroadcast(EVENT_MOBILE_CONNECTED)
            }
            currentNetworkType == "none" && previousNetworkType != "none" -> {
                Logger.i(TAG, "No network connection")
                recordNetworkEvent(EVENT_NO_NETWORK, "Network disconnected")
                sendNetworkEventBroadcast(EVENT_NO_NETWORK)
            }
        }

        calculateConnectionQuality()
    }

    private fun getCurrentNetworkType(): String {
        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            when {
                capabilities == null -> "none"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                else -> "unknown"
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get network type", e)
            "unknown"
        }
    }

    private fun getWifiInfo(): Pair<String, Int> {
        return try {
            val wm = wifiManager ?: return Pair("", 0)
            if (!wm.isWifiEnabled) return Pair("", 0)

            val wifiInfo = wm.connectionInfo
            val ssid = wifiInfo?.ssid?.removeSurrounding("\"") ?: ""
            val rssi = wifiInfo?.rssi ?: -100
            val level = WifiManager.calculateSignalLevel(rssi, 101) // 0-100

            Pair(ssid, level)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get WiFi info", e)
            Pair("", 0)
        }
    }

    private fun getNetworkLinkSpeed(): Int {
        return try {
            val wm = wifiManager ?: return 0
            if (!wm.isWifiEnabled) return 0
            val wifiInfo = wm.connectionInfo
            wifiInfo?.linkSpeed ?: 0 // Mbps
        } catch (e: Exception) {
            0
        }
    }

    // ─── Speed Monitoring ─────────────────────────────────────────────

    private fun updateNetworkSpeed() {
        try {
            val currentRxBytes = getTotalRxBytes()
            val currentTxBytes = getTotalTxBytes()
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - lastSpeedCheckTime

            if (elapsed > 0) {
                val rxDiff = currentRxBytes - lastRxBytes
                val txDiff = currentTxBytes - lastTxBytes

                lastDownloadSpeed = (rxDiff.toDouble() / elapsed) * 1000.0 // bytes/sec
                lastUploadSpeed = (txDiff.toDouble() / elapsed) * 1000.0 // bytes/sec

                // Save totals
                prefs.edit()
                    .putLong(KEY_TOTAL_RX, currentRxBytes)
                    .putLong(KEY_TOTAL_TX, currentTxBytes)
                    .apply()
            }

            lastRxBytes = currentRxBytes
            lastTxBytes = currentTxBytes
            lastSpeedCheckTime = currentTime
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update network speed", e)
        }
    }

    private fun getTotalRxBytes(): Long {
        return try {
            readNetworkBytesFromProc("rx")
        } catch (e: Exception) {
            0L
        }
    }

    private fun getTotalTxBytes(): Long {
        return try {
            readNetworkBytesFromProc("tx")
        } catch (e: Exception) {
            0L
        }
    }

    private fun readNetworkBytesFromProc(direction: String): Long {
        return try {
            val lines = java.io.File("/proc/net/dev").readLines()
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

    // ─── Connection Quality ───────────────────────────────────────────

    private fun calculateConnectionQuality() {
        val score = when (currentNetworkType) {
            "wifi" -> {
                var s = 70 // Base WiFi score
                s += (currentSignalStrength * 0.3).toInt() // Signal contribution
                val linkSpeed = getNetworkLinkSpeed()
                if (linkSpeed > 0) s += minOf(linkSpeed / 10, 20) // Link speed contribution
                if (lastDownloadSpeed > 1_000_000) s += 10 // High download speed bonus
                minOf(s, 100)
            }
            "mobile" -> {
                var s = 50 // Base mobile score
                s += (currentSignalStrength * 0.2).toInt()
                if (lastDownloadSpeed > 500_000) s += 15
                minOf(s, 100)
            }
            "ethernet" -> 95
            "vpn" -> 60
            "none" -> 0
            else -> 30
        }

        connectionQuality = score.coerceIn(0, 100)
    }

    fun getConnectionQualityLabel(): String {
        return when {
            connectionQuality >= 80 -> "Excellent"
            connectionQuality >= 60 -> "Good"
            connectionQuality >= 40 -> "Fair"
            connectionQuality >= 20 -> "Poor"
            else -> "No Connection"
        }
    }

    // ─── Speed Test ───────────────────────────────────────────────────

    private fun performSpeedTest() {
        serviceScope.launch {
            Logger.i(TAG, "Starting speed test...")
            try {
                // Download speed test - measure time to read data
                val downloadStart = System.currentTimeMillis()
                var downloadBytes = 0L

                try {
                    val url = java.net.URL("https://www.google.com")
                    val connection = url.openConnection()
                    connection.connectTimeout = 5000
                    connection.readTimeout = 10000
                    val input = connection.getInputStream()
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    val maxDuration = 5000L // 5 second test

                    while (System.currentTimeMillis() - downloadStart < maxDuration) {
                        bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        downloadBytes += bytesRead
                    }
                    input.close()
                } catch (e: Exception) {
                    Logger.e(TAG, "Speed test download failed", e)
                }

                val downloadDuration = System.currentTimeMillis() - downloadStart
                val downloadSpeedBps = if (downloadDuration > 0) {
                    (downloadBytes.toDouble() / downloadDuration) * 1000.0
                } else 0.0

                lastDownloadSpeed = downloadSpeedBps

                Logger.i(TAG, "Speed test complete: download=${formatSpeed(downloadSpeedBps)}")
                recordNetworkEvent(
                    EVENT_SPEED_TEST_COMPLETE,
                    "Download: ${formatSpeed(downloadSpeedBps)}"
                )
                sendNetworkEventBroadcast(EVENT_SPEED_TEST_COMPLETE)

            } catch (e: Exception) {
                Logger.e(TAG, "Speed test failed", e)
            }
        }
    }

    // ─── DNS Monitoring ───────────────────────────────────────────────

    private fun checkDnsResolution(hostname: String = "www.google.com"): Long {
        return try {
            val start = System.nanoTime()
            InetAddress.getByName(hostname)
            val elapsed = (System.nanoTime() - start) / 1_000_000 // ms
            Logger.d(TAG, "DNS resolution for $hostname: ${elapsed}ms")
            elapsed
        } catch (e: Exception) {
            Logger.e(TAG, "DNS resolution failed for $hostname", e)
            -1L
        }
    }

    // ─── Network Callback Registration ────────────────────────────────

    private fun registerNetworkCallback() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            Logger.d(TAG, "Network callback registered")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Logger.d(TAG, "Network callback unregistered")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to unregister network callback", e)
        }
    }

    private fun registerConnectivityReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            }
            // WiFi/Connectivity broadcasts are system broadcasts - need RECEIVER_EXPORTED on Android 14+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(connectivityReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(connectivityReceiver, filter, Context.RECEIVER_EXPORTED)
            }
            Logger.d(TAG, "Connectivity receiver registered")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register connectivity receiver", e)
        }
    }

    private fun unregisterConnectivityReceiver() {
        try {
            unregisterReceiver(connectivityReceiver)
            Logger.d(TAG, "Connectivity receiver unregistered")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to unregister connectivity receiver", e)
        }
    }

    // ─── Broadcast & History ──────────────────────────────────────────

    private fun sendNetworkEventBroadcast(eventType: String) {
        val intent = Intent(ACTION_NETWORK_EVENT).apply {
            putExtra(EXTRA_EVENT_TYPE, eventType)
            putExtra(EXTRA_NETWORK_TYPE, currentNetworkType)
            putExtra(EXTRA_WIFI_SSID, currentSsid)
            putExtra(EXTRA_SIGNAL_STRENGTH, currentSignalStrength)
            putExtra(EXTRA_CONNECTION_QUALITY, connectionQuality)
            putExtra(EXTRA_DOWNLOAD_SPEED, lastDownloadSpeed)
            putExtra(EXTRA_UPLOAD_SPEED, lastUploadSpeed)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun recordNetworkEvent(eventType: String, description: String) {
        try {
            val history = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")

            val event = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("type", eventType)
                put("description", description)
                put("networkType", currentNetworkType)
                put("ssid", currentSsid)
                put("signalStrength", currentSignalStrength)
                put("connectionQuality", connectionQuality)
            }

            history.put(event)

            // Trim to max size
            while (history.length() > MAX_HISTORY_ENTRIES) {
                history.remove(0)
            }

            prefs.edit()
                .putString(KEY_HISTORY, history.toString())
                .putString(KEY_LAST_NETWORK_TYPE, currentNetworkType)
                .apply()

            Logger.d(TAG, "Network event recorded: $eventType")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to record network event", e)
        }
    }

    // ─── Notification ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "HackerLauncher network monitor status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, NetworkMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speedTestIntent = Intent(this, NetworkMonitorService::class.java).apply {
            action = ACTION_SPEED_TEST
        }
        val speedTestPendingIntent = PendingIntent.getService(
            this, 1, speedTestIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Network Monitor")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_media_play,
                "Speed Test",
                speedTestPendingIntent
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
            val networkLabel = when (currentNetworkType) {
                "wifi" -> "WiFi: $currentSsid"
                "mobile" -> "Mobile Data"
                "ethernet" -> "Ethernet"
                "vpn" -> "VPN"
                "none" -> "No Connection"
                else -> currentNetworkType
            }
            val speedInfo = "↓${formatSpeed(lastDownloadSpeed)} ↑${formatSpeed(lastUploadSpeed)}"
            val qualityInfo = "Quality: ${getConnectionQualityLabel()} ($connectionQuality%)"

            val text = "$networkLabel | $speedInfo | $qualityInfo"
            val notification = buildNotification(text)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update notification", e)
        }
    }

    // ─── Utility ──────────────────────────────────────────────────────

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1_000_000 -> String.format("%.1f MB/s", bytesPerSec / 1_000_000)
            bytesPerSec >= 1_000 -> String.format("%.1f KB/s", bytesPerSec / 1_000)
            else -> String.format("%.0f B/s", bytesPerSec)
        }
    }

    fun getNetworkHistory(): JSONArray {
        return try {
            JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
    }
}
