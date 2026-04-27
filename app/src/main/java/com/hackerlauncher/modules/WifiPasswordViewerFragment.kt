package com.hackerlauncher.modules

import android.graphics.Typeface
import android.os.Build
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

class WifiPasswordViewerFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()

        val rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val tvTitle = TextView(ctx).apply {
            text = "[ WiFi PASSWORDS ]"
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

        btnRow1.addView(makeBtn("View All") { viewAllPasswords() })
        btnRow1.addView(makeBtn("Current WiFi") { viewCurrentWifi() })
        btnRow1.addView(makeBtn("wpa_supplicant") { readWpaSupplicant() })
        btnRow2.addView(makeBtn("WiFi Config") { readWifiConfig() })
        btnRow2.addView(makeBtn("Android 10+") { tryAndroid10Api() })
        btnRow2.addView(makeBtn("Export") { exportPasswords() })

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
        appendOutput("║    WIFI PASSWORD VIEWER v1.0    ║\n")
        appendOutput("║  View saved WiFi passwords      ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")
        appendOutput("[!] ROOT access required to read passwords\n\n")

        return rootLayout
    }

    private fun viewAllPasswords() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║    Saved WiFi Passwords         ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            try {
                val result = withContext(Dispatchers.IO) {
                    val wpaResult = ShellExecutor.execute("su -c 'cat /data/misc/wifi/wpa_supplicant.conf 2>/dev/null'")
                    if (wpaResult.output.isNotEmpty() && !wpaResult.output.contains("Permission denied")) {
                        parseWpaSupplicant(wpaResult.output)
                    } else {
                        val altResult = ShellExecutor.execute("su -c 'cat /data/wifi/wpa_supplicant.conf 2>/dev/null; cat /data/misc/wifi/wpa_supplicant.conf 2>/dev/null'")
                        if (altResult.output.isNotEmpty() && !altResult.output.contains("Permission denied")) {
                            parseWpaSupplicant(altResult.output)
                        } else {
                            val configResult = ShellExecutor.execute("su -c 'ls /data/misc/wifi/ 2>/dev/null'")
                            "NO_ROOT\nAvailable files:\n${configResult.output}"
                        }
                    }
                }

                if (result.startsWith("NO_ROOT")) {
                    appendOutput("║ [!] ROOT ACCESS REQUIRED\n")
                    appendOutput("║ [*] WiFi passwords are stored in:\n")
                    appendOutput("║   /data/misc/wifi/wpa_supplicant.conf\n")
                    appendOutput("║   /data/misc/wifi/WifiConfigStore.xml\n")
                    appendOutput("║\n║ Install Magisk for root access\n")
                    appendOutput("║ Or use ADB: adb pull /data/misc/wifi/\n")
                } else {
                    appendOutput(result)
                }
            } catch (e: Exception) {
                appendOutput("║ [E] ${e.message}\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun viewCurrentWifi() {
        try {
            val wifiManager = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val info = wifiManager.connectionInfo
            val ssid = info.ssid?.removeSurrounding("\"") ?: "Unknown"
            val bssid = info.bssid ?: "Unknown"
            val rssi = info.rssi
            val speed = info.linkSpeed

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Current WiFi Info           ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("║ SSID:    $ssid\n")
            appendOutput("║ BSSID:   $bssid\n")
            appendOutput("║ Signal:  ${rssi}dBm\n")
            appendOutput("║ Speed:   ${speed}Mbps\n")
            appendOutput("║ Net ID:  ${info.networkId}\n")
            appendOutput("║ Hidden:  ${info.hiddenSSID}\n")

            // Determine security type
            try {
                val securityType = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        // Use newer API if available
                        "Check WiFi Config for details"
                    }
                    else -> "WPA/WPA2"
                }
                appendOutput("║ Security: $securityType\n")
            } catch (e: Exception) {
                appendOutput("║ Security: Unknown\n")
            }

            scope.launch {
                try {
                    val pwResult = withContext(Dispatchers.IO) {
                        ShellExecutor.execute("su -c 'grep -A 10 \"$ssid\" /data/misc/wifi/wpa_supplicant.conf 2>/dev/null'")
                    }
                    if (pwResult.output.isNotEmpty()) {
                        val psk = pwResult.output.lines().find { it.trim().startsWith("psk=") }
                        val wepKey = pwResult.output.lines().find { it.trim().startsWith("wep_key0=") }
                        val keyMgmt = pwResult.output.lines().find { it.trim().startsWith("key_mgmt=") }
                        if (psk != null) {
                            appendOutput("║ PSK:     ${psk.trim().substringAfter("psk=")}\n")
                        } else if (wepKey != null) {
                            appendOutput("║ WEP Key: ${wepKey.trim().substringAfter("wep_key0=")}\n")
                        } else {
                            appendOutput("║ Security: Open or password not found\n")
                        }
                        if (keyMgmt != null) {
                            appendOutput("║ Key Mgmt: ${keyMgmt.trim().substringAfter("key_mgmt=")}\n")
                        }
                    } else {
                        appendOutput("║ Password: [Root required to view]\n")
                    }
                } catch (e: Exception) {
                    appendOutput("║ Password: [Root required]\n")
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            }
        } catch (e: Exception) {
            appendOutput("[E] ${e.message}\n")
        }
    }

    private fun readWpaSupplicant() {
        scope.launch {
            appendOutput("[*] Reading wpa_supplicant.conf...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("su -c 'cat /data/misc/wifi/wpa_supplicant.conf 2>/dev/null'")
                }
                if (result.output.isEmpty() || result.output.contains("Permission denied")) {
                    appendOutput("[!] Root access required\n")
                    appendOutput("[*] Alternative paths:\n")
                    appendOutput("  /data/misc/wifi/wpa_supplicant.conf\n")
                    appendOutput("  /data/wifi/wpa_supplicant.conf\n")
                    appendOutput("  /etc/wifi/wpa_supplicant.conf\n")
                } else {
                    appendOutput("╔══════════════════════════════════╗\n")
                    appendOutput("║   wpa_supplicant.conf           ║\n")
                    appendOutput("╠══════════════════════════════════╣\n")
                    for (line in result.output.lines()) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("psk=")) {
                            val key = trimmed.substringAfter("psk=")
                            if (key.startsWith("\"") && key.endsWith("\"")) {
                                val pw = key.removeSurrounding("\"")
                                val masked = if (pw.length > 3) pw.take(2) + "*".repeat(pw.length - 4) + pw.takeLast(2) else "***"
                                appendOutput("║ psk=\"$masked\"\n")
                            } else {
                                appendOutput("║ psk=[hashed]\n")
                            }
                        } else {
                            appendOutput("║ $trimmed\n")
                        }
                    }
                    appendOutput("╚══════════════════════════════════╝\n\n")
                }
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun readWifiConfig() {
        scope.launch {
            appendOutput("[*] Reading WiFi config store...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("su -c 'cat /data/misc/wifi/WifiConfigStore.xml 2>/dev/null'")
                }
                if (result.output.isEmpty() || result.output.contains("Permission denied")) {
                    appendOutput("[!] Root access required\n")
                    try {
                        val wifiManager = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                        @Suppress("DEPRECATION")
                        val configs = wifiManager.configuredNetworks
                        if (configs != null) {
                            appendOutput("╔══════════════════════════════════╗\n")
                            appendOutput("║   Saved Networks (no passwords) ║\n")
                            appendOutput("╠══════════════════════════════════╣\n")
                            for (config in configs) {
                                val ssid = config.SSID?.removeSurrounding("\"") ?: "Unknown"
                                val security = when {
                                    config.allowedKeyManagement.get(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK) -> "WPA/WPA2"
                                    config.allowedKeyManagement.get(android.net.wifi.WifiConfiguration.KeyMgmt.IEEE8021X) -> "802.1X"
                                    config.wepKeys?.any { it != null } == true -> "WEP"
                                    else -> "Open"
                                }
                                appendOutput("║ SSID: $ssid [$security]\n")
                            }
                            appendOutput("╚══════════════════════════════════╝\n\n")
                        }
                    } catch (e: SecurityException) {
                        appendOutput("[!] Location permission required for network list\n")
                    }
                } else {
                    appendOutput("╔══════════════════════════════════╗\n")
                    appendOutput("║   WifiConfigStore.xml           ║\n")
                    appendOutput("╠══════════════════════════════════╣\n")
                    appendOutput(result.output.take(3000))
                    appendOutput("\n╚══════════════════════════════════╝\n\n")
                }
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun tryAndroid10Api() {
        appendOutput("[*] Trying Android 10+ WiFi API...\n")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║   Android 10+ WiFi Info         ║\n")
                appendOutput("╠══════════════════════════════════╣\n")
                appendOutput("║ [i] Android 10+ restricts access\n")
                appendOutput("║ [*] WiFi passwords require root\n")
                appendOutput("║ [*] Use WifiNetworkSuggestionBuilder\n")
                appendOutput("║     for programmatic WiFi access\n")
                appendOutput("║ [*] Settings > Network & Internet\n")
                appendOutput("║     > WiFi > Saved Networks\n")

                val wifiManager = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                val info = wifiManager.connectionInfo
                val ssid = info.ssid?.removeSurrounding("\"") ?: "Unknown"
                appendOutput("║\n║ Current: $ssid\n")
                appendOutput("║ Signal:  ${info.rssi}dBm\n")
                appendOutput("╚══════════════════════════════════╝\n\n")
            } else {
                appendOutput("[i] Android 10+ API not available on this device\n")
                // Fall back to legacy API
                readWifiConfig()
            }
        } catch (e: Exception) {
            appendOutput("[E] ${e.message}\n")
        }
    }

    private fun exportPasswords() {
        scope.launch {
            appendOutput("[*] Exporting WiFi passwords...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("su -c 'cat /data/misc/wifi/wpa_supplicant.conf 2>/dev/null'")
                }
                if (result.output.isNotEmpty() && !result.output.contains("Permission denied")) {
                    val exportDir = android.os.Environment.getExternalStorageDirectory()
                    val exportFile = java.io.File(exportDir, "Download/wifi_passwords_export.txt")
                    exportFile.parentFile?.mkdirs()
                    exportFile.writeText(result.output)
                    appendOutput("[+] Exported to ${exportFile.absolutePath}\n")
                } else {
                    appendOutput("[!] Root required for export\n")
                }
            } catch (e: Exception) {
                appendOutput("[E] Export failed: ${e.message}\n")
            }
        }
    }

    private fun parseWpaSupplicant(data: String): String {
        val sb = StringBuilder()
        val networks = data.split("network=").drop(1)
        for ((idx, network) in networks.withIndex()) {
            val ssidMatch = Regex("ssid=\"([^\"]+)\"").find(network)
            val pskMatch = Regex("psk=\"([^\"]+)\"").find(network)
            val wepMatch = Regex("wep_key0=\"([^\"]+)\"").find(network)
            val keyMgmt = Regex("key_mgmt=([^\n]+)").find(network)
            val priority = Regex("priority=(\\d+)").find(network)

            val ssid = ssidMatch?.groupValues?.get(1) ?: "Unknown"
            val security = keyMgmt?.groupValues?.get(1)?.trim() ?: "NONE"
            val password = pskMatch?.groupValues?.get(1) ?: wepMatch?.groupValues?.get(1) ?: "No password (Open)"

            sb.append("║ ${idx + 1}. SSID: $ssid\n")
            sb.append("║    Security: $security\n")
            sb.append("║    Password: $password\n")
            if (priority != null) sb.append("║    Priority: ${priority.groupValues[1]}\n")
            sb.append("║\n")
        }
        return sb.toString()
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
