package com.hackerlauncher.modules

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hackerlauncher.utils.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

/**
 * FEATURE 4: NetworkScannerFragment
 * Scan local network for connected devices
 * Hacker-themed dark UI with green text (#00FF00) on black background (#0D0D0D)
 */
class NetworkScannerFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val YELLOW = Color.parseColor("#FFFF00")
    private val RED = Color.parseColor("#FF4444")
    private val CYAN = Color.parseColor("#00FFFF")
    private val BLACK = Color.parseColor("#0D0D0D")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")
    private val MED_GRAY = Color.parseColor("#333333")

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var tvStatus: TextView
    private lateinit var scanProgressBar: ProgressBar
    private lateinit var etSubnet: EditText

    private var scanJob: Job? = null

    data class NetworkDevice(
        val ip: String,
        val mac: String,
        val hostname: String,
        val isReachable: Boolean,
        val responseTime: Long
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        val rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
            setPadding(12, 12, 12, 12)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        rootLayout.addView(TextView(ctx).apply {
            text = "[ NETWORK SCANNER ]"
            setTextColor(GREEN)
            textSize = 16f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        })

        // Status
        tvStatus = TextView(ctx).apply {
            text = "[~] Ready to scan"
            setTextColor(YELLOW)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 4)
        }
        rootLayout.addView(tvStatus)

        // Subnet input
        etSubnet = EditText(ctx).apply {
            hint = "Subnet (e.g., 192.168.1)"
            setTextColor(GREEN)
            setHintTextColor(DARK_GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(12, 8, 12, 8)
        }
        rootLayout.addView(etSubnet)

        // Progress bar
        scanProgressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(scanProgressBar)

        // Buttons
        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeBtn("QUICK SCAN") { quickScan() })
        row1.addView(makeBtn("FULL SCAN") { fullScan() })
        row1.addView(makeBtn("STOP") { stopScan() })
        rootLayout.addView(row1)

        val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(makeBtn("MY IP") { showMyIp() })
        row2.addView(makeBtn("ARP TABLE") { showArpTable() })
        row2.addView(makeBtn("CONNECTIONS") { showConnections() })
        rootLayout.addView(row2)

        val row3 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(makeBtn("GATEWAY") { showGateway() })
        row3.addView(makeBtn("DNS INFO") { showDnsInfo() })
        row3.addView(makeBtn("PORT SCAN") { portScan() })
        rootLayout.addView(row3)

        // Output
        scrollView = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }
        tvOutput = TextView(ctx).apply {
            setTextColor(GREEN)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
        }
        scrollView.addView(tvOutput)
        rootLayout.addView(scrollView)

        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║     NETWORK SCANNER v1.0        ║\n")
        appendOutput("║  Scan local network for devices  ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        // Auto-detect subnet
        autoDetectSubnet()

        return rootLayout
    }

    private fun autoDetectSubnet() {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: ""
                        val subnet = ip.substringBeforeLast(".")
                        etSubnet.setText(subnet)
                        appendOutput("[*] Auto-detected subnet: $subnet\n\n")
                        return
                    }
                }
            }
        } catch (e: Exception) {
            appendOutput("[!] Could not auto-detect subnet\n")
            etSubnet.setText("192.168.1")
        }
    }

    private fun quickScan() {
        val subnet = etSubnet.text.toString().trim()
        if (subnet.isEmpty()) {
            Toast.makeText(requireContext(), "Enter subnet", Toast.LENGTH_SHORT).show()
            return
        }

        if (scanJob?.isActive == true) return
        scanProgressBar.progress = 0
        tvStatus.text = "[*] Quick scan: $subnet.0/24 (common IPs)..."

        scanJob = lifecycleScope.launch {
            appendOutput("═══ QUICK SCAN: $subnet.0/24 ═══\n\n")

            // Scan common IPs (gateway, common device IPs)
            val commonIps = listOf(1, 2, 100, 101, 102, 103, 104, 105, 110, 111,
                112, 113, 114, 115, 120, 150, 200, 201, 254, 255)

            var foundCount = 0
            for ((index, host) in commonIps.withIndex()) {
                if (!isActive) break
                val ip = "$subnet.$host"
                val result = withContext(Dispatchers.IO) { pingHost(ip, 1000) }

                if (result.first) {
                    foundCount++
                    appendOutput("  ✅ $ip | ${result.second}ms | ${tryResolveHostname(ip)}\n")
                }

                withContext(Dispatchers.Main) {
                    scanProgressBar.progress = ((index + 1) * 100 / commonIps.size)
                }
            }

            appendOutput("\n[+] Found $foundCount devices on common IPs\n")
            appendOutput("[*] Use FULL SCAN for complete network scan\n\n")
            tvStatus.text = "[+] Quick scan complete. Found: $foundCount devices"
        }
    }

    private fun fullScan() {
        val subnet = etSubnet.text.toString().trim()
        if (subnet.isEmpty()) {
            Toast.makeText(requireContext(), "Enter subnet", Toast.LENGTH_SHORT).show()
            return
        }

        if (scanJob?.isActive == true) return
        scanProgressBar.progress = 0
        tvStatus.text = "[*] Full scan: $subnet.0/24 (254 hosts)..."

        scanJob = lifecycleScope.launch {
            appendOutput("═══ FULL SCAN: $subnet.0/24 ═══\n\n")

            val devices = mutableListOf<NetworkDevice>()

            for (host in 1..254) {
                if (!isActive) break
                val ip = "$subnet.$host"

                val result = withContext(Dispatchers.IO) { pingHost(ip, 500) }

                if (result.first) {
                    val hostname = tryResolveHostname(ip)
                    val mac = tryGetMac(ip)
                    val device = NetworkDevice(
                        ip = ip,
                        mac = mac,
                        hostname = hostname,
                        isReachable = true,
                        responseTime = result.second
                    )
                    devices.add(device)
                    appendOutput("  ✅ $ip | ${result.second}ms | $hostname | $mac\n")
                }

                withContext(Dispatchers.Main) {
                    scanProgressBar.progress = (host * 100 / 254)
                }
            }

            appendOutput("\n═══ SCAN SUMMARY ═══\n")
            appendOutput("Subnet: $subnet.0/24\n")
            appendOutput("Hosts scanned: 254\n")
            appendOutput("Devices found: ${devices.size}\n\n")

            tvStatus.text = "[+] Full scan complete. Found: ${devices.size} devices"
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        tvStatus.text = "[-] Scan stopped"
        appendOutput("[!] Scan stopped by user\n\n")
    }

    private fun pingHost(ip: String, timeout: Int): Pair<Boolean, Long> {
        return try {
            val startTime = System.currentTimeMillis()
            val reachable = InetAddress.getByName(ip).isReachable(timeout)
            val elapsed = System.currentTimeMillis() - startTime
            Pair(reachable, elapsed)
        } catch (e: Exception) {
            Pair(false, 0)
        }
    }

    private fun tryResolveHostname(ip: String): String {
        return try {
            InetAddress.getByName(ip).hostName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun tryGetMac(ip: String): String {
        return try {
            // Try ARP table
            val result = ShellExecutor.execute("ip neigh show $ip 2>/dev/null || arp -n $ip 2>/dev/null")
            val output = result.output.trim()
            val macRegex = Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})")
            val match = macRegex.find(output)
            match?.value ?: "--:--:--:--:--:--"
        } catch (e: Exception) {
            "--:--:--:--:--:--"
        }
    }

    private fun showMyIp() {
        appendOutput("[*] Network interfaces:\n\n")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                appendOutput("Interface: ${intf.name}\n")
                appendOutput("  Display: ${intf.displayName}\n")
                appendOutput("  Up: ${intf.isUp}\n")
                appendOutput("  MTU: ${intf.mtu}\n")

                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    val type = when (addr) {
                        is Inet4Address -> "IPv4"
                        else -> "IPv6"
                    }
                    appendOutput("  $type: ${addr.hostAddress}\n")
                }

                // MAC address
                val mac = intf.hardwareAddress
                if (mac != null) {
                    val macStr = mac.joinToString(":") { "%02X".format(it) }
                    appendOutput("  MAC: $macStr\n")
                }
                appendOutput("\n")
            }
        } catch (e: Exception) {
            appendOutput("[!] Error: ${e.message}\n\n")
        }
    }

    private fun showArpTable() {
        appendOutput("═══ ARP TABLE ═══\n\n")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ShellExecutor.execute("ip neigh show 2>/dev/null || cat /proc/net/arp 2>/dev/null")
            }
            for (line in result.output.lines().filter { it.isNotBlank() }) {
                appendOutput("$line\n")
            }
            appendOutput("\n")
        }
    }

    private fun showConnections() {
        appendOutput("═══ ACTIVE CONNECTIONS ═══\n\n")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ShellExecutor.execute("cat /proc/net/tcp 2>/dev/null; cat /proc/net/tcp6 2>/dev/null")
            }
            val lines = result.output.lines().filter { it.isNotBlank() }.take(30)
            for (line in lines) {
                appendOutput("$line\n")
            }
            appendOutput("\n[*] Use Terminal for detailed connection info\n\n")
        }
    }

    private fun showGateway() {
        appendOutput("═══ GATEWAY INFO ═══\n\n")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ShellExecutor.execute("ip route show default 2>/dev/null || route -n 2>/dev/null | grep '^0.0.0.0'")
            }
            appendOutput(result.output.trim() + "\n\n")

            // Try DHCP info
            try {
                val wifiManager = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                val dhcpInfo = wifiManager.dhcpInfo
                val gateway = intToIp(dhcpInfo.gateway)
                val dns1 = intToIp(dhcpInfo.dns1)
                val dns2 = intToIp(dhcpInfo.dns2)
                val server = intToIp(dhcpInfo.serverAddress)
                val netmask = intToIp(dhcpInfo.netmask)

                appendOutput("DHCP Info:\n")
                appendOutput("  Gateway:  $gateway\n")
                appendOutput("  DNS 1:    $dns1\n")
                appendOutput("  DNS 2:    $dns2\n")
                appendOutput("  Server:   $server\n")
                appendOutput("  Netmask:  $netmask\n\n")
            } catch (e: Exception) {
                appendOutput("DHCP info unavailable: ${e.message}\n\n")
            }
        }
    }

    private fun showDnsInfo() {
        appendOutput("═══ DNS INFO ═══\n\n")
        lifecycleScope.launch {
            val props = listOf("net.dns1", "net.dns2", "net.wlan0.dns1", "net.wlan0.dns2")
            for (prop in props) {
                val result = withContext(Dispatchers.IO) { ShellExecutor.execute("getprop $prop") }
                val value = result.output.trim()
                if (value.isNotEmpty()) {
                    appendOutput("  $prop = $value\n")
                }
            }
            appendOutput("\n")
        }
    }

    private fun portScan() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "IP address"
            setTextColor(GREEN)
            setHintTextColor(DARK_GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(12, 8, 12, 8)
            setText(etSubnet.text.toString().trim() + ".1")
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Port Scan")
            .setView(input)
            .setPositiveButton("Scan") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) performPortScan(ip)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performPortScan(ip: String) {
        appendOutput("═══ PORT SCAN: $ip ═══\n\n")
        lifecycleScope.launch {
            val commonPorts = mapOf(
                21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
                53 to "DNS", 80 to "HTTP", 110 to "POP3", 143 to "IMAP",
                443 to "HTTPS", 445 to "SMB", 993 to "IMAPS", 995 to "POP3S",
                3306 to "MySQL", 3389 to "RDP", 5432 to "PostgreSQL",
                5900 to "VNC", 6379 to "Redis", 8080 to "HTTP-Alt",
                8443 to "HTTPS-Alt", 8888 to "HTTP-Proxy"
            )

            var openPorts = 0
            for ((port, service) in commonPorts) {
                val isOpen = withContext(Dispatchers.IO) {
                    try {
                        val socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress(ip, port), 1000)
                        socket.close()
                        true
                    } catch (_: Exception) {
                        false
                    }
                }

                if (isOpen) {
                    openPorts++
                    appendOutput("  ✅ Port $port ($service) - OPEN\n")
                }
            }

            appendOutput("\n[+] $openPorts open ports found on $ip\n\n")
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

    private fun makeBtn(label: String, listener: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label
            setTextColor(GREEN)
            setBackgroundColor(DARK_GRAY)
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setPadding(2, 2, 2, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(1, 1, 1, 1)
            }
            setOnClickListener { listener() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanJob?.cancel()
    }
}
