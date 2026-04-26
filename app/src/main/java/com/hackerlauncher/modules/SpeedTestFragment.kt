package com.hackerlauncher.modules

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SpeedTestFragment : Fragment() {

    private lateinit var buttonStartTest: Button
    private lateinit var textViewDownloadSpeed: TextView
    private lateinit var textViewUploadSpeed: TextView
    private lateinit var textViewPing: TextView
    private lateinit var textViewJitter: TextView
    private lateinit var textViewStatus: TextView
    private lateinit var progressBarTest: ProgressBar
    private lateinit var recyclerViewHistory: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var textViewCurrentSpeed: TextView

    private var testJob: Job? = null
    private var isTesting = false

    private val historyList = mutableListOf<SpeedTestResult>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // Test server URLs for download
    private val downloadUrls = listOf(
        "https://speed.cloudflare.com/__down?bytes=10000000",
        "https://speed.hetzner.de/1GB.bin",
        "http://speedtest.tele2.net/10MB.zip"
    )

    // Test server URL for upload
    private val uploadUrl = "https://speed.cloudflare.com/__up"

    // Ping hosts
    private val pingHosts = listOf(
        "1.1.1.1",
        "8.8.8.8",
        "speed.cloudflare.com"
    )

    data class SpeedTestResult(
        val id: String,
        val timestamp: Long,
        val downloadSpeed: Double,  // Mbps
        val uploadSpeed: Double,    // Mbps
        val ping: Double,           // ms
        val jitter: Double,         // ms
        val server: String
    )

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_speed_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonStartTest = view.findViewById(R.id.buttonStartTest)
        textViewDownloadSpeed = view.findViewById(R.id.textViewDownloadSpeed)
        textViewUploadSpeed = view.findViewById(R.id.textViewUploadSpeed)
        textViewPing = view.findViewById(R.id.textViewPing)
        textViewJitter = view.findViewById(R.id.textViewJitter)
        textViewStatus = view.findViewById(R.id.textViewStatus)
        progressBarTest = view.findViewById(R.id.progressBarTest)
        recyclerViewHistory = view.findViewById(R.id.recyclerViewHistory)
        emptyStateText = view.findViewById(R.id.textViewEmptyState)
        textViewCurrentSpeed = view.findViewById(R.id.textViewCurrentSpeed)

        setupRecyclerView()
        setupButtons()
        loadHistory()
        resetDisplay()
    }

    private fun setupRecyclerView() {
        recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewHistory.adapter = SpeedTestHistoryAdapter(historyList)
    }

    private fun setupButtons() {
        buttonStartTest.setOnClickListener {
            if (!isTesting) {
                startSpeedTest()
            } else {
                cancelTest()
            }
        }
    }

    private fun startSpeedTest() {
        isTesting = true
        buttonStartTest.text = "CANCEL"
        progressBarTest.isIndeterminate = true
        textViewStatus.text = "[>] Initializing test..."
        textViewStatus.setTextColor(0xFFFFFF00.toInt())

        testJob = lifecycleScope.launch {
            try {
                // Phase 1: Ping test
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "[>] Testing latency..."
                    progressBarTest.progress = 10
                }

                val pingResult = withContext(Dispatchers.IO) {
                    performPingTest()
                }

                withContext(Dispatchers.Main) {
                    textViewPing.text = "${"%.1f".format(pingResult.first)} ms"
                    textViewJitter.text = "${"%.1f".format(pingResult.second)} ms"
                    textViewPing.setTextColor(
                        when {
                            pingResult.first < 20 -> 0xFF00FF00.toInt()
                            pingResult.first < 50 -> 0xFF88FF00.toInt()
                            pingResult.first < 100 -> 0xFFFFFF00.toInt()
                            else -> 0xFFFF4444.toInt()
                        }
                    )
                    progressBarTest.progress = 25
                }

                // Phase 2: Download test
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "[>] Testing download..."
                    textViewCurrentSpeed.text = "↓ Measuring..."
                    textViewCurrentSpeed.setTextColor(0xFF00FF00.toInt())
                }

                val downloadSpeed = withContext(Dispatchers.IO) {
                    performDownloadTest()
                }

                withContext(Dispatchers.Main) {
                    textViewDownloadSpeed.text = "${"%.2f".format(downloadSpeed)} Mbps"
                    textViewDownloadSpeed.setTextColor(
                        when {
                            downloadSpeed > 50 -> 0xFF00FF00.toInt()
                            downloadSpeed > 20 -> 0xFF88FF00.toInt()
                            downloadSpeed > 5 -> 0xFFFFFF00.toInt()
                            else -> 0xFFFF4444.toInt()
                        }
                    )
                    progressBarTest.progress = 60
                }

                // Phase 3: Upload test
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "[>] Testing upload..."
                    textViewCurrentSpeed.text = "↑ Measuring..."
                    textViewCurrentSpeed.setTextColor(0xFF00CCFF.toInt())
                }

                val uploadSpeed = withContext(Dispatchers.IO) {
                    performUploadTest()
                }

                withContext(Dispatchers.Main) {
                    textViewUploadSpeed.text = "${"%.2f".format(uploadSpeed)} Mbps"
                    textViewUploadSpeed.setTextColor(
                        when {
                            uploadSpeed > 25 -> 0xFF00FF00.toInt()
                            uploadSpeed > 10 -> 0xFF88FF00.toInt()
                            uploadSpeed > 2 -> 0xFFFFFF00.toInt()
                            else -> 0xFFFF4444.toInt()
                        }
                    )
                    progressBarTest.progress = 100
                }

                // Save result
                val result = SpeedTestResult(
                    id = System.currentTimeMillis().toString(),
                    timestamp = System.currentTimeMillis(),
                    downloadSpeed = downloadSpeed,
                    uploadSpeed = uploadSpeed,
                    ping = pingResult.first,
                    jitter = pingResult.second,
                    server = "Cloudflare"
                )

                historyList.add(0, result)
                saveHistory()
                recyclerViewHistory.adapter?.notifyItemInserted(0)
                updateEmptyState()

                withContext(Dispatchers.Main) {
                    textViewStatus.text = "[OK] Test complete"
                    textViewStatus.setTextColor(0xFF00FF00.toInt())
                    textViewCurrentSpeed.text = ""
                    Toast.makeText(requireContext(), "Speed test complete!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    textViewStatus.text = "[!] Test failed: ${e.message}"
                    textViewStatus.setTextColor(0xFFFF0000.toInt())
                    textViewCurrentSpeed.text = ""
                    Toast.makeText(requireContext(), "Speed test failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isTesting = false
                withContext(Dispatchers.Main) {
                    buttonStartTest.text = "START TEST"
                    progressBarTest.isIndeterminate = false
                    progressBarTest.progress = 0
                }
            }
        }
    }

    private fun cancelTest() {
        testJob?.cancel()
        testJob = null
        isTesting = false
        buttonStartTest.text = "START TEST"
        progressBarTest.isIndeterminate = false
        progressBarTest.progress = 0
        textViewStatus.text = "[!] Test cancelled"
        textViewStatus.setTextColor(0xFFFF8800.toInt())
        textViewCurrentSpeed.text = ""
    }

    private fun performPingTest(): Pair<Double, Double> {
        val latencies = mutableListOf<Long>()

        for (host in pingHosts) {
            for (i in 0 until 5) {
                try {
                    val startTime = System.currentTimeMillis()

                    // Try ICMP-style socket ping
                    try {
                        val address = InetAddress.getByName(host)
                        val reachable = address.isReachable(5000)
                        if (reachable) {
                            val endTime = System.currentTimeMillis()
                            latencies.add(endTime - startTime)
                        }
                    } catch (_: Exception) {
                        // Fallback: TCP socket connect
                        try {
                            val socket = java.net.Socket()
                            socket.connect(
                                java.net.InetSocketAddress(host, 80),
                                5000
                            )
                            val endTime = System.currentTimeMillis()
                            latencies.add(endTime - startTime)
                            socket.close()
                        } catch (_: Exception) {
                        }
                    }

                    Thread.sleep(200)
                } catch (_: Exception) {
                }
            }

            if (latencies.isNotEmpty()) break
        }

        if (latencies.isEmpty()) {
            return Pair(-1.0, -1.0)
        }

        val avgPing = latencies.average()
        val jitter = if (latencies.size > 1) {
            val diffs = mutableListOf<Double>()
            for (i in 1 until latencies.size) {
                diffs.add(Math.abs(latencies[i].toDouble() - latencies[i - 1].toDouble()))
            }
            diffs.average()
        } else {
            0.0
        }

        return Pair(avgPing, jitter)
    }

    private suspend fun performDownloadTest(): Double {
        var totalBytes = 0L
        var bestSpeed = 0.0

        for (url in downloadUrls) {
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

                    if (elapsed > 0 && currentTime - lastUpdate > 200) {
                        val speedBps = totalBytes / elapsed
                        val speedMbps = (speedBps * 8) / 1_000_000.0

                        if (speedMbps > bestSpeed) bestSpeed = speedMbps

                        withContext(Dispatchers.Main) {
                            textViewCurrentSpeed.text = "↓ ${"%.2f".format(speedMbps)} Mbps"
                        }
                        lastUpdate = currentTime
                    }

                    // Limit download to ~10MB max for speed test
                    if (totalBytes > 10 * 1024 * 1024) break
                }

                inputStream.close()
                response.close()

                val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
                if (totalTime > 0) {
                    val speedBps = totalBytes / totalTime
                    val speedMbps = (speedBps * 8) / 1_000_000.0
                    if (speedMbps > bestSpeed) bestSpeed = speedMbps
                }

                break // Successfully tested, don't try other URLs
            } catch (_: Exception) {
                continue
            }
        }

        return bestSpeed
    }

    private suspend fun performUploadTest(): Double {
        var totalBytes = 0L
        var bestSpeed = 0.0

        try {
            val uploadData = ByteArray(2 * 1024 * 1024) // 2MB test data
            java.util.Random().nextBytes(uploadData)

            val startTime = System.currentTimeMillis()

            val request = Request.Builder()
                .url(uploadUrl)
                .post(uploadData.toRequestBody())
                .build()

            val response = okHttpClient.newCall(request).execute()
            totalBytes = uploadData.size.toLong()

            val endTime = System.currentTimeMillis()
            val elapsed = (endTime - startTime) / 1000.0

            if (elapsed > 0) {
                val speedBps = totalBytes / elapsed
                bestSpeed = (speedBps * 8) / 1_000_000.0
            }

            withContext(Dispatchers.Main) {
                textViewCurrentSpeed.text = "↑ ${"%.2f".format(bestSpeed)} Mbps"
            }

            response.close()

            // Try a second upload for better measurement
            val startTime2 = System.currentTimeMillis()
            val request2 = Request.Builder()
                .url(uploadUrl)
                .post(uploadData.toRequestBody())
                .build()
            val response2 = okHttpClient.newCall(request2).execute()
            val endTime2 = System.currentTimeMillis()
            val elapsed2 = (endTime2 - startTime2) / 1000.0
            val totalBytes2 = (totalBytes + uploadData.size).toDouble()
            if (elapsed2 > 0) {
                val avgSpeed = (totalBytes2 * 8) / ((elapsed + elapsed2) * 1_000_000.0)
                if (avgSpeed > bestSpeed) bestSpeed = avgSpeed
            }
            response2.close()

        } catch (e: Exception) {
            // Upload test may fail due to CORS/server restrictions
            bestSpeed = -1.0
        }

        return bestSpeed
    }

    private fun resetDisplay() {
        textViewDownloadSpeed.text = "-- Mbps"
        textViewUploadSpeed.text = "-- Mbps"
        textViewPing.text = "-- ms"
        textViewJitter.text = "-- ms"
        textViewCurrentSpeed.text = ""
        textViewStatus.text = "[>] Ready to test"
        textViewStatus.setTextColor(0xFF00FF00.toInt())
    }

    private fun saveHistory() {
        val prefs = requireContext().getSharedPreferences("speed_test_prefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in historyList.take(50)) { // Keep last 50
            val jsonObj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("downloadSpeed", item.downloadSpeed)
                put("uploadSpeed", item.uploadSpeed)
                put("ping", item.ping)
                put("jitter", item.jitter)
                put("server", item.server)
            }
            jsonArray.put(jsonObj)
        }
        prefs.edit().putString("history", jsonArray.toString()).apply()
    }

    private fun loadHistory() {
        val prefs = requireContext().getSharedPreferences("speed_test_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("history", null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            historyList.clear()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                historyList.add(
                    SpeedTestResult(
                        id = jsonObj.getString("id"),
                        timestamp = jsonObj.getLong("timestamp"),
                        downloadSpeed = jsonObj.getDouble("downloadSpeed"),
                        uploadSpeed = jsonObj.getDouble("uploadSpeed"),
                        ping = jsonObj.getDouble("ping"),
                        jitter = jsonObj.getDouble("jitter"),
                        server = jsonObj.optString("server", "Unknown")
                    )
                )
            }
            recyclerViewHistory.adapter?.notifyDataSetChanged()
        } catch (_: Exception) {
        }
        updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyStateText.visibility = if (historyList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        testJob?.cancel()
    }

    inner class SpeedTestHistoryAdapter(
        private val items: List<SpeedTestResult>
    ) : RecyclerView.Adapter<SpeedTestHistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textDate: TextView = view.findViewById(R.id.textViewTestDate)
            val textDownload: TextView = view.findViewById(R.id.textViewHistDownload)
            val textUpload: TextView = view.findViewById(R.id.textViewHistUpload)
            val textPing: TextView = view.findViewById(R.id.textViewHistPing)
            val textServer: TextView = view.findViewById(R.id.textViewHistServer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_speed_test, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textDate.text = dateFormat.format(Date(item.timestamp))
            holder.textDownload.text = "↓ ${"%.2f".format(item.downloadSpeed)} Mbps"
            holder.textUpload.text = "↑ ${"%.2f".format(item.uploadSpeed)} Mbps"
            holder.textPing.text = "${"%.1f".format(item.ping)} ms"
            holder.textServer.text = item.server

            holder.textDownload.setTextColor(
                when {
                    item.downloadSpeed > 50 -> 0xFF00FF00.toInt()
                    item.downloadSpeed > 20 -> 0xFFFFFF00.toInt()
                    else -> 0xFFFF4444.toInt()
                }
            )
            holder.textUpload.setTextColor(
                when {
                    item.uploadSpeed > 25 -> 0xFF00FF00.toInt()
                    item.uploadSpeed > 10 -> 0xFFFFFF00.toInt()
                    else -> 0xFFFF4444.toInt()
                }
            )
            holder.textPing.setTextColor(
                when {
                    item.ping < 20 -> 0xFF00FF00.toInt()
                    item.ping < 50 -> 0xFFFFFF00.toInt()
                    else -> 0xFFFF4444.toInt()
                }
            )
        }

        override fun getItemCount(): Int = items.size
    }
}
