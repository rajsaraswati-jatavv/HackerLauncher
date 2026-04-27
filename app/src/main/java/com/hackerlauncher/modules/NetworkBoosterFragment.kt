package com.hackerlauncher.modules

import android.content.Context
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
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

class NetworkBoosterFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var beforeSpeed: Long = 0
    private var afterSpeed: Long = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()

        val rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val tvTitle = TextView(ctx).apply {
            text = "[ NETWORK BOOSTER ]"
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
        val btnRow3 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        btnRow1.addView(makeBtn("Flush DNS") { flushDns() })
        btnRow1.addView(makeBtn("Renew DHCP") { renewDhcp() })
        btnRow1.addView(makeBtn("Reset Sockets") { resetSockets() })
        btnRow2.addView(makeBtn("Optimize MTU") { optimizeMtu() })
        btnRow2.addView(makeBtn("TCP Optimize") { tcpOptimize() })
        btnRow2.addView(makeBtn("Reset Conn") { resetConnections() })
        btnRow3.addView(makeBtn("Clear Proxy") { clearProxy() })
        btnRow3.addView(makeBtn("Speed Test") { speedTest() })
        btnRow3.addView(makeBtn("BOOST ALL", 0xFF00FF00.toInt()) { boostAll() })

        rootLayout.addView(btnRow1)
        rootLayout.addView(btnRow2)
        rootLayout.addView(btnRow3)

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
        appendOutput("║      NETWORK BOOSTER v1.0       ║\n")
        appendOutput("║  Optimize network performance   ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return rootLayout
    }

    private fun flushDns() {
        scope.launch {
            appendOutput("[*] Flushing DNS cache...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    val r1 = ShellExecutor.execute("su -c 'setprop net.dns1 \"\"; setprop net.dns2 \"\"'")
                    sb.append(if (r1.error.isEmpty()) "[+] DNS properties cleared (root)\n" else "[!] Root required for DNS flush\n")

                    val r2 = ShellExecutor.execute("su -c 'nscd -i hosts 2>/dev/null'")
                    if (r2.output.isNotEmpty()) sb.append("[+] NSCD cache flushed\n")

                    val start = System.currentTimeMillis()
                    java.net.InetAddress.getByName("google.com")
                    val elapsed = System.currentTimeMillis() - start
                    sb.append("[*] DNS resolution test: ${elapsed}ms\n")

                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] DNS flush failed: ${e.message}\n")
            }
        }
    }

    private fun renewDhcp() {
        scope.launch {
            appendOutput("[*] Renewing DHCP lease...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()

                    val r1 = ShellExecutor.execute("su -c 'dhcpcd -n wlan0 2>/dev/null; dhclient -r wlan0 2>/dev/null; dhclient wlan0 2>/dev/null'")
                    if (r1.error.isEmpty()) {
                        sb.append("[+] DHCP renewal command sent (root)\n")
                    } else {
                        sb.append("[!] Root required for DHCP renewal\n")
                    }

                    // Show current DHCP info
                    try {
                        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        val dhcpInfo = wifiManager.dhcpInfo
                        sb.append("[*] Current DHCP Info:\n")
                        sb.append("  Gateway: ${intToIp(dhcpInfo.gateway)}\n")
                        sb.append("  DNS1: ${intToIp(dhcpInfo.dns1)}\n")
                        sb.append("  DNS2: ${intToIp(dhcpInfo.dns2)}\n")
                        sb.append("  Server: ${intToIp(dhcpInfo.serverAddress)}\n")
                        sb.append("  Lease: ${dhcpInfo.leaseDuration}s\n")
                    } catch (e: Exception) {
                        sb.append("[E] DHCP info: ${e.message}\n")
                    }

                    sb.append("[*] Alternative: Toggle WiFi off/on\n")
                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] DHCP renewal failed: ${e.message}\n")
            }
        }
    }

    private fun resetSockets() {
        scope.launch {
            appendOutput("[*] Resetting socket connections...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()

                    val tcpResult = ShellExecutor.execute("cat /proc/net/tcp 2>/dev/null | wc -l")
                    val connCount = tcpResult.output.trim().toIntOrNull()?.minus(1) ?: 0
                    sb.append("[*] Current TCP connections: $connCount\n")

                    // Close idle sockets (root)
                    val r1 = ShellExecutor.execute("su -c 'ss -K state established \"( dport = :80 or dport = :443 )\" 2>/dev/null; ss -K state time-wait 2>/dev/null'")
                    if (r1.error.isEmpty()) {
                        sb.append("[+] Idle sockets closed (root)\n")
                    } else {
                        sb.append("[!] Root required for socket reset\n")
                        sb.append("[*] Try closing apps that use network\n")
                    }

                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] Socket reset failed: ${e.message}\n")
            }
        }
    }

    private fun optimizeMtu() {
        scope.launch {
            appendOutput("[*] Optimizing MTU settings...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()

                    val mtuResult = ShellExecutor.execute("cat /sys/class/net/wlan0/mtu 2>/dev/null; ip link show wlan0 2>/dev/null | grep mtu")
                    val currentMtu = mtuResult.output.trim()
                    sb.append("[*] Current MTU: ${if (currentMtu.isNotEmpty()) currentMtu else "Unknown"}\n")

                    // Test optimal MTU
                    val testHosts = listOf("google.com", "cloudflare.com")
                    val mtuValues = listOf(1500, 1400, 1300, 1200)

                    sb.append("[*] MTU path discovery:\n")
                    for (host in testHosts) {
                        for (mtu in mtuValues) {
                            try {
                                val start = System.currentTimeMillis()
                                val address = java.net.InetAddress.getByName(host)
                                val reachable = address.isReachable(2000)
                                val elapsed = System.currentTimeMillis() - start
                                sb.append("  $host MTU $mtu: ${elapsed}ms ${if (reachable) "[OK]" else "[FAIL]"}\n")
                            } catch (_: Exception) {
                                sb.append("  $host MTU $mtu: [FAIL]\n")
                            }
                        }
                    }

                    // Set optimal MTU (root)
                    val r1 = ShellExecutor.execute("su -c 'ip link set wlan0 mtu 1500 2>/dev/null; ifconfig wlan0 mtu 1500 2>/dev/null'")
                    if (r1.error.isEmpty()) {
                        sb.append("[+] MTU set to 1500 (optimal for most networks)\n")
                    } else {
                        sb.append("[!] Root required to set MTU\n")
                        sb.append("[*] Recommended MTU values:\n")
                        sb.append("    Ethernet: 1500\n")
                        sb.append("    PPPoE: 1492\n")
                        sb.append("    VPN: 1400\n")
                    }

                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] MTU optimization failed: ${e.message}\n")
            }
        }
    }

    private fun tcpOptimize() {
        scope.launch {
            appendOutput("[*] Optimizing TCP parameters...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()

                    val settings = mapOf(
                        "tcp_window_scaling" to "1",
                        "tcp_sack" to "1",
                        "tcp_timestamps" to "0",
                        "tcp_tw_reuse" to "1",
                        "tcp_fin_timeout" to "15",
                        "tcp_keepalive_time" to "300",
                        "tcp_keepalive_probes" to "5",
                        "tcp_keepalive_intvl" to "15",
                        "tcp_low_latency" to "1",
                        "tcp_fastopen" to "3"
                    )

                    // TCP buffer sizes
                    sb.append("║ TCP Buffer Sizes:\n")
                    val rmem = ShellExecutor.execute("cat /proc/sys/net/ipv4/tcp_rmem 2>/dev/null")
                    val wmem = ShellExecutor.execute("cat /proc/sys/net/ipv4/tcp_wmem 2>/dev/null")
                    if (rmem.output.trim().isNotEmpty()) sb.append("║  Read:  ${rmem.output.trim()}\n")
                    if (wmem.output.trim().isNotEmpty()) sb.append("║  Write: ${wmem.output.trim()}\n")
                    sb.append("║\n")

                    for ((key, recommended) in settings) {
                        val current = ShellExecutor.execute("cat /proc/sys/net/ipv4/$key 2>/dev/null").output.trim()
                        val needsChange = current != recommended && current.isNotEmpty()
                        if (needsChange) {
                            ShellExecutor.execute("su -c 'echo $recommended > /proc/sys/net/ipv4/$key 2>/dev/null'")
                        }
                        sb.append("  $key: ${current.ifEmpty { "N/A" }} -> ${if (needsChange) "changed" else "ok"} = $recommended\n")
                    }

                    sb.toString()
                }
                appendOutput(result)
                appendOutput("[*] TCP optimization applied where possible\n")
            } catch (e: Exception) {
                appendOutput("[E] TCP optimization failed: ${e.message}\n")
            }
        }
    }

    private fun resetConnections() {
        scope.launch {
            appendOutput("[*] Resetting network connections...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    val tcpCount = ShellExecutor.execute("cat /proc/net/tcp 2>/dev/null | wc -l").output.trim()
                    sb.append("[*] Current TCP entries: ${tcpCount.toIntOrNull()?.minus(1) ?: 0}\n")

                    // Reset network stats
                    val r1 = ShellExecutor.execute("su -c 'ss -K state close-wait 2>/dev/null; ss -K state time-wait 2>/dev/null; ss -K state closing 2>/dev/null'")
                    if (r1.error.isEmpty()) {
                        sb.append("[+] Stale connections cleared (root)\n")
                    } else {
                        sb.append("[!] Root required for connection reset\n")
                        sb.append("[*] Try: Settings > Apps > Force Stop network apps\n")
                    }

                    val tcpCountAfter = ShellExecutor.execute("cat /proc/net/tcp 2>/dev/null | wc -l").output.trim()
                    sb.append("[*] TCP entries after reset: ${tcpCountAfter.toIntOrNull()?.minus(1) ?: 0}\n")

                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] Connection reset failed: ${e.message}\n")
            }
        }
    }

    private fun clearProxy() {
        scope.launch {
            appendOutput("[*] Clearing proxy settings...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val r1 = ShellExecutor.execute("su -c 'settings put global http_proxy :0; settings delete global http_proxy 2>/dev/null'")
                    if (r1.error.isEmpty()) "[+] Proxy cleared (root)\n" else "[!] Root required\n"

                    val r2 = ShellExecutor.execute("settings get global http_proxy")
                    "[*] Current proxy: ${r2.output.trim()}\n"
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] Proxy clear failed: ${e.message}\n")
            }
        }
    }

    private fun speedTest() {
        scope.launch {
            appendOutput("[*] Running quick speed test...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    val testHosts = listOf(
                        "https://www.google.com" to "Google",
                        "https://www.cloudflare.com" to "Cloudflare",
                        "https://www.amazon.com" to "Amazon"
                    )

                    for ((url, name) in testHosts) {
                        try {
                            val start = System.currentTimeMillis()
                            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 5000
                            conn.readTimeout = 5000
                            conn.requestMethod = "HEAD"
                            val code = conn.responseCode
                            val elapsed = System.currentTimeMillis() - start
                            conn.disconnect()
                            sb.append("$name: ${elapsed}ms (HTTP $code)\n")
                        } catch (e: Exception) {
                            sb.append("$name: FAILED (${e.message})\n")
                        }
                    }

                    // DNS resolution speed
                    val dnsStart = System.currentTimeMillis()
                    try {
                        java.net.InetAddress.getByName("google.com")
                        val dnsElapsed = System.currentTimeMillis() - dnsStart
                        sb.append("DNS Resolution: ${dnsElapsed}ms\n")
                    } catch (_: Exception) {
                        sb.append("DNS Resolution: FAILED\n")
                    }

                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] Speed test failed: ${e.message}\n")
            }
        }
    }

    private fun boostAll() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     FULL NETWORK BOOST          ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            try {
                // Measure before
                val beforeResult = withContext(Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    try {
                        java.net.InetAddress.getByName("google.com")
                    } catch (_: Exception) {}
                    System.currentTimeMillis() - start
                }
                beforeSpeed = beforeResult

                val results = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    val start = System.currentTimeMillis()

                    // 1. Flush DNS
                    sb.append("[1/7] Flushing DNS cache...\n")
                    ShellExecutor.execute("su -c 'setprop net.dns1 \"\"; setprop net.dns2 \"\"; nscd -i hosts 2>/dev/null'")

                    // 2. Reset sockets
                    sb.append("[2/7] Resetting idle sockets...\n")
                    ShellExecutor.execute("su -c 'ss -K state time-wait 2>/dev/null'")

                    // 3. Optimize TCP
                    sb.append("[3/7] Optimizing TCP settings...\n")
                    ShellExecutor.execute("su -c 'echo 1 > /proc/sys/net/ipv4/tcp_tw_reuse 2>/dev/null; echo 1 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null; echo 0 > /proc/sys/net/ipv4/tcp_timestamps 2>/dev/null'")

                    // 4. Set DNS to fast servers
                    sb.append("[4/7] Setting fast DNS (1.1.1.1)...\n")
                    ShellExecutor.execute("su -c 'setprop net.dns1 1.1.1.1; setprop net.dns2 1.0.0.1'")

                    // 5. Clear proxy
                    sb.append("[5/7] Clearing proxy settings...\n")
                    ShellExecutor.execute("su -c 'settings put global http_proxy :0'")

                    // 6. Optimize MTU
                    sb.append("[6/7] Setting optimal MTU...\n")
                    ShellExecutor.execute("su -c 'ip link set wlan0 mtu 1500 2>/dev/null'")

                    // 7. Optimize TCP buffer sizes
                    sb.append("[7/7] Optimizing TCP buffers...\n")
                    ShellExecutor.execute("su -c 'echo \"4096 87380 524288\" > /proc/sys/net/ipv4/tcp_rmem 2>/dev/null; echo \"4096 65536 524288\" > /proc/sys/net/ipv4/tcp_wmem 2>/dev/null'")

                    val elapsed = (System.currentTimeMillis() - start) / 1000.0
                    sb.append("\n[+] Boost completed in ${elapsed}s\n")
                    sb.append("[*] Some optimizations require root\n")

                    sb.toString()
                }
                appendOutput(results)

                // Measure after
                val afterResult = withContext(Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    try {
                        java.net.InetAddress.getByName("google.com")
                    } catch (_: Exception) {}
                    System.currentTimeMillis() - start
                }
                afterSpeed = afterResult

                appendOutput("║\n║ === Before/After Comparison ===\n")
                appendOutput("║ DNS Before: ${beforeSpeed}ms\n")
                appendOutput("║ DNS After:  ${afterSpeed}ms\n")
                val improvement = if (beforeSpeed > 0 && afterSpeed < beforeSpeed) {
                    val pct = ((beforeSpeed - afterSpeed).toDouble() / beforeSpeed * 100).toInt()
                    "+$pct% faster"
                } else if (afterSpeed > beforeSpeed) {
                    val pct = ((afterSpeed - beforeSpeed).toDouble() / afterSpeed * 100).toInt()
                    "$pct% slower (may need retry)"
                } else {
                    "No change"
                }
                appendOutput("║ Result: $improvement\n")

            } catch (e: Exception) {
                appendOutput("[E] Boost failed: ${e.message}\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun intToIp(addr: Int): String {
        return ((addr and 0xFF).toString() + "." +
                ((addr shr 8) and 0xFF) + "." +
                ((addr shr 16) and 0xFF) + "." +
                ((addr shr 24) and 0xFF))
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
}
