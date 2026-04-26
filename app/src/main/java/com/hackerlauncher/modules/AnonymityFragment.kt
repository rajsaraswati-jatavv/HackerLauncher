package com.hackerlauncher.modules

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.hackerlauncher.R
import com.hackerlauncher.utils.Logger
import com.hackerlauncher.utils.ShellExecutor
import kotlinx.coroutines.*

class AnonymityFragment : Fragment() {

    private lateinit var tvAnonOutput: TextView
    private lateinit var scrollView: ScrollView
    private val logger = Logger()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_anonymity, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvAnonOutput = view.findViewById(R.id.tvAnonOutput)
        scrollView = view.findViewById(R.id.scrollViewAnon)
        val btnVpnStatus = view.findViewById<Button>(R.id.btnVpnStatus)
        val btnDnsCheck = view.findViewById<Button>(R.id.btnDnsCheck)
        val btnIpCheck = view.findViewById<Button>(R.id.btnIpCheck)
        val btnDnsChange = view.findViewById<Button>(R.id.btnDnsChange)
        val btnProxyInfo = view.findViewById<Button>(R.id.btnProxyInfo)
        val btnTorCheck = view.findViewById<Button>(R.id.btnTorCheck)
        val btnWifiPrivacy = view.findViewById<Button>(R.id.btnWifiPrivacy)

        btnVpnStatus.setOnClickListener { checkVpnStatus() }
        btnDnsCheck.setOnClickListener { checkDns() }
        btnIpCheck.setOnClickListener { checkPublicIp() }
        btnDnsChange.setOnClickListener { changeDns() }
        btnProxyInfo.setOnClickListener { checkProxy() }
        btnTorCheck.setOnClickListener { checkTor() }
        btnWifiPrivacy.setOnClickListener { openWifiPrivacy() }

        // Initial status check
        checkVpnStatus()
    }

    private fun checkVpnStatus() {
        scope.launch {
            val sb = StringBuilder("═══ VPN Status ═══\n")
            try {
                val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val network = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(network)

                if (caps != null) {
                    val isVpn = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                    sb.append("  Active Network: ${if (isVpn) "VPN" else "Regular"}\n")
                    sb.append("  VPN Transport: ${if (isVpn) "YES" else "NO"}\n")
                    sb.append("  Internet: ${caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)}\n")
                    sb.append("  Validated: ${caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)}\n")
                } else {
                    sb.append("  No active network\n")
                }

                // Check for running VPN apps
                val pm = requireContext().packageManager
                val vpnApps = listOf("com.example.vpn", "net.mullvad.mullvadvpn", "com.windscribe.vpn",
                    "org.strongswan.android", "de.blinkt.openvpn", "com.proton.vpn")
                sb.append("\n  Installed VPN Apps:\n")
                for (pkg in vpnApps) {
                    try {
                        pm.getPackageInfo(pkg, 0)
                        sb.append("  [+] $pkg\n")
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                sb.append("[E] Check failed: ${e.message}\n")
            }
            sb.append("════════════════════════\n")
            appendOutput(sb.toString())
        }
    }

    private fun checkDns() {
        scope.launch {
            appendOutput("[*] Checking DNS...\n")
            val result = withContext(Dispatchers.IO) {
                val sb = StringBuilder("═══ DNS Information ═══\n")
                try {
                    // Get current DNS servers
                    val cmd = "getprop net.dns1 && getprop net.dns2 && getprop net.wifi.dns1 && getprop net.wifi.dns2"
                    val exec = ShellExecutor.execute(cmd)
                    sb.append("  DNS Servers:\n")
                    if (exec.output.isNotEmpty()) {
                        exec.output.lines().filter { it.isNotBlank() }.forEach {
                            sb.append("    $it\n")
                        }
                    }

                    // Test DNS resolution
                    val testDomains = listOf("google.com", "dnsleaktest.com", "cloudflare.com")
                    sb.append("\n  DNS Resolution Test:\n")
                    for (domain in testDomains) {
                        try {
                            val start = System.currentTimeMillis()
                            java.net.InetAddress.getByName(domain)
                            val time = System.currentTimeMillis() - start
                            sb.append("    $domain: ${time}ms\n")
                        } catch (e: Exception) {
                            sb.append("    $domain: FAILED (${e.message})\n")
                        }
                    }
                } catch (e: Exception) {
                    sb.append("[E] DNS check failed: ${e.message}\n")
                }
                sb.append("════════════════════════\n")
                sb.toString()
            }
            appendOutput(result)
        }
    }

    private fun checkPublicIp() {
        scope.launch {
            appendOutput("[*] Checking public IP...\n")
            val result = withContext(Dispatchers.IO) {
                val sb = StringBuilder("═══ Public IP Check ═══\n")
                try {
                    val url = java.net.URL("http://ip-api.com/json/")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    val response = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()

                    val extract = { key: String ->
                        response.substringAfter("\"$key\":\"").substringBefore("\"")
                    }
                    sb.append("  IP:       ${extract("query")}\n")
                    sb.append("  Country:  ${extract("country")}\n")
                    sb.append("  City:     ${extract("city")}\n")
                    sb.append("  ISP:      ${extract("isp")}\n")
                    sb.append("  Org:      ${extract("org")}\n")
                    sb.append("  Timezone: ${extract("timezone")}\n")

                    // Check if likely VPN/Proxy
                    val isp = extract("isp").lowercase()
                    val org = extract("org").lowercase()
                    val vpnKeywords = listOf("vpn", "proxy", "tunnel", "hide", "anonymous", "private", "mullvad", "nordvpn", "express")
                    val isLikelyVpn = vpnKeywords.any { isp.contains(it) || org.contains(it) }
                    sb.append("\n  VPN/Proxy Detected: ${if (isLikelyVpn) "POSSIBLE" else "UNLIKELY"}\n")
                } catch (e: Exception) {
                    sb.append("[E] IP check failed: ${e.message}\n")
                }
                sb.append("════════════════════════\n")
                sb.toString()
            }
            appendOutput(result)
        }
    }

    private fun changeDns() {
        appendOutput("═══ DNS Change ═══\n")
        appendOutput("[*] Common DNS options:\n")
        appendOutput("  1. Cloudflare (1.1.1.1 / 1.0.0.1) - Privacy focused\n")
        appendOutput("  2. Google (8.8.8.8 / 8.8.4.4) - Fast\n")
        appendOutput("  3. Quad9 (9.9.9.9) - Security focused\n")
        appendOutput("  4. AdGuard (94.140.14.14) - Ad blocking\n")
        appendOutput("\n[!] On Android 9+, use Settings > Network > Private DNS\n")
        appendOutput("    Cloudflare: dns.cloudflare.com\n")
        appendOutput("    Google: dns.google\n")
        appendOutput("    Quad9: dns.quad9.net\n")

        // Open Android DNS settings
        try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            appendOutput("[E] Could not open settings: ${e.message}\n")
        }
        appendOutput("════════════════════════\n")
    }

    private fun checkProxy() {
        scope.launch {
            appendOutput("[*] Checking proxy settings...\n")
            val result = withContext(Dispatchers.IO) {
                val sb = StringBuilder("═══ Proxy Information ═══\n")
                try {
                    // Check system proxy
                    val httpProxy = System.getProperty("http.proxyHost")
                    val httpPort = System.getProperty("http.proxyPort")
                    val httpsProxy = System.getProperty("https.proxyHost")
                    val httpsPort = System.getProperty("https.proxyPort")

                    sb.append("  HTTP Proxy:  ${httpProxy ?: "None"}:${httpPort ?: "N/A"}\n")
                    sb.append("  HTTPS Proxy: ${httpsProxy ?: "None"}:${httpsPort ?: "N/A"}\n")

                    // Check for proxy apps
                    val pm = requireContext().packageManager
                    val proxyApps = listOf("org.torproject.android", "com.m3958.app.pproxyservice",
                        "com.github.shadowsocks", "com.v2ray.ang")
                    sb.append("\n  Installed Proxy/Tunnel Apps:\n")
                    for (pkg in proxyApps) {
                        try {
                            pm.getPackageInfo(pkg, 0)
                            sb.append("  [+] $pkg\n")
                        } catch (_: Exception) {}
                    }

                    sb.append("\n  Proxy Setup Options:\n")
                    sb.append("  - Orbot (Tor): Install from F-Droid\n")
                    sb.append("  - Shadowsocks: Install from F-Droid\n")
                    sb.append("  - v2rayNG: Install from GitHub releases\n")
                } catch (e: Exception) {
                    sb.append("[E] Proxy check failed: ${e.message}\n")
                }
                sb.append("════════════════════════\n")
                sb.toString()
            }
            appendOutput(result)
        }
    }

    private fun checkTor() {
        scope.launch {
            appendOutput("[*] Checking Tor status...\n")
            val result = withContext(Dispatchers.IO) {
                val sb = StringBuilder("═══ Tor Check ═══\n")
                try {
                    // Check if Orbot is installed
                    val pm = requireContext().packageManager
                    var orbotInstalled = false
                    try {
                        pm.getPackageInfo("org.torproject.android", 0)
                        orbotInstalled = true
                        sb.append("  Orbot: INSTALLED\n")
                    } catch (_: Exception) {
                        sb.append("  Orbot: NOT INSTALLED\n")
                    }

                    // Check Tor check service
                    try {
                        val url = java.net.URL("https://check.torproject.org/")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 8000
                        val response = conn.inputStream.bufferedReader().readText()
                        val isUsingTor = response.contains("Congratulations") || response.contains("This browser is configured to use Tor")
                        sb.append("  Using Tor: ${if (isUsingTor) "YES" else "NO"}\n")
                        conn.disconnect()
                    } catch (e: Exception) {
                        sb.append("  Tor check service: ${e.message}\n")
                    }

                    if (!orbotInstalled) {
                        sb.append("\n  [*] Install Orbot from F-Droid for Tor support\n")
                        sb.append("      https://guardianproject.info/apps/org.torproject.android/\n")
                    }
                } catch (e: Exception) {
                    sb.append("[E] Tor check failed: ${e.message}\n")
                }
                sb.append("════════════════════════\n")
                sb.toString()
            }
            appendOutput(result)
        }
    }

    private fun openWifiPrivacy() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
            appendOutput("[*] Opened WiFi settings\n")
        } catch (e: Exception) {
            appendOutput("[E] Could not open WiFi settings: ${e.message}\n")
        }
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvAnonOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
}
