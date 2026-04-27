package com.hackerlauncher.modules

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.hackerlauncher.utils.ShellExecutor
import kotlinx.coroutines.*

class PacketCaptureFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var capturing = false

    private var tcpCount = 0
    private var udpCount = 0
    private var icmpCount = 0
    private var otherCount = 0
    private var totalBytes = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()

        val rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val tvTitle = TextView(ctx).apply {
            text = "[ PACKET CAPTURE ]"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvTitle)

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

        btnRow1.addView(makeBtn("Start Capture") { startCapture() })
        btnRow1.addView(makeBtn("Stop", 0xFFFF0000.toInt()) { stopCapture() })
        btnRow1.addView(makeBtn("Statistics") { showStats() })
        btnRow2.addView(makeBtn("Connections") { showConnections() })
        btnRow2.addView(makeBtn("ARP Table") { showArpTable() })
        btnRow2.addView(makeBtn("Reset Stats") { resetStats() })

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
        appendOutput("║      PACKET CAPTURE v1.0        ║\n")
        appendOutput("║  Network packet monitoring      ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")
        appendOutput("[i] Full packet capture requires VpnService or root\n")
        appendOutput("[i] This module uses /proc/net/ for statistics\n\n")

        return rootLayout
    }

    private fun startCapture() {
        if (capturing) { appendOutput("[!] Already capturing\n"); return }
        capturing = true
        resetStats()

        appendOutput("[*] Starting packet capture monitoring...\n")

        scope.launch {
            while (capturing && isActive) {
                try {
                    withContext(Dispatchers.IO) {
                        val snmp = ShellExecutor.execute("cat /proc/net/snmp 2>/dev/null")
                        if (snmp.output.isNotEmpty()) {
                            parseSnmp(snmp.output)
                        }
                    }
                    delay(3000)
                } catch (e: Exception) {
                    if (isActive) appendOutput("[E] Capture error: ${e.message}\n")
                }
            }
        }

        scope.launch {
            try {
                val checkTcpdump = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("which tcpdump 2>/dev/null")
                }
                if (checkTcpdump.output.trim().isNotEmpty()) {
                    appendOutput("[+] tcpdump found at ${checkTcpdump.output.trim()}\n")
                    appendOutput("[*] Run: su -c tcpdump -i wlan0 -c 100 for packet capture\n")
                } else {
                    appendOutput("[*] tcpdump not found. Install via Termux: pkg install tcpdump\n")
                }
            } catch (_: Exception) {}
        }
    }

    private fun stopCapture() {
        capturing = false
        appendOutput("[*] Packet capture stopped.\n")
        showStats()
    }

    private fun parseSnmp(data: String) {
        try {
            val lines = data.lines()
            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("Tcp:")) {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size > 10) {
                        val inSegs = parts[11].toIntOrNull() ?: 0
                        val outSegs = parts[12].toIntOrNull() ?: 0
                        tcpCount = inSegs + outSegs
                    }
                }
                if (line.startsWith("Udp:")) {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size > 5) {
                        udpCount = (parts[5].toIntOrNull() ?: 0) + (parts[8].toIntOrNull() ?: 0)
                    }
                }
                if (line.startsWith("Icmp:")) {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size > 3) {
                        icmpCount = (parts[3].toIntOrNull() ?: 0) + (parts[10].toIntOrNull() ?: 0)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun showStats() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║       Packet Statistics         ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            // Show protocol counts
            appendOutput("║ Protocol Breakdown:\n")
            appendOutput("║   TCP:   $tcpCount packets\n")
            appendOutput("║   UDP:   $udpCount packets\n")
            appendOutput("║   ICMP:  $icmpCount packets\n")
            appendOutput("║   Other: $otherCount packets\n")
            appendOutput("║\n")

            try {
                val result = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("cat /proc/net/snmp 2>/dev/null")
                }
                if (result.output.isNotEmpty()) {
                    val lines = result.output.lines()
                    for (line in lines) {
                        if (line.startsWith("Ip:") || line.startsWith("Tcp:") || line.startsWith("Udp:") || line.startsWith("Icmp:")) {
                            appendOutput("║ ${line.trim().take(60)}\n")
                        }
                    }
                }

                val devResult = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("cat /proc/net/dev 2>/dev/null")
                }
                if (devResult.output.isNotEmpty()) {
                    appendOutput("║\n║ Interface Stats:\n")
                    val lines = devResult.output.lines().drop(2)
                    for (line in lines) {
                        val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        if (parts.size >= 10) {
                            val iface = parts[0].removeSuffix(":")
                            if (iface != "lo") {
                                val rxBytes = parts[1].toLongOrNull() ?: 0
                                val txBytes = parts[9].toLongOrNull() ?: 0
                                val rxPackets = parts[2].toLongOrNull() ?: 0
                                val txPackets = parts[10].toLongOrNull() ?: 0
                                appendOutput("║ $iface:\n")
                                appendOutput("║   RX: ${formatBytes(rxBytes)} (${rxPackets} pkts)\n")
                                appendOutput("║   TX: ${formatBytes(txBytes)} (${txPackets} pkts)\n")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                appendOutput("║ [E] ${e.message}\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun showConnections() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║      Active Connections         ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            try {
                val tcpResult = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("cat /proc/net/tcp 2>/dev/null; cat /proc/net/tcp6 2>/dev/null")
                }
                if (tcpResult.output.isNotEmpty()) {
                    appendOutput("║ TCP Connections:\n")
                    val lines = tcpResult.output.lines().drop(1).take(30)
                    for (line in lines) {
                        val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        if (parts.size >= 4) {
                            try {
                                val local = hexToIp(parts[1])
                                val remote = hexToIp(parts[2])
                                val state = tcpState(parts[3].toIntOrNull(16) ?: 0)
                                appendOutput("║   $local -> $remote [$state]\n")
                            } catch (_: Exception) {}
                        }
                    }
                }

                val udpResult = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("cat /proc/net/udp 2>/dev/null; cat /proc/net/udp6 2>/dev/null")
                }
                if (udpResult.output.isNotEmpty()) {
                    appendOutput("║\n║ UDP Connections:\n")
                    val lines = udpResult.output.lines().drop(1).take(15)
                    for (line in lines) {
                        val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        if (parts.size >= 2) {
                            try {
                                val local = hexToIp(parts[1])
                                appendOutput("║   $local [UDP]\n")
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                appendOutput("║ [E] ${e.message}\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun showArpTable() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║         ARP Table               ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("cat /proc/net/arp 2>/dev/null")
                }
                if (result.output.isNotEmpty()) {
                    val lines = result.output.lines()
                    for (line in lines) {
                        appendOutput("║ ${line.trim()}\n")
                    }
                } else {
                    appendOutput("║ No ARP entries found\n")
                }
            } catch (e: Exception) {
                appendOutput("║ [E] ${e.message}\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun resetStats() {
        tcpCount = 0
        udpCount = 0
        icmpCount = 0
        otherCount = 0
        totalBytes = 0
        appendOutput("[*] Statistics reset\n")
    }

    private fun hexToIp(hex: String): String {
        try {
            val parts = hex.split(":")
            if (parts.size == 2) {
                val ipHex = parts[0]
                val port = parts[1].toIntOrNull(16) ?: 0
                if (ipHex.length == 8) {
                    val ip = String.format("%d.%d.%d.%d",
                        ipHex.substring(6, 8).toInt(16),
                        ipHex.substring(4, 6).toInt(16),
                        ipHex.substring(2, 4).toInt(16),
                        ipHex.substring(0, 2).toInt(16))
                    return "$ip:$port"
                }
            }
        } catch (_: Exception) {}
        return hex
    }

    private fun tcpState(state: Int): String = when (state) {
        1 -> "ESTABLISHED"; 2 -> "SYN_SENT"; 3 -> "SYN_RECV"; 4 -> "FIN_WAIT1"
        5 -> "FIN_WAIT2"; 6 -> "TIME_WAIT"; 7 -> "CLOSE"; 8 -> "CLOSE_WAIT"
        9 -> "LAST_ACK"; 10 -> "LISTEN"; 11 -> "CLOSING"; else -> "UNKNOWN($state)"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        capturing = false
        scope.cancel()
    }
}
