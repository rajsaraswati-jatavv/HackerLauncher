package com.hackerlauncher.modules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
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
import java.net.NetworkInterface

class VpnMonitorFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var monitoring = false
    private var lastVpnState = false
    private var vpnReceiver: BroadcastReceiver? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()

        val rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val tvTitle = TextView(ctx).apply {
            text = "[ VPN MONITOR ]"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvTitle)

        val btnRow1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val btnRow2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

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

        btnRow1.addView(makeBtn("Check VPN") { checkVpnStatus() })
        btnRow1.addView(makeBtn("Interfaces") { listNetworkInterfaces() })
        btnRow1.addView(makeBtn("Monitor") { toggleMonitoring() })
        btnRow2.addView(makeBtn("VPN Apps") { detectVpnApps() })
        btnRow2.addView(makeBtn("Tunnel Info") { showTunnelInfo() })
        btnRow2.addView(makeBtn("DNS Leak") { dnsLeakTest() })

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
        appendOutput("║       VPN MONITOR v1.0          ║\n")
        appendOutput("║  Monitor VPN connections & leaks ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        registerVpnReceiver()

        return rootLayout
    }

    private fun registerVpnReceiver() {
        try {
            vpnReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    try {
                        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                        val network = cm.activeNetwork
                        val caps = cm.getNetworkCapabilities(network)
                        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                        val time = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
                        if (isVpn != lastVpnState) {
                            appendOutput("[🔔 ALERT] VPN ${if (isVpn) "CONNECTED" else "DISCONNECTED"} at $time\n")
                            lastVpnState = isVpn
                        }
                    } catch (_: Exception) {}
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(vpnReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION), Context.RECEIVER_NOT_EXPORTED)
            } else {
                requireContext().registerReceiver(vpnReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
            }
        } catch (e: Exception) {
            appendOutput("[E] BroadcastReceiver register failed: ${e.message}\n")
        }
    }

    private fun checkVpnStatus() {
        try {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║       VPN Status Check          ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

            if (hasVpn) {
                appendOutput("║ [+] VPN is ACTIVE\n")
                appendOutput("║ [+] Traffic is tunneled\n")

                val linkDown = caps?.linkDownstreamBandwidthKbps ?: 0
                val linkUp = caps?.linkUpstreamBandwidthKbps ?: 0
                appendOutput("║ Down: ${linkDown}Kbps | Up: ${linkUp}Kbps\n")

                val allNetworks = cm.allNetworks
                for (net in allNetworks) {
                    val nc = cm.getNetworkCapabilities(net)
                    if (nc?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                        val info = cm.getNetworkInfo(net)
                        appendOutput("║ Network: ${info?.typeName} ${info?.subtypeName}\n")
                        appendOutput("║ State: ${info?.state}\n")
                        appendOutput("║ Detailed: ${info?.detailedState}\n")

                        // Get VPN app name from PackageManager
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val ownerUid = nc.ownerUid
                                if (ownerUid != 0) {
                                    val pm = requireContext().packageManager
                                    val apps = pm.getInstalledApplications(0)
                                    for (app in apps) {
                                        if (app.uid == ownerUid) {
                                            appendOutput("║ VPN App: ${app.loadLabel(pm)} (${app.packageName})\n")
                                            break
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            appendOutput("║ VPN App UID lookup: ${e.message}\n")
                        }
                    }
                }
            } else {
                appendOutput("║ [-] VPN is NOT active\n")
                appendOutput("║ [!] Traffic may be unencrypted\n")
                val activeInfo = cm.activeNetworkInfo
                appendOutput("║ Active: ${activeInfo?.typeName} ${activeInfo?.subtypeName}\n")
                appendOutput("║ State: ${activeInfo?.state}\n")
            }

            // Check for tun0 interface
            try {
                val tunInterfaces = NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.name.startsWith("tun") || it.name.startsWith("ppp") || it.name.startsWith("pptp") }
                if (tunInterfaces.isNotEmpty()) {
                    appendOutput("║ [+] VPN tunnel interfaces found:\n")
                    for (tun in tunInterfaces) {
                        appendOutput("║   ${tun.name}: ${tun.interfaceAddresses}\n")
                        appendOutput("║   MTU: ${tun.mtu}\n")
                        appendOutput("║   Up: ${tun.isUp}\n")
                    }
                } else if (hasVpn) {
                    appendOutput("║ [*] VPN active but no tun interface detected\n")
                }
            } catch (e: Exception) {
                appendOutput("║ [E] Interface check: ${e.message}\n")
            }

            lastVpnState = hasVpn
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] VPN check failed: ${e.message}\n")
        }
    }

    private fun listNetworkInterfaces() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Network Interfaces          ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            try {
                val interfaces = withContext(Dispatchers.IO) {
                    NetworkInterface.getNetworkInterfaces().toList()
                }
                for (netIf in interfaces) {
                    val isVpn = netIf.name.startsWith("tun") || netIf.name.startsWith("ppp")
                    val marker = if (isVpn) " [VPN]" else ""
                    appendOutput("║ ${netIf.name}$marker\n")
                    appendOutput("║   Display: ${netIf.displayName}\n")
                    appendOutput("║   Up: ${netIf.isUp} | MTU: ${netIf.mtu}\n")
                    appendOutput("║   Loopback: ${netIf.isLoopback} | P2P: ${netIf.isPointToPoint}\n")
                    appendOutput("║   Virtual: ${netIf.isVirtual} | Multicast: ${netIf.supportsMulticast()}\n")
                    for (addr in netIf.interfaceAddresses) {
                        appendOutput("║   Addr: ${addr.address.hostAddress}/${addr.networkPrefixLength}\n")
                    }
                    try {
                        val mac = netIf.hardwareAddress
                        if (mac != null) {
                            val macStr = mac.joinToString(":") { "%02X".format(it) }
                            appendOutput("║   MAC: $macStr\n")
                        }
                    } catch (_: Exception) {}
                    appendOutput("║\n")
                }
            } catch (e: Exception) {
                appendOutput("║ [E] ${e.message}\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun toggleMonitoring() {
        monitoring = !monitoring
        if (monitoring) {
            appendOutput("[*] VPN monitoring started. Checking every 5s...\n")
            scope.launch {
                while (monitoring && isActive) {
                    delay(5000)
                    try {
                        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                        val network = cm.activeNetwork
                        val caps = cm.getNetworkCapabilities(network)
                        val currentVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                        if (currentVpn != lastVpnState) {
                            val time = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
                            if (currentVpn) {
                                appendOutput("\n[🔔 ALERT] VPN CONNECTED at $time\n")
                            } else {
                                appendOutput("\n[🔔 ALERT] VPN DISCONNECTED at $time\n")
                            }
                            lastVpnState = currentVpn
                        }
                    } catch (e: Exception) {
                        appendOutput("[E] Monitor error: ${e.message}\n")
                    }
                }
            }
        } else {
            appendOutput("[*] VPN monitoring stopped.\n")
        }
    }

    private fun detectVpnApps() {
        try {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║      VPN Apps Detection         ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val pm = requireContext().packageManager
            val vpnApps = mutableListOf<String>()

            val knownVpns = mapOf(
                "com.nordvpn.android" to "NordVPN",
                "com.expressvpn.vpn" to "ExpressVPN",
                "com.mullvad.mullvadvpn" to "Mullvad",
                "com.wireguard.android" to "WireGuard",
                "org.strongswan.android" to "strongSwan",
                "de.blinkt.openvpn" to "OpenVPN for Android",
                "net.openvpn.openvpn" to "OpenVPN Connect",
                "com.proton.vpn" to "ProtonVPN",
                "ch.protonvpn.android" to "ProtonVPN",
                "com.cloudflare.onedotonedotonedotone" to "1.1.1.1 WARP",
                "com.surfshark.vpnclient.android" to "Surfshark",
                "com.vyprvpn.app" to "VyprVPN",
                "com.privateinternetaccess.android" to "PIA VPN",
                "com.tunnelbear.android" to "TunnelBear",
                "com.hotspotshield.android.vpn" to "Hotspot Shield"
            )

            for ((pkg, name) in knownVpns) {
                try {
                    val info = pm.getPackageInfo(pkg, 0)
                    vpnApps.add("$name ($pkg) v${info.versionName}")
                    appendOutput("║ [+] $name installed (v${info.versionName})\n")
                } catch (_: Exception) {}
            }

            if (vpnApps.isEmpty()) {
                appendOutput("║ [*] No known VPN apps detected\n")
            }
            appendOutput("║\n║ Total VPN apps: ${vpnApps.size}\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] VPN app detection failed: ${e.message}\n")
        }
    }

    private fun showTunnelInfo() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║        Tunnel Information       ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            try {
                val interfaces = withContext(Dispatchers.IO) {
                    NetworkInterface.getNetworkInterfaces().toList()
                        .filter { it.name.startsWith("tun") || it.name.startsWith("ppp") || it.name.startsWith("wg") }
                }
                if (interfaces.isEmpty()) {
                    appendOutput("║ [*] No VPN tunnel interfaces found\n")
                    appendOutput("║ [*] VPN may not be connected\n")
                } else {
                    for (tun in interfaces) {
                        appendOutput("║ Interface: ${tun.name}\n")
                        appendOutput("║   Display: ${tun.displayName}\n")
                        appendOutput("║   Status: ${if (tun.isUp) "UP" else "DOWN"}\n")
                        appendOutput("║   MTU: ${tun.mtu}\n")
                        appendOutput("║   Point-to-Point: ${tun.isPointToPoint}\n")
                        for (addr in tun.interfaceAddresses) {
                            appendOutput("║   IP: ${addr.address.hostAddress}/${addr.networkPrefixLength}\n")
                            val broadcast = addr.broadcast
                            if (broadcast != null) {
                                appendOutput("║   Broadcast: ${broadcast.hostAddress}\n")
                            }
                        }
                        appendOutput("║\n")
                    }
                }
            } catch (e: Exception) {
                appendOutput("║ [E] ${e.message}\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun dnsLeakTest() {
        scope.launch {
            appendOutput("[*] Running DNS leak test...\n")
            try {
                val results = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    val dnsServers = mutableListOf<String>()

                    try {
                        val process = Runtime.getRuntime().exec("getprop net.dns1")
                        val output = process.inputStream.bufferedReader().readText().trim()
                        if (output.isNotEmpty()) dnsServers.add("net.dns1: $output")
                    } catch (_: Exception) {}

                    try {
                        val process = Runtime.getRuntime().exec("getprop net.dns2")
                        val output = process.inputStream.bufferedReader().readText().trim()
                        if (output.isNotEmpty()) dnsServers.add("net.dns2: $output")
                    } catch (_: Exception) {}

                    try {
                        val process = Runtime.getRuntime().exec("getprop net.wlan0.dns1")
                        val output = process.inputStream.bufferedReader().readText().trim()
                        if (output.isNotEmpty()) dnsServers.add("net.wlan0.dns1: $output")
                    } catch (_: Exception) {}

                    val start = System.currentTimeMillis()
                    val addresses = java.net.InetAddress.getAllByName("dnsleaktest.com")
                    val elapsed = System.currentTimeMillis() - start

                    sb.append("DNS Servers:\n")
                    for (dns in dnsServers) {
                        sb.append("  $dns\n")
                    }
                    sb.append("\nResolution Test:\n")
                    sb.append("  Domain: dnsleaktest.com\n")
                    sb.append("  Time: ${elapsed}ms\n")
                    sb.append("  Addresses:\n")
                    for (addr in addresses) {
                        sb.append("    ${addr.hostAddress}\n")
                    }
                    when {
                        elapsed < 50 -> sb.append("  [+] Fast resolution - likely local/VPN DNS\n")
                        elapsed < 200 -> sb.append("  [*] Normal resolution time\n")
                        else -> sb.append("  [!] Slow resolution - possible DNS leak or ISP DNS\n")
                    }
                    sb.toString()
                }
                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║         DNS Leak Test           ║\n")
                appendOutput("╠══════════════════════════════════╣\n")
                appendOutput(results)
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] DNS leak test failed: ${e.message}\n")
            }
        }
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        monitoring = false
        try { vpnReceiver?.let { requireContext().unregisterReceiver(it) } } catch (_: Exception) {}
        scope.cancel()
    }
}
