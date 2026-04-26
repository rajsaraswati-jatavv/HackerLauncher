package com.hackerlauncher.modules

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiInfo
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.hackerlauncher.R
import com.hackerlauncher.utils.ShellExecutor
import com.hackerlauncher.utils.Logger
import kotlinx.coroutines.*

class NetworkModuleFragment : Fragment() {

    private lateinit var tvNetOutput: TextView
    private lateinit var scrollView: ScrollView
    private val logger = Logger()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_network, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvNetOutput = view.findViewById(R.id.tvNetOutput)
        scrollView = view.findViewById(R.id.scrollViewNetwork)

        val btnWifiInfo = view.findViewById<Button>(R.id.btnWifiInfo)
        val btnScanWifi = view.findViewById<Button>(R.id.btnScanWifi)
        val btnArpTable = view.findViewById<Button>(R.id.btnArpTable)
        val btnDnsLookup = view.findViewById<Button>(R.id.btnDnsLookup)
        val btnPing = view.findViewById<Button>(R.id.btnPing)
        val btnPortScan = view.findViewById<Button>(R.id.btnPortScan)
        val btnIfconfig = view.findViewById<Button>(R.id.btnIfconfig)
        val btnNetstat = view.findViewById<Button>(R.id.btnNetstat)
        val etHost = view.findViewById<EditText>(R.id.etHost)
        val etPort = view.findViewById<EditText>(R.id.etPort)

        btnWifiInfo.setOnClickListener { getWifiInfo() }
        btnScanWifi.setOnClickListener { scanWifi() }
        btnArpTable.setOnClickListener { runCommand("cat /proc/net/arp", "ARP Table") }
        btnIfconfig.setOnClickListener { runCommand("ifconfig 2>/dev/null || ip addr", "Network Interfaces") }
        btnNetstat.setOnClickListener { runCommand("netstat -tlnp 2>/dev/null || ss -tlnp", "Network Connections") }

        btnDnsLookup.setOnClickListener {
            val host = etHost.text.toString().trim()
            if (host.isNotEmpty()) runCommand("nslookup $host", "DNS Lookup: $host")
            else appendOutput("[!] Enter a host first\n")
        }

        btnPing.setOnClickListener {
            val host = etHost.text.toString().trim()
            if (host.isNotEmpty()) runCommand("ping -c 4 $host", "Ping: $host")
            else appendOutput("[!] Enter a host first\n")
        }

        btnPortScan.setOnClickListener {
            val host = etHost.text.toString().trim()
            val portStr = etPort.text.toString().trim()
            if (host.isNotEmpty() && portStr.isNotEmpty()) {
                scope.launch {
                    appendOutput("[*] Scanning $host port $portStr...\n")
                    val result = withContext(Dispatchers.IO) {
                        scanPort(host, portStr)
                    }
                    appendOutput(result + "\n")
                }
            } else {
                appendOutput("[!] Enter host and port\n")
            }
        }
    }

    private fun getWifiInfo() {
        try {
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info: WifiInfo = wifiManager.connectionInfo
            val ip = Formatter.formatIpAddress(info.ipAddress)
            val ssid = info.ssid?.removeSurrounding("\"") ?: "Unknown"
            val bssid = info.bssid ?: "Unknown"
            val rssi = info.rssi
            val speed = info.linkSpeed
            val level = WifiManager.calculateSignalLevel(rssi, 5)

            appendOutput("""
                ═══ WiFi Information ═══
                SSID:     $ssid
                BSSID:    $bssid
                IP:       $ip
                Signal:   ${rssi}dBm (Level: $level/4)
                Speed:    ${speed}Mbps
                MAC:      ${info.macAddress ?: "Unavailable"}
                ═══════════════════════
                
            """.trimIndent() + "\n")
        } catch (e: Exception) {
            appendOutput("[E] WiFi info error: ${e.message}\n")
        }
    }

    private fun scanWifi() {
        try {
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val results = wifiManager.scanResults
            appendOutput("═══ WiFi Scan Results (${results.size} found) ═══\n")
            for ((idx, result) in results.withIndex()) {
                val level = WifiManager.calculateSignalLevel(result.level, 5)
                val security = when {
                    result.capabilities.contains("WPA3") -> "WPA3"
                    result.capabilities.contains("WPA2") -> "WPA2"
                    result.capabilities.contains("WPA") -> "WPA"
                    result.capabilities.contains("WEP") -> "WEP"
                    else -> "Open"
                }
                appendOutput("  ${idx + 1}. ${result.SSID}\n")
                appendOutput("     BSSID: ${result.BSSID} | Signal: ${result.level}dBm ($level/4)\n")
                appendOutput("     Security: $security | Freq: ${result.frequency}MHz\n\n")
            }
        } catch (e: SecurityException) {
            appendOutput("[E] Location permission required for WiFi scan\n")
        } catch (e: Exception) {
            appendOutput("[E] Scan failed: ${e.message}\n")
        }
    }

    private fun scanPort(host: String, portStr: String): String {
        val ports = try {
            if (portStr.contains("-")) {
                val range = portStr.split("-")
                (range[0].toInt()..range[1].toInt()).toList()
            } else if (portStr.contains(",")) {
                portStr.split(",").map { it.trim().toInt() }
            } else {
                listOf(portStr.toInt())
            }
        } catch (e: Exception) {
            return "[E] Invalid port format. Use: 80, 80-100, or 80,443,8080"
        }

        val sb = StringBuilder("═══ Port Scan: $host ═══\n")
        for (port in ports.take(1024)) { // Limit to 1024 ports max
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(host, port), 1000)
                socket.close()
                val service = getWellKnownService(port)
                sb.append("  [OPEN] Port $port ($service)\n")
            } catch (_: Exception) {
                // Port closed/filtered, skip silently for brevity
            }
        }
        sb.append("══════════════════════\n")
        return sb.toString()
    }

    private fun getWellKnownService(port: Int): String = when (port) {
        21 -> "FTP"; 22 -> "SSH"; 23 -> "Telnet"; 25 -> "SMTP"
        53 -> "DNS"; 80 -> "HTTP"; 110 -> "POP3"; 143 -> "IMAP"
        443 -> "HTTPS"; 445 -> "SMB"; 993 -> "IMAPS"; 995 -> "POP3S"
        3306 -> "MySQL"; 5432 -> "PostgreSQL"; 6379 -> "Redis"
        8080 -> "HTTP-Alt"; 8443 -> "HTTPS-Alt"; 27017 -> "MongoDB"
        else -> "Unknown"
    }

    private fun runCommand(cmd: String, label: String) {
        scope.launch {
            appendOutput("[*] $label...\n")
            val result = withContext(Dispatchers.IO) { ShellExecutor.execute(cmd) }
            if (result.output.isNotEmpty()) appendOutput(result.output + "\n")
            if (result.error.isNotEmpty()) appendOutput("[E] ${result.error}\n")
        }
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvNetOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
}
