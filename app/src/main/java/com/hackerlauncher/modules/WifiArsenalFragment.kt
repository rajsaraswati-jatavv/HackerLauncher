package com.hackerlauncher.modules

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiConfiguration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.hackerlauncher.R
import com.hackerlauncher.utils.Logger
import com.hackerlauncher.utils.ShellExecutor
import kotlinx.coroutines.*

class WifiArsenalFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private val logger = Logger()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_network, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvOutput = view.findViewById(R.id.tvNetOutput)
        scrollView = view.findViewById(R.id.scrollViewNetwork)

        val btnWifiInfo = view.findViewById<Button>(R.id.btnWifiInfo)
        val btnScanWifi = view.findViewById<Button>(R.id.btnScanWifi)
        val btnArpTable = view.findViewById<Button>(R.id.btnArpTable)
        val btnDnsLookup = view.findViewById<Button>(R.id.btnDnsLookup)
        val btnPing = view.findViewById<Button>(R.id.btnPing)
        val btnPortScan = view.findViewById<Button>(R.id.btnPortScan)
        val btnIfconfig = view.findViewById<Button>(R.id.btnIfconfig)
        val btnNetstat = view.findViewById<Button>(R.id.btnNetstat)

        // Repurpose buttons for WiFi Arsenal
        btnWifiInfo.text = "WiFi Info"
        btnScanWifi.text = "Deep Scan"
        btnArpTable.text = "Connected Devs"
        btnDnsLookup.text = "WPS Check"
        btnPing.text = "Signal Mon"
        btnPortScan.text = "Deauth Det"
        btnIfconfig.text = "WiFi Pwr"
        btnNetstat.text = "Handshake"

        btnWifiInfo.setOnClickListener { getWifiInfo() }
        btnScanWifi.setOnClickListener { deepScanWifi() }
        btnArpTable.setOnClickListener { findConnectedDevices() }
        btnDnsLookup.setOnClickListener { checkWps() }
        btnPing.setOnClickListener { monitorSignal() }
        btnPortScan.setOnClickListener { detectDeauth() }
        btnIfconfig.setOnClickListener { checkWifiPower() }
        btnNetstat.setOnClickListener { checkHandshake() }
    }

    @Suppress("DEPRECATION")
    private fun getWifiInfo() {
        try {
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info: WifiInfo = wifiManager.connectionInfo

            val ipInt = info.ipAddress
            val ip = if (ipInt != 0) {
                String.format("%d.%d.%d.%d", ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
            } else "N/A"

            val ssid = info.ssid?.removeSurrounding("\"") ?: "Unknown"
            val bssid = info.bssid ?: "Unknown"
            val rssi = info.rssi
            val speed = info.linkSpeed
            val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) info.frequency else 0
            val band = when {
                freq in 2400..2500 -> "2.4 GHz"
                freq in 4900..5900 -> "5 GHz"
                freq in 5900..7125 -> "6 GHz"
                else -> "Unknown"
            }
            val level = WifiManager.calculateSignalLevel(rssi, 5)
            val channel = if (freq > 0) {
                if (freq in 2400..2500) (freq - 2407) / 5
                else (freq - 5000) / 5
            } else 0

            val security = getSecurityType(wifiManager)

            appendOutput("""
                ╔══════════════════════════════════╗
                ║     WiFi ARSENAL - Info Mode     ║
                ╠══════════════════════════════════╣
                ║ SSID:      $ssid
                ║ BSSID:     $bssid
                ║ IP:        $ip
                ║ Signal:    ${rssi}dBm (Level: $level/4)
                ║ Speed:     ${speed}Mbps
                ║ Band:      $band
                ║ Channel:   $channel
                ║ Security:  $security
                ║ MAC:       ${info.macAddress ?: "Unavailable"}
                ║ Network ID: ${info.networkId}
                ║ Hidden:    ${info.hiddenSSID}
                ╚══════════════════════════════════╝

            """.trimIndent() + "\n")
        } catch (e: Exception) {
            appendOutput("[E] WiFi info error: ${e.message}\n")
        }
    }

    @Suppress("DEPRECATION")
    private fun getSecurityType(wifiManager: WifiManager): String {
        return try {
            val configs = wifiManager.configuredNetworks
            val current = configs?.find { it.status == 0 }
            when {
                current == null -> "Unknown"
                else -> {
                    val caps = current.allowedKeyManagement
                    when {
                        caps?.contains(4) == true -> "WPA3"  // KeyMgmt.SAE = 4
                        caps?.contains(4) == true -> "WPA2-PSK"  // KeyMgmt.WPA2_PSK = 4
                        caps?.contains(1) == true -> "WPA-PSK"   // KeyMgmt.WPA_PSK = 1
                        current.wepKeys?.any { it != null } == true -> "WEP"
                        else -> "Open"
                    }
                }
            }
        } catch (e: Exception) {
            "Detection failed"
        }
    }

    @Suppress("DEPRECATION")
    private fun deepScanWifi() {
        try {
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val scanStarted = wifiManager.startScan()
            if (!scanStarted) {
                appendOutput("[!] WiFi scan throttled. Showing cached.\n")
            }
            val results = wifiManager.scanResults

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   WiFi ARSENAL - Deep Scan      ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("║ Found: ${results.size} networks\n\n")

            // Sort by signal strength
            val sorted = results.sortedByDescending { it.level }

            for ((idx, result) in sorted.withIndex()) {
                val level = WifiManager.calculateSignalLevel(result.level, 5)
                val security = when {
                    result.capabilities.contains("WPA3") -> "WPA3"
                    result.capabilities.contains("WPA2") -> "WPA2"
                    result.capabilities.contains("WPA") -> "WPA"
                    result.capabilities.contains("WEP") -> "WEP"
                    else -> "OPEN"
                }
                val band = when {
                    result.frequency in 2400..2500 -> "2.4G"
                    result.frequency in 4900..5900 -> "5G"
                    else -> "${result.frequency}MHz"
                }
                val hasWPS = result.capabilities.contains("WPS")
                val channel = if (result.frequency in 2400..2500) (result.frequency - 2407) / 5 else (result.frequency - 5000) / 5

                @Suppress("DEPRECATION")
                val ssid = result.SSID ?: "Hidden"

                val securityIcon = when (security) {
                    "OPEN" -> "[!!!]"
                    "WEP" -> "[!! ]"
                    "WPA" -> "[!  ]"
                    else -> "[   ]"
                }

                appendOutput("  ${idx + 1}. $ssid\n")
                appendOutput("     BSSID: ${result.BSSID}\n")
                appendOutput("     Signal: ${result.level}dBm ($level/4) | Ch: $channel | $band\n")
                appendOutput("     Security: $security $securityIcon\n")
                if (hasWPS) appendOutput("     [WPS] WPS enabled - potential vulnerability!\n")
                if (security == "OPEN") appendOutput("     [!!!] Open network - no encryption!\n")
                appendOutput("\n")
            }
            appendOutput("╚══════════════════════════════════╝\n")
        } catch (e: SecurityException) {
            appendOutput("[E] Location permission required\n")
        } catch (e: Exception) {
            appendOutput("[E] Scan failed: ${e.message}\n")
        }
    }

    private fun findConnectedDevices() {
        scope.launch {
            appendOutput("[*] Scanning for connected devices...\n")
            val result = withContext(Dispatchers.IO) {
                val sb = StringBuilder("╔══════════════════════════════════╗\n")
                sb.append("║   Connected Devices Scanner     ║\n")
                sb.append("╠══════════════════════════════════╣\n\n")

                // Read ARP table
                val arpResult = ShellExecutor.execute("cat /proc/net/arp")
                if (arpResult.output.isNotEmpty()) {
                    val lines = arpResult.output.lines().filter { it.isNotBlank() && !it.contains("IP address") }
                    for ((idx, line) in lines.withIndex()) {
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size >= 4) {
                            val ip = parts[0]
                            val hwType = parts[1]
                            val flags = parts[2]
                            val mac = parts[3]
                            if (mac != "00:00:00:00:00:00") {
                                val vendor = guessVendor(mac)
                                sb.append("  ${idx + 1}. IP: $ip\n")
                                sb.append("     MAC: $mac\n")
                                sb.append("     Vendor: $vendor\n")
                                sb.append("     Type: $hwType | Flags: $flags\n\n")
                            }
                        }
                    }
                }

                // Try to ping scan the subnet
                val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info = wifiManager.connectionInfo
                val ipInt = info.ipAddress
                if (ipInt != 0) {
                    val ip = String.format("%d.%d.%d.%d", ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
                    val subnet = ip.substringBeforeLast(".")
                    sb.append("[*] Subnet: $subnet.0/24\n")
                    sb.append("[*] Running ping sweep...\n")

                    for (i in 1..254) {
                        try {
                            val addr = java.net.InetAddress.getByName("$subnet.$i")
                            if (addr.isReachable(100)) {
                                sb.append("  [ALIVE] $subnet.$i\n")
                            }
                        } catch (_: Exception) {}
                    }
                }

                sb.append("\n╚══════════════════════════════════╝\n")
                sb.toString()
            }
            appendOutput(result)
        }
    }

    private fun guessVendor(mac: String): String {
        val oui = mac.replace(":", "").uppercase().take(6)
        return when {
            oui.startsWith("001122") -> "Cisco"
            oui.startsWith("AABBCC") -> "Apple"
            oui.startsWith("DC2B2A") -> "Apple"
            oui.startsWith("3C22FB") -> "Apple"
            oui.startsWith("F8FF0A") -> "Apple"
            oui.startsWith("B827EB") -> "Raspberry Pi"
            oui.startsWith("DCA632") -> "Samsung"
            oui.startsWith("A0CBFD") -> "Samsung"
            oui.startsWith("9C3AAF") -> "Samsung"
            oui.startsWith("F43D7C") -> "Samsung"
            oui.startsWith("E894F6") -> "Samsung"
            oui.startsWith("507AC5") -> "Samsung"
            oui.startsWith("5C6A3D") -> "Samsung"
            oui.startsWith("B47443") -> "Samsung"
            oui.startsWith("E0ACCB") -> "Samsung"
            oui.startsWith("48A195") -> "Samsung"
            oui.startsWith("7825AD") -> "Samsung"
            oui.startsWith("40D357") -> "Samsung"
            oui.startsWith("70BCC5") -> "Samsung"
            oui.startsWith("484BAA") -> "Samsung"
            oui.startsWith("EC1F72") -> "Samsung"
            oui.startsWith("AC5F3E") -> "Samsung"
            oui.startsWith("B4A984") -> "Samsung"
            oui.startsWith("94B12A") -> "Samsung"
            oui.startsWith("D8F2CA") -> "Samsung"
            oui.startsWith("F01898") -> "Samsung"
            oui.startsWith("CC3A61") -> "Samsung"
            oui.startsWith("D4712A") -> "Samsung"
            oui.startsWith("60F081") -> "Samsung"
            oui.startsWith("4C11BF") -> "Samsung"
            oui.startsWith("BCE7LF") -> "Samsung"
            oui.startsWith("78F9C1") -> "Samsung"
            oui.startsWith("A488D9") -> "Samsung"
            oui.startsWith("680588") -> "Samsung"
            oui.startsWith("C8BA94") -> "Samsung"
            oui.startsWith("D4619D") -> "Samsung"
            oui.startsWith("14ABF5") -> "Samsung"
            oui.startsWith("30D27C") -> "Samsung"
            oui.startsWith("38AAE4") -> "Samsung"
            oui.startsWith("64404E") -> "Samsung"
            oui.startsWith("E4BEED") -> "Samsung"
            oui.startsWith("0C1D") -> "Samsung"
            oui.startsWith("B0D5") -> "Samsung"
            oui.startsWith("7C64") -> "Samsung"
            oui.startsWith("AC36") -> "Samsung"
            oui.startsWith("3423") -> "Samsung"
            oui.startsWith("F025") -> "Samsung"
            oui.startsWith("DC74") -> "Samsung"
            else -> "Unknown ($oui)"
        }
    }

    private fun checkWps() {
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║   WPS Vulnerability Check       ║\n")
        appendOutput("╠══════════════════════════════════╣\n\n")
        appendOutput("[*] WPS (WiFi Protected Setup) can be vulnerable to:\n")
        appendOutput("    - Pixie Dust attack (WPS PIN brute force)\n")
        appendOutput("    - Null PIN attack\n")
        appendOutput("    - PIN brute force attack\n\n")
        appendOutput("[*] Networks with WPS enabled:\n")

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    @Suppress("DEPRECATION")
                    val results = wifiManager.scanResults
                    val wpsNetworks = results.filter { it.capabilities.contains("WPS") }
                    if (wpsNetworks.isEmpty()) {
                        "[*] No WPS-enabled networks found in recent scan\n"
                    } else {
                        val sb = StringBuilder()
                        for ((idx, net) in wpsNetworks.withIndex()) {
                            @Suppress("DEPRECATION")
                            val ssid = net.SSID ?: "Hidden"
                            sb.append("  ${idx + 1}. $ssid (${net.BSSID})\n")
                            sb.append("     Signal: ${net.level}dBm\n")
                            sb.append("     [!] WPS is potentially vulnerable\n\n")
                        }
                        sb.toString()
                    }
                } catch (e: Exception) {
                    "[E] WPS check failed: ${e.message}\n"
                }
            }
            appendOutput(result)
            appendOutput("\n╚══════════════════════════════════╝\n")
        }
    }

    private fun monitorSignal() {
        try {
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val rssi = info.rssi
            val level = WifiManager.calculateSignalLevel(rssi, 5)
            val quality = when (level) {
                0 -> "Very Poor"
                1 -> "Poor"
                2 -> "Fair"
                3 -> "Good"
                4 -> "Excellent"
                else -> "Unknown"
            }
            val bars = "█".repeat(level + 1) + "░".repeat(4 - level)

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Signal Monitor                 ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("║ RSSI:     ${rssi}dBm\n")
            appendOutput("║ Level:    $level/4\n")
            appendOutput("║ Quality:  $quality\n")
            appendOutput("║ Signal:   [$bars]\n")
            appendOutput("║ Speed:    ${info.linkSpeed}Mbps\n")
            appendOutput("╚══════════════════════════════════╝\n")
        } catch (e: Exception) {
            appendOutput("[E] Signal monitor failed: ${e.message}\n")
        }
    }

    private fun detectDeauth() {
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║   Deauth Detection              ║\n")
        appendOutput("╠══════════════════════════════════╣\n\n")
        appendOutput("[*] Deauthentication attack detection\n")
        appendOutput("[*] This requires monitor mode (root + compatible WiFi adapter)\n\n")
        appendOutput("[*] Signs of deauth attack:\n")
        appendOutput("    - Sudden WiFi disconnections\n")
        appendOutput("    - Cannot reconnect to known network\n")
        appendOutput("    - Connection drops intermittently\n")
        appendOutput("    - Multiple reconnection attempts\n\n")

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ShellExecutor.execute("dmesg | grep -i 'deauth\\|disassoc\\|wlan' | tail -20")
            }
            if (result.output.isNotEmpty()) {
                appendOutput("[*] System WiFi logs:\n${result.output.take(1000)}\n\n")
            }
        }

        appendOutput("[*] Detection tools (root required):\n")
        appendOutput("    - tcpdump -i wlan0 -e -s 0 type mgt subtype deauth\n")
        appendOutput("    - airodump-ng (via external WiFi adapter)\n")
        appendOutput("    - Wireshark with monitor mode\n\n")
        appendOutput("╚══════════════════════════════════╝\n")
    }

    private fun checkWifiPower() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ShellExecutor.execute("cat /proc/net/wireless 2>/dev/null; iwconfig 2>/dev/null; iw dev wlan0 info 2>/dev/null")
            }
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   WiFi Power Info               ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            if (result.output.isNotEmpty()) {
                appendOutput(result.output.take(1500) + "\n")
            } else {
                appendOutput("[*] No wireless power info available\n")
                appendOutput("[*] Root access may be required\n")
            }
            appendOutput("╚══════════════════════════════════╝\n")
        }
    }

    private fun checkHandshake() {
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║   WiFi Handshake Capture Info   ║\n")
        appendOutput("╠══════════════════════════════════╣\n\n")
        appendOutput("[!] Handshake capture requires:\n")
        appendOutput("    1. Root access\n")
        appendOutput("    2. Monitor mode capable adapter\n")
        appendOutput("    3. External WiFi dongle (OTG)\n\n")
        appendOutput("[*] Process overview:\n")
        appendOutput("    1. Enable monitor mode: airmon-ng start wlan0\n")
        appendOutput("    2. Start capture: airodump-ng wlan0mon -c <ch> --bssid <mac> -w capture\n")
        appendOutput("    3. Force deauth: aireplay-ng -0 5 -a <mac> wlan0mon\n")
        appendOutput("    4. Crack: aircrack-ng -w wordlist.txt capture-01.cap\n\n")
        appendOutput("[!] For educational/authorized testing only!\n")
        appendOutput("╚══════════════════════════════════╝\n")
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
