package com.hackerlauncher.modules

import android.content.Context
import android.net.wifi.WifiManager
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

class SubnetScannerFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private val logger = Logger
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_network, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvOutput = view.findViewById(R.id.tvNetOutput)
        scrollView = view.findViewById(R.id.scrollViewNetwork)
        etHost = view.findViewById(R.id.etHost)
        etPort = view.findViewById(R.id.etPort)

        val btnWifiInfo = view.findViewById<Button>(R.id.btnWifiInfo)
        val btnScanWifi = view.findViewById<Button>(R.id.btnScanWifi)
        val btnArpTable = view.findViewById<Button>(R.id.btnArpTable)
        val btnDnsLookup = view.findViewById<Button>(R.id.btnDnsLookup)
        val btnPing = view.findViewById<Button>(R.id.btnPing)
        val btnPortScan = view.findViewById<Button>(R.id.btnPortScan)
        val btnIfconfig = view.findViewById<Button>(R.id.btnIfconfig)
        val btnNetstat = view.findViewById<Button>(R.id.btnNetstat)

        btnWifiInfo.text = "Subnet Scan"
        btnScanWifi.text = "Host Scan"
        btnArpTable.text = "Port Scan"
        btnDnsLookup.text = "Service Enum"
        btnPing.text = "Ping Sweep"
        btnPortScan.text = "Traceroute"
        btnIfconfig.text = "Net Info"
        btnNetstat.text = "Connections"

        etHost.hint = "Host or subnet"
        etPort.hint = "Port or range"

        btnWifiInfo.setOnClickListener { scanSubnet() }
        btnScanWifi.setOnClickListener { scanHost() }
        btnArpTable.setOnClickListener { portScanHost() }
        btnDnsLookup.setOnClickListener { enumerateServices() }
        btnPing.setOnClickListener { pingSweep() }
        btnPortScan.setOnClickListener { traceroute() }
        btnIfconfig.setOnClickListener { networkInfo() }
        btnNetstat.setOnClickListener { showConnections() }
    }

    private fun scanSubnet() {
        scope.launch {
            appendOutput("[*] Detecting subnet...\n")
            val result = withContext(Dispatchers.IO) {
                try {
                    val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val info = wifiManager.connectionInfo
                    val ipInt = info.ipAddress
                    if (ipInt == 0) return@withContext "[E] Not connected to WiFi"

                    val ip = String.format("%d.%d.%d.%d", ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
                    val subnet = ip.substringBeforeLast(".")

                    val sb = StringBuilder("╔══════════════════════════════════╗\n")
                    sb.append("║   Subnet Scanner                 ║\n")
                    sb.append("╠══════════════════════════════════╣\n")
                    sb.append("║ Your IP: $ip\n")
                    sb.append("║ Subnet:  $subnet.0/24\n\n")

                    var alive = 0
                    val hosts = mutableListOf<String>()
                    for (i in 1..254) {
                        try {
                            val addr = java.net.InetAddress.getByName("$subnet.$i")
                            if (addr.isReachable(200)) {
                                alive++
                                val hostname = addr.hostName
                                sb.append("  [ALIVE] $subnet.$i ($hostname)\n")
                                hosts.add("$subnet.$i")
                            }
                        } catch (_: Exception) {}
                    }
                    sb.append("\n║ Total alive hosts: $alive\n")
                    sb.append("╚══════════════════════════════════╝\n")
                    sb.toString()
                } catch (e: Exception) {
                    "[E] Subnet scan failed: ${e.message}"
                }
            }
            appendOutput(result + "\n")
        }
    }

    private fun scanHost() {
        val host = etHost.text.toString().trim()
        if (host.isEmpty()) {
            appendOutput("[!] Enter a host IP\n")
            return
        }
        scope.launch {
            appendOutput("[*] Scanning host: $host\n")
            val result = withContext(Dispatchers.IO) {
                try {
                    val addr = java.net.InetAddress.getByName(host)
                    val sb = StringBuilder("╔══════════════════════════════════╗\n")
                    sb.append("║   Host Scan: $host\n")
                    sb.append("╠══════════════════════════════════╣\n")
                    sb.append("║ Hostname: ${addr.hostName}\n")
                    sb.append("║ Reachable: ${addr.isReachable(3000)}\n")
                    sb.append("║ IP: ${addr.hostAddress}\n\n")

                    // Scan common ports
                    val commonPorts = listOf(21, 22, 23, 25, 53, 80, 110, 135, 139, 443, 445, 993, 995, 1433, 3306, 3389, 5432, 5900, 6379, 8080, 8443, 8888, 9090, 27017)
                    sb.append("║ Port Scan Results:\n")
                    for (port in commonPorts) {
                        try {
                            val socket = java.net.Socket()
                            socket.connect(java.net.InetSocketAddress(host, port), 800)
                            socket.close()
                            val service = getServiceName(port)
                            sb.append("║   [OPEN] $port ($service)\n")
                        } catch (_: Exception) {}
                    }
                    sb.append("╚══════════════════════════════════╝\n")
                    sb.toString()
                } catch (e: Exception) {
                    "[E] Host scan failed: ${e.message}"
                }
            }
            appendOutput(result + "\n")
        }
    }

    private fun portScanHost() {
        val host = etHost.text.toString().trim()
        val portStr = etPort.text.toString().trim()
        if (host.isEmpty()) { appendOutput("[!] Enter host\n"); return }

        scope.launch {
            appendOutput("[*] Port scanning: $host\n")
            val result = withContext(Dispatchers.IO) {
                val ports = if (portStr.isEmpty()) {
                    (1..1024).toList()
                } else if (portStr.contains("-")) {
                    val range = portStr.split("-")
                    (range[0].toInt()..range[1].toInt()).toList()
                } else {
                    portStr.split(",").map { it.trim().toInt() }
                }

                val sb = StringBuilder("╔══════════════════════════════════╗\n")
                sb.append("║   Port Scan: $host\n")
                sb.append("║   Ports: ${ports.size} to scan\n")
                sb.append("╠══════════════════════════════════╣\n\n")

                var openCount = 0
                for (port in ports.take(2048)) {
                    try {
                        val socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress(host, port), 500)
                        socket.close()
                        openCount++
                        sb.append("  [OPEN] Port $port (${getServiceName(port)})\n")
                    } catch (_: Exception) {}
                }
                sb.append("\n║ Open ports: $openCount / ${ports.take(2048).size}\n")
                sb.append("╚══════════════════════════════════╝\n")
                sb.toString()
            }
            appendOutput(result + "\n")
        }
    }

    private fun enumerateServices() {
        val host = etHost.text.toString().trim()
        if (host.isEmpty()) { appendOutput("[!] Enter host\n"); return }

        scope.launch {
            appendOutput("[*] Enumerating services on: $host\n")
            val result = withContext(Dispatchers.IO) {
                val sb = StringBuilder("╔══════════════════════════════════╗\n")
                sb.append("║   Service Enumeration: $host\n")
                sb.append("╠══════════════════════════════════╣\n\n")

                // Check HTTP
                try {
                    val conn = java.net.URL("http://$host").openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.requestMethod = "HEAD"
                    val server = conn.getHeaderField("Server") ?: "Unknown"
                    val powered = conn.getHeaderField("X-Powered-By") ?: "Unknown"
                    sb.append("  HTTP Server: $server\n")
                    sb.append("  Powered By: $powered\n")
                    sb.append("  Status: ${conn.responseCode}\n\n")
                    conn.disconnect()
                } catch (_: Exception) {}

                // Check HTTPS
                try {
                    val conn = java.net.URL("https://$host").openConnection() as javax.net.ssl.HttpsURLConnection
                    conn.connectTimeout = 3000
                    conn.requestMethod = "HEAD"
                    sb.append("  HTTPS Status: ${conn.responseCode}\n")
                    try {
                        val certs = conn.serverCertificates
                        if (certs != null && certs.isNotEmpty()) {
                            sb.append("  SSL Certs: ${certs.size} certificate(s) found\n")
                        }
                    } catch (_: Exception) {}
                    conn.disconnect()
                } catch (_: Exception) {}

                // DNS
                try {
                    val dnsResult = ShellExecutor.executeWithTimeout("nslookup $host", 5000)
                    if (dnsResult.output.isNotEmpty()) {
                        sb.append("\n  DNS Records:\n${dnsResult.output.take(500)}\n")
                    }
                } catch (_: Exception) {}

                sb.append("\n╚══════════════════════════════════╝\n")
                sb.toString()
            }
            appendOutput(result + "\n")
        }
    }

    private fun pingSweep() {
        scope.launch {
            appendOutput("[*] Running ping sweep...\n")
            val result = withContext(Dispatchers.IO) {
                try {
                    val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val info = wifiManager.connectionInfo
                    val ipInt = info.ipAddress
                    if (ipInt == 0) return@withContext "[E] Not connected to WiFi"

                    val ip = String.format("%d.%d.%d.%d", ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
                    val subnet = ip.substringBeforeLast(".")

                    val sb = StringBuilder("╔══════════════════════════════════╗\n")
                    sb.append("║   Ping Sweep: $subnet.0/24\n")
                    sb.append("╠══════════════════════════════════╣\n\n")

                    // Fast ping sweep using shell
                    val pingResult = ShellExecutor.executeWithTimeout("for i in \$(seq 1 254); do ping -c 1 -W 1 $subnet.\$i &>/dev/null && echo '$subnet.'\$i; done", 30000)
                    if (pingResult.output.isNotEmpty()) {
                        val aliveHosts = pingResult.output.lines().filter { it.isNotBlank() }
                        for ((idx, host) in aliveHosts.withIndex()) {
                            sb.append("  [ALIVE] $host\n")
                        }
                        sb.append("\n║ Total: ${aliveHosts.size} hosts alive\n")
                    } else {
                        sb.append("  No hosts responded (may need root for raw sockets)\n")
                    }
                    sb.append("\n╚══════════════════════════════════╝\n")
                    sb.toString()
                } catch (e: Exception) {
                    "[E] Ping sweep failed: ${e.message}"
                }
            }
            appendOutput(result + "\n")
        }
    }

    private fun traceroute() {
        val host = etHost.text.toString().trim().ifEmpty { "google.com" }
        scope.launch {
            appendOutput("[*] Traceroute to: $host\n")
            val result = withContext(Dispatchers.IO) {
                ShellExecutor.executeWithTimeout("traceroute $host 2>/dev/null || ping -c 10 -t 1 $host", 15000)
            }
            val sb = StringBuilder("╔══════════════════════════════════╗\n")
            sb.append("║   Traceroute: $host\n")
            sb.append("╠══════════════════════════════════╣\n\n")
            if (result.output.isNotEmpty()) {
                sb.append(result.output.take(2000))
            } else {
                sb.append("[*] traceroute not available on this device\n")
                sb.append("[*] Try installing via Termux: pkg install traceroute\n")
            }
            sb.append("\n╚══════════════════════════════════╝\n")
            appendOutput(sb.toString() + "\n")
        }
    }

    private fun networkInfo() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val sb = StringBuilder("╔══════════════════════════════════╗\n")
                sb.append("║   Network Information           ║\n")
                sb.append("╠══════════════════════════════════╣\n\n")

                val ifconfig = ShellExecutor.execute("ifconfig 2>/dev/null || ip addr show")
                sb.append(ifconfig.output.take(2000) + "\n\n")

                val route = ShellExecutor.execute("route -n 2>/dev/null || ip route show")
                sb.append("[Routing Table]\n" + route.output.take(1000) + "\n\n")

                val dns = ShellExecutor.execute("getprop net.dns1; getprop net.dns2")
                sb.append("[DNS Servers]\n" + dns.output + "\n")

                sb.append("\n╚══════════════════════════════════╝\n")
                sb.toString()
            }
            appendOutput(result + "\n")
        }
    }

    private fun showConnections() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val sb = StringBuilder("╔══════════════════════════════════╗\n")
                sb.append("║   Active Connections            ║\n")
                sb.append("╠══════════════════════════════════╣\n\n")

                val netstat = ShellExecutor.execute("netstat -tlnp 2>/dev/null || ss -tlnp 2>/dev/null")
                sb.append("[Listening Ports]\n" + netstat.output.take(2000) + "\n\n")

                val established = ShellExecutor.execute("netstat -tnp 2>/dev/null || ss -tnp 2>/dev/null")
                sb.append("[Established Connections]\n" + established.output.take(2000) + "\n")

                sb.append("\n╚══════════════════════════════════╝\n")
                sb.toString()
            }
            appendOutput(result + "\n")
        }
    }

    private fun getServiceName(port: Int): String = when (port) {
        21 -> "FTP"; 22 -> "SSH"; 23 -> "Telnet"; 25 -> "SMTP"
        53 -> "DNS"; 80 -> "HTTP"; 110 -> "POP3"; 135 -> "MSRPC"
        139 -> "NetBIOS"; 143 -> "IMAP"; 443 -> "HTTPS"; 445 -> "SMB"
        993 -> "IMAPS"; 995 -> "POP3S"; 1433 -> "MSSQL"; 3306 -> "MySQL"
        3389 -> "RDP"; 5432 -> "PostgreSQL"; 5900 -> "VNC"
        6379 -> "Redis"; 8080 -> "HTTP-Alt"; 8443 -> "HTTPS-Alt"
        8888 -> "HTTP-Proxy"; 9090 -> "WebSocket"; 27017 -> "MongoDB"
        else -> "Unknown"
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
