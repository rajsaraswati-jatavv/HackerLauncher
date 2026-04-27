package com.hackerlauncher.modules

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

class PortScannerFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etIp: EditText
    private lateinit var etStartPort: EditText
    private lateinit var etEndPort: EditText
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var scanning = false

    private val commonPorts = mapOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        80 to "HTTP", 110 to "POP3", 143 to "IMAP", 443 to "HTTPS", 445 to "SMB",
        993 to "IMAPS", 995 to "POP3S", 1433 to "MSSQL", 3306 to "MySQL",
        3389 to "RDP", 5432 to "PostgreSQL", 5900 to "VNC", 6379 to "Redis",
        8080 to "HTTP-Alt", 8443 to "HTTPS-Alt", 27017 to "MongoDB"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()

        val rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val tvTitle = TextView(ctx).apply {
            text = "[ PORT SCANNER ]"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvTitle)

        etIp = EditText(ctx).apply {
            hint = "Target IP (e.g. 192.168.1.1)"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        rootLayout.addView(etIp)

        val portRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        etStartPort = EditText(ctx).apply {
            hint = "Start Port"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(8, 8, 8, 8)
            setText("1")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        etEndPort = EditText(ctx).apply {
            hint = "End Port"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(8, 8, 8, 8)
            setText("1024")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        portRow.addView(etStartPort)
        portRow.addView(etEndPort)
        rootLayout.addView(portRow)

        fun makeBtn(label: String, color: Int = 0xFF00FF00.toInt(), listener: () -> Unit) = Button(ctx).apply {
            text = label
            setTextColor(color)
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener { listener() }
        }

        val btnRow1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val btnRow2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        btnRow1.addView(makeBtn("Quick Scan") { quickScan() })
        btnRow1.addView(makeBtn("Full Range") { rangeScan() })
        btnRow1.addView(makeBtn("Common") { commonPortScan() })
        btnRow2.addView(makeBtn("Service Detect") { serviceDetect() })
        btnRow2.addView(makeBtn("Stop", 0xFFFF0000.toInt()) { scanning = false })

        rootLayout.addView(btnRow1)
        rootLayout.addView(btnRow2)

        scrollView = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(0xFF0A0A0A.toInt())
        }
        tvOutput = TextView(ctx).apply {
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
        }
        scrollView.addView(tvOutput)
        rootLayout.addView(scrollView)

        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║      PORT SCANNER v1.0          ║\n")
        appendOutput("║  Concurrent port scanning engine ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return rootLayout
    }

    private fun quickScan() {
        val ip = etIp.text.toString().trim()
        if (ip.isEmpty()) { appendOutput("[!] Enter target IP\n"); return }
        scanPorts(ip, 1, 1024, 50)
    }

    private fun rangeScan() {
        val ip = etIp.text.toString().trim()
        if (ip.isEmpty()) { appendOutput("[!] Enter target IP\n"); return }
        val start = etStartPort.text.toString().toIntOrNull() ?: 1
        val end = etEndPort.text.toString().toIntOrNull() ?: 1024
        scanPorts(ip, start, end, 50)
    }

    private fun commonPortScan() {
        val ip = etIp.text.toString().trim()
        if (ip.isEmpty()) { appendOutput("[!] Enter target IP\n"); return }
        val ports = commonPorts.keys.toList()
        scanning = true

        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║     Common Ports Scan: $ip\n")
        appendOutput("╠══════════════════════════════════╣\n")

        scope.launch {
            var openCount = 0
            val results = withContext(Dispatchers.IO) {
                ports.map { port ->
                    async(Dispatchers.IO) {
                        if (!scanning) return@async null
                        val open = checkPort(ip, port, 500)
                        if (open) {
                            openCount++
                            Triple(port, true, commonPorts[port] ?: "Unknown")
                        } else null
                    }
                }.awaitAll().filterNotNull()
            }

            for ((port, _, service) in results.sortedBy { it.first }) {
                appendOutput("║ [OPEN] Port $port (${commonPorts[port] ?: service})\n")
            }
            appendOutput("║\n║ Open: $openCount/${ports.size} common ports\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
            scanning = false
        }
    }

    private fun scanPorts(ip: String, startPort: Int, endPort: Int, concurrency: Int) {
        if (scanning) { appendOutput("[!] Scan already in progress\n"); return }
        scanning = true

        val totalPorts = endPort - startPort + 1
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║  Scanning $ip\n")
        appendOutput("║  Ports: $startPort-$endPort ($totalPorts ports)\n")
        appendOutput("╠══════════════════════════════════╣\n")

        scope.launch {
            var openCount = 0
            var closedCount = 0
            var filteredCount = 0
            val startTime = System.currentTimeMillis()

            val chunks = (startPort..endPort).chunked(concurrency)
            for (chunk in chunks) {
                if (!scanning) break
                val results = withContext(Dispatchers.IO) {
                    chunk.map { port ->
                        async(Dispatchers.IO) {
                            when (checkPortDetailed(ip, port, 500)) {
                                PortState.OPEN -> Triple(port, PortState.OPEN, commonPorts[port])
                                PortState.FILTERED -> Triple(port, PortState.FILTERED, commonPorts[port])
                                else -> Triple(port, PortState.CLOSED, commonPorts[port])
                            }
                        }
                    }.awaitAll()
                }

                for ((port, state, service) in results) {
                    when (state) {
                        PortState.OPEN -> {
                            openCount++
                            appendOutput("║ [OPEN]    $port${if (service != null) " ($service)" else ""}\n")
                        }
                        PortState.FILTERED -> filteredCount++
                        PortState.CLOSED -> closedCount++
                    }
                }

                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                val progress = ((closedCount + openCount + filteredCount).toDouble() / totalPorts * 100).toInt()
                appendOutput("║  Progress: $progress% | ${elapsed}s elapsed\n")
            }

            val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("║ Results for $ip\n")
            appendOutput("║ Open: $openCount | Closed: $closedCount | Filtered: $filteredCount\n")
            appendOutput("║ Time: ${totalTime}s | Rate: ${(totalPorts / totalTime).toInt()} ports/sec\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
            scanning = false
        }
    }

    private fun serviceDetect() {
        val ip = etIp.text.toString().trim()
        if (ip.isEmpty()) { appendOutput("[!] Enter target IP\n"); return }

        appendOutput("[*] Service detection on $ip...\n")
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                commonPorts.entries.map { (port, service) ->
                    async(Dispatchers.IO) {
                        try {
                            val socket = Socket()
                            socket.connect(InetSocketAddress(ip, port), 500)
                            val banner = try {
                                val stream = socket.getInputStream()
                                val buf = ByteArray(512)
                                val read = stream.read(buf)
                                if (read > 0) String(buf, 0, read).trim().take(100) else ""
                            } catch (_: Exception) { "" }
                            socket.close()
                            Triple(port, service, banner)
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().filterNotNull()
            }

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Service Detection           ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            for ((port, service, banner) in results.sortedBy { it.first }) {
                appendOutput("║ Port $port ($service)\n")
                if (banner.isNotEmpty()) {
                    appendOutput("║   Banner: ${banner.replace("\n", " ").replace("\r", "")}\n")
                }
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun checkPort(ip: String, port: Int, timeout: Int): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeout)
            socket.close()
            true
        } catch (_: Exception) { false }
    }

    private fun checkPortDetailed(ip: String, port: Int, timeout: Int): PortState {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeout)
            socket.close()
            PortState.OPEN
        } catch (e: java.net.ConnectException) {
            PortState.CLOSED
        } catch (e: java.net.SocketTimeoutException) {
            PortState.FILTERED
        } catch (_: Exception) {
            PortState.CLOSED
        }
    }

    private enum class PortState { OPEN, CLOSED, FILTERED }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanning = false
        scope.cancel()
    }
}
