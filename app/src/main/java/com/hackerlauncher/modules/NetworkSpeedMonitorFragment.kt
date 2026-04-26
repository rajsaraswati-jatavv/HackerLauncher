package com.hackerlauncher.modules

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hackerlauncher.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit

data class NetworkConnection(
    val appName: String,
    val remoteAddress: String,
    val protocol: String,
    val state: String
)

class SpeedGraphView(context: Context) : View(context) {

    private val downloadPoints = mutableListOf<Float>()
    private val uploadPoints = mutableListOf<Float>()
    private val maxPoints = 60

    private val downloadLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#00FF00")
    }

    private val uploadLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#00CCFF")
    }

    private val downloadFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00FF00")
        alpha = 30
    }

    private val uploadFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00CCFF")
        alpha = 30
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#333333")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = Color.parseColor("#888888")
    }

    private val legendDownloadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = Color.parseColor("#00FF00")
    }

    private val legendUploadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = Color.parseColor("#00CCFF")
    }

    private var maxValue = 1f

    fun addDataPoint(downloadSpeed: Float, uploadSpeed: Float) {
        downloadPoints.add(downloadSpeed)
        uploadPoints.add(uploadSpeed)
        if (downloadPoints.size > maxPoints) {
            downloadPoints.removeAt(0)
        }
        if (uploadPoints.size > maxPoints) {
            uploadPoints.removeAt(0)
        }
        maxValue = maxOf(
            downloadPoints.maxOrNull() ?: 1f,
            uploadPoints.maxOrNull() ?: 1f,
            1f
        )
        invalidate()
    }

    fun clearData() {
        downloadPoints.clear()
        uploadPoints.clear()
        maxValue = 1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 40f
        val topPadding = 50f

        // Draw grid lines
        for (i in 0..4) {
            val y = topPadding + (h - topPadding - padding) * i / 4f
            canvas.drawLine(padding, y, w - padding, y, gridPaint)
            val label = String.format("%.0f", maxValue * (4 - i) / 4f)
            canvas.drawText(label, 2f, y + 8f, labelPaint)
        }

        // Draw legend
        canvas.drawText("↓ Download", w - 280f, 30f, legendDownloadPaint)
        canvas.drawText("↑ Upload", w - 130f, 30f, legendUploadPaint)

        // Bottom axis line
        val bottomY = h - padding
        canvas.drawLine(padding, bottomY, w - padding, bottomY, gridPaint)

        val drawWidth = w - 2 * padding
        val drawHeight = h - topPadding - padding

        drawLine(canvas, downloadPoints, downloadLinePaint, downloadFillPaint, padding, topPadding, drawWidth, drawHeight, bottomY)
        drawLine(canvas, uploadPoints, uploadLinePaint, uploadFillPaint, padding, topPadding, drawWidth, drawHeight, bottomY)
    }

    private fun drawLine(
        canvas: Canvas,
        points: List<Float>,
        linePaint: Paint,
        fillPaint: Paint,
        padding: Float,
        topPadding: Float,
        drawWidth: Float,
        drawHeight: Float,
        bottomY: Float
    ) {
        if (points.size < 2) return

        val step = drawWidth / (maxPoints - 1)
        val startX = padding + (maxPoints - points.size) * step

        val linePath = Path()
        val fillPath = Path()

        val firstY = bottomY - (points[0] / maxValue) * drawHeight
        linePath.moveTo(startX, firstY)
        fillPath.moveTo(startX, bottomY)
        fillPath.lineTo(startX, firstY)

        for (i in 1 until points.size) {
            val x = startX + i * step
            val y = bottomY - (points[i] / maxValue) * drawHeight
            linePath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }

        fillPath.lineTo(startX + (points.size - 1) * step, bottomY)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }
}

class NetworkSpeedMonitorFragment : Fragment() {

    private lateinit var textViewDownloadSpeed: TextView
    private lateinit var textViewUploadSpeed: TextView
    private lateinit var textViewTotalDown: TextView
    private lateinit var textViewTotalUp: TextView
    private lateinit var textViewNetworkType: TextView
    private lateinit var textViewIpAddress: TextView
    private lateinit var textViewDns: TextView
    private lateinit var textViewGateway: TextView
    private lateinit var textViewConnectionQuality: TextView
    private lateinit var speedGraphView: SpeedGraphView
    private lateinit var buttonSpeedTest: Button
    private lateinit var progressBarSpeedTest: ProgressBar
    private lateinit var textViewLatency: TextView
    private lateinit var recyclerViewConnections: RecyclerView

    private var monitorJob: Job? = null
    private var speedTestJob: Job? = null
    private var isSpeedTestRunning = false

    private val connectionList = mutableListOf<NetworkConnection>()

    private var prevRxBytes: Long = 0
    private var prevTxBytes: Long = 0
    private var prevTimestamp: Long = 0
    private var bootRxBytes: Long = 0
    private var bootTxBytes: Long = 0

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val speedTestUrls = listOf(
        "https://speed.cloudflare.com/__down?bytes=10000000",
        "https://speed.hetzner.de/1GB.bin",
        "http://speedtest.tele2.net/10MB.zip"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_network_speed_monitor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textViewDownloadSpeed = view.findViewById(R.id.textViewDownloadSpeed)
        textViewUploadSpeed = view.findViewById(R.id.textViewUploadSpeed)
        textViewTotalDown = view.findViewById(R.id.textViewTotalDown)
        textViewTotalUp = view.findViewById(R.id.textViewTotalUp)
        textViewNetworkType = view.findViewById(R.id.textViewNetworkType)
        textViewIpAddress = view.findViewById(R.id.textViewIpAddress)
        textViewDns = view.findViewById(R.id.textViewDns)
        textViewGateway = view.findViewById(R.id.textViewGateway)
        textViewConnectionQuality = view.findViewById(R.id.textViewConnectionQuality)
        speedGraphView = view.findViewById(R.id.speedGraphView)
        buttonSpeedTest = view.findViewById(R.id.buttonSpeedTest)
        progressBarSpeedTest = view.findViewById(R.id.progressBarSpeedTest)
        textViewLatency = view.findViewById(R.id.textViewLatency)
        recyclerViewConnections = view.findViewById(R.id.recyclerViewConnections)

        setupRecyclerView()
        setupButtons()
        initBootCounters()
        updateStaticInfo()
        startMonitoring()
    }

    private fun setupRecyclerView() {
        recyclerViewConnections.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewConnections.adapter = NetworkConnectionAdapter(connectionList)
    }

    private fun setupButtons() {
        buttonSpeedTest.setOnClickListener {
            if (!isSpeedTestRunning) {
                startSpeedTest()
            } else {
                Toast.makeText(requireContext(), "Speed test already running...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initBootCounters() {
        try {
            val netDevFile = File("/proc/net/dev")
            if (netDevFile.exists()) {
                val lines = BufferedReader(FileReader(netDevFile)).readLines()
                var totalRx = 0L
                var totalTx = 0L
                for (line in lines) {
                    if (line.contains(":")) {
                        val parts = line.split(":")
                        if (parts.size == 2) {
                            val values = parts[1].trim().split("\\s+".toRegex())
                            if (values.size >= 10) {
                                totalRx += values[0].toLongOrNull() ?: 0L
                                totalTx += values[8].toLongOrNull() ?: 0L
                            }
                        }
                    }
                }
                bootRxBytes = totalRx
                bootTxBytes = totalTx
                prevRxBytes = totalRx
                prevTxBytes = totalTx
                prevTimestamp = System.currentTimeMillis()
            }
        } catch (_: Exception) {
        }
    }

    private fun updateStaticInfo() {
        lifecycleScope.launch {
            val networkType = withContext(Dispatchers.IO) { getNetworkType() }
            val ipAddress = withContext(Dispatchers.IO) { getIpAddress() }
            val dns = withContext(Dispatchers.IO) { getDnsServers() }
            val gateway = withContext(Dispatchers.IO) { getGateway() }

            textViewNetworkType.text = "Type: $networkType"
            textViewIpAddress.text = "IP: $ipAddress"
            textViewDns.text = "DNS: $dns"
            textViewGateway.text = "Gateway: $gateway"
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch {
            while (isActive) {
                refreshSpeedData()
                delay(2000)
            }
        }
    }

    private suspend fun refreshSpeedData() {
        withContext(Dispatchers.IO) {
            val speedData = readNetworkSpeed()
            val connections = readNetworkConnections()

            withContext(Dispatchers.Main) {
                textViewDownloadSpeed.text = "↓ ${formatSpeed(speedData.first)}"
                textViewUploadSpeed.text = "↑ ${formatSpeed(speedData.second)}"

                textViewDownloadSpeed.setTextColor(
                    when {
                        speedData.first > 1024 * 1024 -> 0xFF00FF00.toInt()
                        speedData.first > 256 * 1024 -> 0xFFFFFF00.toInt()
                        else -> 0xFFFF4444.toInt()
                    }
                )
                textViewUploadSpeed.setTextColor(
                    when {
                        speedData.second > 512 * 1024 -> 0xFF00CCFF.toInt()
                        speedData.second > 128 * 1024 -> 0xFFFFFF00.toInt()
                        else -> 0xFFFF4444.toInt()
                    }
                )

                textViewTotalDown.text = "Total ↓: ${formatBytes(speedData.third)}"
                textViewTotalUp.text = "Total ↑: ${formatBytes(speedData.fourth)}"

                // Connection quality indicator
                val quality = when {
                    speedData.first > 5 * 1024 * 1024 -> "EXCELLENT"
                    speedData.first > 1024 * 1024 -> "GOOD"
                    speedData.first > 256 * 1024 -> "FAIR"
                    speedData.first > 0 -> "POOR"
                    else -> "NO CONNECTION"
                }
                textViewConnectionQuality.text = "Quality: $quality"
                textViewConnectionQuality.setTextColor(
                    when (quality) {
                        "EXCELLENT" -> 0xFF00FF00.toInt()
                        "GOOD" -> 0xFF88FF00.toInt()
                        "FAIR" -> 0xFFFFFF00.toInt()
                        "POOR" -> 0xFFFF8800.toInt()
                        else -> 0xFFFF4444.toInt()
                    }
                )

                // Update graph (convert to KB/s for display)
                val dlKbps = speedData.first / 1024f
                val ulKbps = speedData.second / 1024f
                speedGraphView.addDataPoint(dlKbps, ulKbps)

                connectionList.clear()
                connectionList.addAll(connections)
                recyclerViewConnections.adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun readNetworkSpeed(): Tuple4<Long, Long, Long, Long> {
        try {
            val netDevFile = File("/proc/net/dev")
            if (!netDevFile.exists()) return Tuple4(0L, 0L, 0L, 0L)

            val lines = BufferedReader(FileReader(netDevFile)).readLines()
            var totalRx = 0L
            var totalTx = 0L

            for (line in lines) {
                if (line.contains(":")) {
                    val parts = line.split(":")
                    if (parts.size == 2) {
                        val iface = parts[0].trim()
                        // Skip loopback
                        if (iface == "lo") continue
                        val values = parts[1].trim().split("\\s+".toRegex())
                        if (values.size >= 10) {
                            totalRx += values[0].toLongOrNull() ?: 0L
                            totalTx += values[8].toLongOrNull() ?: 0L
                        }
                    }
                }
            }

            val currentTime = System.currentTimeMillis()
            val timeDelta = currentTime - prevTimestamp

            val rxSpeed = if (timeDelta > 0 && prevRxBytes > 0) {
                ((totalRx - prevRxBytes) * 1000L) / timeDelta
            } else {
                0L
            }

            val txSpeed = if (timeDelta > 0 && prevTxBytes > 0) {
                ((totalTx - prevTxBytes) * 1000L) / timeDelta
            } else {
                0L
            }

            val totalDownSinceBoot = totalRx - bootRxBytes
            val totalUpSinceBoot = totalTx - bootTxBytes

            prevRxBytes = totalRx
            prevTxBytes = totalTx
            prevTimestamp = currentTime

            return Tuple4(
                rxSpeed.coerceAtLeast(0L),
                txSpeed.coerceAtLeast(0L),
                totalDownSinceBoot.coerceAtLeast(0L),
                totalUpSinceBoot.coerceAtLeast(0L)
            )
        } catch (_: Exception) {
            return Tuple4(0L, 0L, 0L, 0L)
        }
    }

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun readNetworkConnections(): List<NetworkConnection> {
        val result = mutableListOf<NetworkConnection>()
        try {
            // Read TCP connections
            val tcpFile = File("/proc/net/tcp")
            if (tcpFile.exists()) {
                val lines = BufferedReader(FileReader(tcpFile)).readLines()
                for (i in 1 until lines.size.coerceAtMost(51)) { // Skip header, limit to 50
                    val line = lines[i].trim()
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val localAddr = parts[1]
                        val remoteAddr = parts[2]
                        val state = parts[3]

                        val remoteIpPort = convertHexToIpPort(remoteAddr)
                        val stateName = tcpStateToString(state.toIntOrNull(16) ?: 0)

                        result.add(
                            NetworkConnection(
                                appName = "-",
                                remoteAddress = remoteIpPort,
                                protocol = "TCP",
                                state = stateName
                            )
                        )
                    }
                }
            }

            // Read TCP6 connections
            val tcp6File = File("/proc/net/tcp6")
            if (tcp6File.exists()) {
                val lines = BufferedReader(FileReader(tcp6File)).readLines()
                for (i in 1 until lines.size.coerceAtMost(26)) {
                    val line = lines[i].trim()
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val remoteAddr = parts[2]
                        val state = parts[3]

                        val remoteIpPort = convertHexToIpPort(remoteAddr)
                        val stateName = tcpStateToString(state.toIntOrNull(16) ?: 0)

                        result.add(
                            NetworkConnection(
                                appName = "-",
                                remoteAddress = remoteIpPort,
                                protocol = "TCP6",
                                state = stateName
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun convertHexToIpPort(hexAddr: String): String {
        try {
            val parts = hexAddr.split(":")
            if (parts.size != 2) return hexAddr

            val hexIp = parts[0]
            val port = parts[1].toIntOrNull(16) ?: 0

            if (hexIp.length == 8) {
                // IPv4
                val ipParts = hexIp.chunked(2).reversed().map {
                    it.toIntOrNull(16) ?: 0
                }
                return "${ipParts.joinToString(".")}:$port"
            } else if (hexIp.length == 32) {
                // IPv6 - abbreviated
                return "[$hexIp]:$port"
            }
        } catch (_: Exception) {
        }
        return hexAddr
    }

    private fun tcpStateToString(state: Int): String = when (state) {
        0x01 -> "ESTABLISHED"
        0x02 -> "SYN_SENT"
        0x03 -> "SYN_RECV"
        0x04 -> "FIN_WAIT1"
        0x05 -> "FIN_WAIT2"
        0x06 -> "TIME_WAIT"
        0x07 -> "CLOSE"
        0x08 -> "CLOSE_WAIT"
        0x09 -> "LAST_ACK"
        0x0A -> "LISTEN"
        0x0B -> "CLOSING"
        else -> "UNKNOWN($state)"
    }

    private fun getNetworkType(): String {
        try {
            val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return "No Connection"
            val caps = cm.getNetworkCapabilities(network) ?: return "Unknown"

            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                else -> "Other"
            }
        } catch (_: Exception) {
            return "Unknown"
        }
    }

    private fun getIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "N/A"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "N/A"
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "N/A"
    }

    private fun getDnsServers(): String {
        try {
            val dnsList = mutableListOf<String>()
            // Try reading from system properties
            val proc = Runtime.getRuntime().exec("getprop net.dns1")
            val dns1 = proc.inputStream.bufferedReader().readLine()?.trim()
            proc.waitFor()

            val proc2 = Runtime.getRuntime().exec("getprop net.dns2")
            val dns2 = proc2.inputStream.bufferedReader().readLine()?.trim()
            proc2.waitFor()

            if (!dns1.isNullOrBlank()) dnsList.add(dns1)
            if (!dns2.isNullOrBlank()) dnsList.add(dns2)

            if (dnsList.isNotEmpty()) return dnsList.joinToString(", ")

            // Fallback: try WiFi manager DNS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val wm = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val dhcpInfo = wm?.dhcpInfo
                    if (dhcpInfo != null) {
                        val d1 = android.text.format.Formatter.formatIpAddress(dhcpInfo.dns1)
                        val d2 = android.text.format.Formatter.formatIpAddress(dhcpInfo.dns2)
                        if (d1 != "0.0.0.0") dnsList.add(d1)
                        if (d2 != "0.0.0.0") dnsList.add(d2)
                    }
                } catch (_: Exception) {}
                if (dnsList.isNotEmpty()) return dnsList.joinToString(", ")
            }

            return "N/A"
        } catch (_: Exception) {
            return "N/A"
        }
    }

    @Suppress("DEPRECATION")
    private fun getGateway(): String {
        try {
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcpInfo = wifiManager.dhcpInfo
            val gateway = dhcpInfo.gateway
            if (gateway != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    gateway and 0xff,
                    gateway shr 8 and 0xff,
                    gateway shr 16 and 0xff,
                    gateway shr 24 and 0xff
                )
            }
        } catch (_: Exception) {
        }
        return "N/A"
    }

    private fun startSpeedTest() {
        isSpeedTestRunning = true
        buttonSpeedTest.text = "TESTING..."
        progressBarSpeedTest.visibility = View.VISIBLE

        speedTestJob = lifecycleScope.launch {
            try {
                // Phase 1: Latency test
                val latency = withContext(Dispatchers.IO) { performLatencyTest() }
                withContext(Dispatchers.Main) {
                    if (latency >= 0) {
                        textViewLatency.text = "Latency: ${"%.1f".format(latency)} ms"
                        textViewLatency.setTextColor(
                            when {
                                latency < 30 -> 0xFF00FF00.toInt()
                                latency < 80 -> 0xFFFFFF00.toInt()
                                else -> 0xFFFF4444.toInt()
                            }
                        )
                    } else {
                        textViewLatency.text = "Latency: N/A"
                    }
                }

                // Phase 2: Download speed test
                val downloadSpeed = withContext(Dispatchers.IO) { performDownloadSpeedTest() }
                withContext(Dispatchers.Main) {
                    if (downloadSpeed > 0) {
                        val speedMbps = (downloadSpeed * 8) / 1_000_000.0
                        Toast.makeText(
                            requireContext(),
                            "Speed test complete: ${"%.2f".format(speedMbps)} Mbps",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(requireContext(), "Speed test failed. Try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Speed test error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSpeedTestRunning = false
                    buttonSpeedTest.text = "SPEED TEST"
                    progressBarSpeedTest.visibility = View.GONE
                }
            }
        }
    }

    private fun performLatencyTest(): Double {
        val hosts = listOf("1.1.1.1", "8.8.8.8", "speed.cloudflare.com")
        val latencies = mutableListOf<Long>()

        for (host in hosts) {
            for (i in 0 until 3) {
                try {
                    val startTime = System.currentTimeMillis()
                    val address = InetAddress.getByName(host)
                    val reachable = address.isReachable(5000)
                    if (reachable) {
                        val endTime = System.currentTimeMillis()
                        latencies.add(endTime - startTime)
                    }
                } catch (_: Exception) {
                    // Try TCP connect fallback
                    try {
                        val startTime = System.currentTimeMillis()
                        val socket = Socket()
                        socket.connect(java.net.InetSocketAddress(host, 80), 5000)
                        val endTime = System.currentTimeMillis()
                        latencies.add(endTime - startTime)
                        socket.close()
                    } catch (_: Exception) {
                    }
                }
                Thread.sleep(100)
            }
            if (latencies.isNotEmpty()) break
        }

        return if (latencies.isNotEmpty()) latencies.average() else -1.0
    }

    private suspend fun performDownloadSpeedTest(): Long {
        var totalBytes = 0L
        var bestSpeedBps = 0L

        for (url in speedTestUrls) {
            try {
                val startTime = System.currentTimeMillis()
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) continue

                val body = response.body ?: continue
                val inputStream = body.byteStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastUpdate = startTime

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    val currentTime = System.currentTimeMillis()
                    val elapsed = (currentTime - startTime) / 1000.0

                    if (elapsed > 0 && currentTime - lastUpdate > 500) {
                        val speedBps = (totalBytes / elapsed).toLong()
                        if (speedBps > bestSpeedBps) bestSpeedBps = speedBps
                        lastUpdate = currentTime
                    }

                    // Limit to 10MB
                    if (totalBytes > 10 * 1024 * 1024) break
                }

                inputStream.close()
                response.close()

                val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
                if (totalTime > 0) {
                    val speedBps = (totalBytes / totalTime).toLong()
                    if (speedBps > bestSpeedBps) bestSpeedBps = speedBps
                }
                break
            } catch (_: Exception) {
                continue
            }
        }

        return bestSpeedBps
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> "${"%.2f".format(bytesPerSecond / (1024.0 * 1024.0))} MB/s"
            bytesPerSecond >= 1024 -> "${"%.1f".format(bytesPerSecond / 1024.0)} KB/s"
            else -> "$bytesPerSecond B/s"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
            bytes >= 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        monitorJob?.cancel()
        speedTestJob?.cancel()
    }

    inner class NetworkConnectionAdapter(
        private val items: List<NetworkConnection>
    ) : RecyclerView.Adapter<NetworkConnectionAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textAppName: TextView = view.findViewById(R.id.textViewAppName)
            val textRemoteAddress: TextView = view.findViewById(R.id.textViewRemoteAddress)
            val textProtocol: TextView = view.findViewById(R.id.textViewProtocol)
            val textState: TextView = view.findViewById(R.id.textViewState)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_network_connection, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textAppName.text = item.appName
            holder.textRemoteAddress.text = item.remoteAddress
            holder.textProtocol.text = item.protocol
            holder.textState.text = item.state

            holder.textState.setTextColor(
                when (item.state) {
                    "ESTABLISHED" -> 0xFF00FF00.toInt()
                    "LISTEN" -> 0xFFFFFF00.toInt()
                    "TIME_WAIT" -> 0xFF888888.toInt()
                    else -> 0xFFFF4444.toInt()
                }
            )
        }

        override fun getItemCount(): Int = items.size
    }
}
