package com.hackerlauncher.modules

import android.graphics.Typeface
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class SipScannerFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etSubnet: EditText
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var scanning = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()

        val rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val tvTitle = TextView(ctx).apply {
            text = "[ SIP SCANNER ]"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvTitle)

        etSubnet = EditText(ctx).apply {
            hint = "Subnet (e.g. 192.168.1)"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        rootLayout.addView(etSubnet)

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

        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(makeBtn("Scan SIP") { scanSip() })
        btnRow.addView(makeBtn("SIP Ports") { scanCommonSipPorts() })
        btnRow.addView(makeBtn("SIP Enum") { sipEnumeration() })
        btnRow.addView(makeBtn("Stop", 0xFFFF0000.toInt()) { scanning = false })
        rootLayout.addView(btnRow)

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
        appendOutput("║       SIP/VoIP SCANNER          ║\n")
        appendOutput("║  Detect SIP services on network ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return rootLayout
    }

    private fun scanSip() {
        val subnet = etSubnet.text.toString().trim()
        if (subnet.isEmpty()) { appendOutput("[!] Enter subnet (e.g. 192.168.1)\n"); return }
        if (scanning) { appendOutput("[!] Already scanning\n"); return }
        scanning = true

        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║  Scanning $subnet.0/24 for SIP...\n")
            appendOutput("╠══════════════════════════════════╣\n")

            var found = 0
            val startTime = System.currentTimeMillis()

            val results = withContext(Dispatchers.IO) {
                (1..254).map { host ->
                    async(Dispatchers.IO) {
                        if (!scanning) return@async null
                        val ip = "$subnet.$host"
                        // Try TCP on 5060
                        try {
                            val socket = java.net.Socket()
                            socket.connect(InetSocketAddress(ip, 5060), 300)
                            socket.close()
                            return@async ip to 5060
                        } catch (_: Exception) {}

                        // Try TCP on 5061 (TLS)
                        try {
                            val socket = java.net.Socket()
                            socket.connect(InetSocketAddress(ip, 5061), 300)
                            socket.close()
                            return@async ip to 5061
                        } catch (_: Exception) {}

                        // Try UDP SIP OPTIONS
                        try {
                            val sipOptions = buildSipOptions(ip)
                            val udpSocket = DatagramSocket()
                            udpSocket.soTimeout = 500
                            val packet = DatagramPacket(sipOptions, sipOptions.size, InetAddress.getByName(ip), 5060)
                            udpSocket.send(packet)

                            val buf = ByteArray(4096)
                            val response = DatagramPacket(buf, buf.size)
                            udpSocket.receive(response)
                            udpSocket.close()
                            return@async ip to 5060
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().filterNotNull()
            }

            for ((ip, port) in results) {
                found++
                val proto = if (port == 5061) "TLS" else "TCP/UDP"
                appendOutput("║ [+] SIP service found: $ip:$port ($proto)\n")
            }

            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("║ Scan complete: ${elapsed}s\n")
            appendOutput("║ SIP services found: $found\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
            scanning = false
        }
    }

    private fun scanCommonSipPorts() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Common SIP/VoIP Ports         ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val sipPorts = mapOf(
                5060 to "SIP (TCP/UDP)",
                5061 to "SIP over TLS",
                5062 to "SIP (Alt)",
                1720 to "H.323",
                1719 to "H.323 RAS",
                2427 to "MGCP",
                2727 to "MGCP Call Agent",
                4569 to "IAX2",
                5004 to "RTP",
                5005 to "RTCP",
                8443 to "SIP WebSocket",
                8080 to "SIP WebSocket (Alt)"
            )

            for ((port, desc) in sipPorts) {
                try {
                    val open = withContext(Dispatchers.IO) {
                        try {
                            val socket = java.net.Socket()
                            socket.connect(InetSocketAddress("127.0.0.1", port), 200)
                            socket.close()
                            true
                        } catch (_: Exception) { false }
                    }
                    appendOutput("║ ${port} ($desc): ${if (open) "[OPEN]" else "[closed]"}\n")
                } catch (e: Exception) {
                    appendOutput("║ $port ($desc): [error]\n")
                }
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun sipEnumeration() {
        val subnet = etSubnet.text.toString().trim()
        if (subnet.isEmpty()) { appendOutput("[!] Enter subnet\n"); return }

        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║       SIP Enumeration           ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val targets = listOf("$subnet.1", "$subnet.2", "$subnet.100", "$subnet.254")

            for (target in targets) {
                try {
                    val response = withContext(Dispatchers.IO) {
                        sendSipOptions(target)
                    }
                    if (response != null) {
                        appendOutput("║ [+] $target responded:\n")
                        for (line in response.lines().take(10)) {
                            appendOutput("║   $line\n")
                        }
                        appendOutput("║\n")
                    }
                } catch (_: Exception) {}
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun buildSipOptions(target: String): ByteArray {
        val sipMsg = "OPTIONS sip:$target SIP/2.0\r\n" +
                "Via: SIP/2.0/UDP $target:5060;branch=z9hG4bK776asdhds\r\n" +
                "Max-Forwards: 70\r\n" +
                "To: sip:$target\r\n" +
                "From: sip:scanner@$target;tag=49583\r\n" +
                "Call-ID: ${System.currentTimeMillis()}@hack_launcher\r\n" +
                "CSeq: 1 OPTIONS\r\n" +
                "Contact: sip:scanner@$target:5060\r\n" +
                "Accept: application/sdp\r\n" +
                "Content-Length: 0\r\n\r\n"
        return sipMsg.toByteArray()
    }

    private fun sendSipOptions(target: String): String? {
        return try {
            val socket = DatagramSocket()
            socket.soTimeout = 3000
            val message = buildSipOptions(target)
            val packet = DatagramPacket(message, message.size, InetAddress.getByName(target), 5060)
            socket.send(packet)

            val buf = ByteArray(4096)
            val response = DatagramPacket(buf, buf.size)
            socket.receive(response)
            socket.close()

            String(response.data, response.offset, response.length)
        } catch (_: Exception) {
            null
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
        scanning = false
        scope.cancel()
    }
}
