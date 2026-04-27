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

class ConnectionLoggerFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var logging = false
    private val connectionLog = mutableListOf<ConnectionEntry>()

    data class ConnectionEntry(
        val protocol: String,
        val localAddr: String,
        val remoteAddr: String,
        val state: String,
        val timestamp: Long = System.currentTimeMillis()
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
            text = "[ CONNECTION LOGGER ]"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvTitle)

        fun makeBtn(label: String, listener: () -> Unit) = Button(ctx).apply {
            text = label
            setTextColor(0xFF00FF00.toInt())
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

        btnRow1.addView(makeBtn("Log All") { logAllConnections() })
        btnRow1.addView(makeBtn("TCP Only") { logTcp() })
        btnRow1.addView(makeBtn("UDP Only") { logUdp() })
        btnRow2.addView(makeBtn("Monitor") { toggleMonitor() })
        btnRow2.addView(makeBtn("By App") { connectionsByApp() })
        btnRow2.addView(makeBtn("Clear") { connectionLog.clear(); tvOutput.text = "" })

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
        appendOutput("║    CONNECTION LOGGER v1.0       ║\n")
        appendOutput("║  Monitor active connections     ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return rootLayout
    }

    private fun logAllConnections() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║      All Active Connections     ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            try {
                val entries = withContext(Dispatchers.IO) { parseConnections() }

                var tcpCount = 0
                var udpCount = 0
                var established = 0
                var listening = 0

                for (entry in entries) {
                    appendOutput("║ [${entry.protocol}] ${entry.localAddr} -> ${entry.remoteAddr} [${entry.state}]\n")
                    when (entry.protocol) {
                        "TCP" -> tcpCount++
                        "UDP" -> udpCount++
                    }
                    when (entry.state) {
                        "ESTABLISHED" -> established++
                        "LISTEN" -> listening++
                    }
                    connectionLog.add(entry)
                }

                appendOutput("╠══════════════════════════════════╣\n")
                appendOutput("║ Total: ${entries.size} | TCP: $tcpCount | UDP: $udpCount\n")
                appendOutput("║ Established: $established | Listening: $listening\n")
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("║ [E] ${e.message}\n")
                appendOutput("╚══════════════════════════════════╝\n\n")
            }
        }
    }

    private fun logTcp() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║       TCP Connections           ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            try {
                val entries = withContext(Dispatchers.IO) {
                    parseConnections().filter { it.protocol == "TCP" }
                }

                val byState = entries.groupBy { it.state }
                for ((state, conns) in byState.toSortedMap()) {
                    appendOutput("║\n║ --- $state (${conns.size}) ---\n")
                    for (conn in conns.take(20)) {
                        appendOutput("║   ${conn.localAddr} -> ${conn.remoteAddr}\n")
                    }
                    if (conns.size > 20) appendOutput("║   ... and ${conns.size - 20} more\n")
                }

                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun logUdp() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║       UDP Connections           ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            try {
                val entries = withContext(Dispatchers.IO) {
                    parseConnections().filter { it.protocol == "UDP" }
                }

                for (entry in entries.take(30)) {
                    appendOutput("║ ${entry.localAddr} [${entry.state}]\n")
                }
                if (entries.size > 30) appendOutput("║ ... and ${entries.size - 30} more\n")
                appendOutput("║\n║ Total UDP: ${entries.size}\n")
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun toggleMonitor() {
        logging = !logging
        if (logging) {
            appendOutput("[*] Connection monitoring started (checking every 5s)...\n")
            scope.launch {
                var prevCount = 0
                while (logging && isActive) {
                    try {
                        val entries = withContext(Dispatchers.IO) { parseConnections() }
                        val currentCount = entries.size
                        if (currentCount != prevCount) {
                            val diff = currentCount - prevCount
                            val time = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
                            appendOutput("[$time] Connections: $currentCount (${if (diff > 0) "+" else ""}$diff)\n")

                            val established = entries.count { it.state == "ESTABLISHED" }
                            val listening = entries.count { it.state == "LISTEN" }
                            appendOutput("  Established: $established | Listening: $listening\n")

                            if (prevCount > 0 && diff > 0) {
                                val newConns = entries.filter { !connectionLog.any { old -> old.localAddr == it.localAddr && old.remoteAddr == it.remoteAddr } }
                                for (nc in newConns.take(5)) {
                                    appendOutput("  [NEW] [${nc.protocol}] ${nc.localAddr} -> ${nc.remoteAddr} [${nc.state}]\n")
                                }
                            }

                            connectionLog.clear()
                            connectionLog.addAll(entries)
                            prevCount = currentCount
                        }
                    } catch (e: Exception) {
                        appendOutput("[E] Monitor: ${e.message}\n")
                    }
                    delay(5000)
                }
            }
        } else {
            appendOutput("[*] Monitoring stopped.\n")
        }
    }

    private fun connectionsByApp() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Connections by App          ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            try {
                val result = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("su -c 'cat /proc/net/tcp /proc/net/udp 2>/dev/null; for pid in /proc/[0-9]*/fd/*; do readlink \$pid 2>/dev/null; done | sort | uniq -c | sort -rn | head -20'")
                }

                if (result.output.isNotEmpty()) {
                    val lines = result.output.lines().filter { it.isNotBlank() }.take(40)
                    for (line in lines) {
                        appendOutput("║ ${line.trim().take(50)}\n")
                    }
                } else {
                    appendOutput("║ [*] Root required for app-level mapping\n")
                    appendOutput("║ [*] Showing all connections instead:\n\n")
                    val entries = withContext(Dispatchers.IO) { parseConnections() }
                    for (entry in entries.take(20)) {
                        appendOutput("║ [${entry.protocol}] ${entry.localAddr} -> ${entry.remoteAddr}\n")
                    }
                }
            } catch (e: Exception) {
                appendOutput("║ [E] ${e.message}\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun parseConnections(): List<ConnectionEntry> {
        val entries = mutableListOf<ConnectionEntry>()

        // Parse TCP from /proc/net/tcp and /proc/net/tcp6
        try {
            val tcpResult = ShellExecutor.execute("cat /proc/net/tcp 2>/dev/null; cat /proc/net/tcp6 2>/dev/null")
            for (line in tcpResult.output.lines().drop(1)) {
                val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (parts.size >= 4) {
                    try {
                        val local = hexToIp(parts[1])
                        val remote = hexToIp(parts[2])
                        val state = tcpState(parts[3].toIntOrNull(16) ?: 0)
                        entries.add(ConnectionEntry("TCP", local, remote, state))
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        // Parse UDP from /proc/net/udp and /proc/net/udp6
        try {
            val udpResult = ShellExecutor.execute("cat /proc/net/udp 2>/dev/null; cat /proc/net/udp6 2>/dev/null")
            for (line in udpResult.output.lines().drop(1)) {
                val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (parts.size >= 3) {
                    try {
                        val local = hexToIp(parts[1])
                        val state = if (parts.size > 3) parts[3].toIntOrNull(16)?.let { if (it == 7) "UNCONNECTED" else "ACTIVE" } ?: "ACTIVE" else "ACTIVE"
                        entries.add(ConnectionEntry("UDP", local, "*:*", state))
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        return entries
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
                return "[$ipHex]:$port"
            }
        } catch (_: Exception) {}
        return hex
    }

    private fun tcpState(state: Int): String = when (state) {
        1 -> "ESTABLISHED"; 2 -> "SYN_SENT"; 3 -> "SYN_RECV"; 4 -> "FIN_WAIT1"
        5 -> "FIN_WAIT2"; 6 -> "TIME_WAIT"; 7 -> "CLOSE"; 8 -> "CLOSE_WAIT"
        9 -> "LAST_ACK"; 10 -> "LISTEN"; 11 -> "CLOSING"; else -> "UNKNOWN($state)"
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        logging = false
        scope.cancel()
    }
}
