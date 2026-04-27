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
import com.hackerlauncher.utils.ShellExecutor
import kotlinx.coroutines.*

class DnsChangerFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etCustomDns1: EditText
    private lateinit var etCustomDns2: EditText
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val dnsPresets = mapOf(
        "Google" to Pair("8.8.8.8", "8.8.4.4"),
        "Cloudflare" to Pair("1.1.1.1", "1.0.0.1"),
        "OpenDNS" to Pair("208.67.222.222", "208.67.220.220"),
        "Quad9" to Pair("9.9.9.9", "149.112.112.112"),
        "AdGuard" to Pair("94.140.14.14", "94.140.15.15"),
        "CleanBrowsing" to Pair("185.228.168.9", "185.228.169.9"),
        "Comodo" to Pair("8.26.56.26", "8.20.247.20"),
        "Yandex" to Pair("77.88.8.8", "77.88.8.1")
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
            text = "[ DNS CHANGER ]"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvTitle)

        etCustomDns1 = EditText(ctx).apply {
            hint = "Custom DNS 1"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        etCustomDns2 = EditText(ctx).apply {
            hint = "Custom DNS 2"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        rootLayout.addView(etCustomDns1)
        rootLayout.addView(etCustomDns2)

        fun makeBtn(label: String, listener: () -> Unit) = Button(ctx).apply {
            text = label
            setTextColor(0xFF00FF00.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(2, 2, 2, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(1, 1, 1, 1)
            }
            setOnClickListener { listener() }
        }

        val btnRow1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val btnRow2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val btnRow3 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        btnRow1.addView(makeBtn("Current DNS") { showCurrentDns() })
        btnRow1.addView(makeBtn("Google") { setDns("Google") })
        btnRow1.addView(makeBtn("Cloudflare") { setDns("Cloudflare") })
        btnRow2.addView(makeBtn("OpenDNS") { setDns("OpenDNS") })
        btnRow2.addView(makeBtn("Quad9") { setDns("Quad9") })
        btnRow2.addView(makeBtn("AdGuard") { setDns("AdGuard") })
        btnRow3.addView(makeBtn("Set Custom") { setCustomDns() })
        btnRow3.addView(makeBtn("Test DNS") { testDnsResolution() })
        btnRow3.addView(makeBtn("Benchmark") { benchmarkAll() })

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
        appendOutput("║       DNS CHANGER v1.0          ║\n")
        appendOutput("║  Change & benchmark DNS servers ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return rootLayout
    }

    private fun showCurrentDns() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║       Current DNS Settings      ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            try {
                val props = listOf("net.dns1", "net.dns2", "net.wlan0.dns1", "net.wlan0.dns2", "net.rmnet0.dns1", "net.rmnet0.dns2")
                for (prop in props) {
                    val result = withContext(Dispatchers.IO) { ShellExecutor.execute("getprop $prop") }
                    val value = result.output.trim()
                    if (value.isNotEmpty()) {
                        appendOutput("║ $prop = $value\n")
                    }
                }

                // Try DhcpInfo from WifiManager
                try {
                    val wifiManager = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                    val dhcpInfo = wifiManager.dhcpInfo
                    val dns1 = intToIp(dhcpInfo.dns1)
                    val dns2 = intToIp(dhcpInfo.dns2)
                    val gateway = intToIp(dhcpInfo.gateway)
                    val server = intToIp(dhcpInfo.serverAddress)
                    appendOutput("║\n║ DHCP Info:\n")
                    appendOutput("║   DNS1: $dns1\n")
                    appendOutput("║   DNS2: $dns2\n")
                    appendOutput("║   Gateway: $gateway\n")
                    appendOutput("║   Server: $server\n")
                } catch (e: Exception) {
                    appendOutput("║ DHCP info: ${e.message}\n")
                }

                val resolvResult = withContext(Dispatchers.IO) { ShellExecutor.execute("cat /etc/resolv.conf 2>/dev/null") }
                if (resolvResult.output.trim().isNotEmpty()) {
                    appendOutput("║\n║ /etc/resolv.conf:\n")
                    for (line in resolvResult.output.lines().filter { it.startsWith("nameserver") }) {
                        appendOutput("║   $line\n")
                    }
                }
            } catch (e: Exception) {
                appendOutput("║ [E] ${e.message}\n")
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

    private fun setDns(preset: String) {
        val dns = dnsPresets[preset] ?: return
        scope.launch {
            appendOutput("[*] Setting DNS to $preset (${dns.first}, ${dns.second})...\n")
            try {
                val result1 = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("su -c 'setprop net.dns1 ${dns.first}; setprop net.dns2 ${dns.second}'")
                }
                if (result1.error.isNotEmpty()) {
                    appendOutput("[!] Root required for DNS change\n")
                    appendOutput("[*] Alternative: Settings > Private DNS > hostname\n")
                    appendOutput("[*] DoH: dns.google for Google, cloudflare-dns.com for CF\n")
                } else {
                    appendOutput("[+] DNS set to $preset\n")
                    appendOutput("║   DNS1: ${dns.first}\n")
                    appendOutput("║   DNS2: ${dns.second}\n")
                }
            } catch (e: Exception) {
                appendOutput("[E] DNS change failed: ${e.message}\n")
            }
        }
    }

    private fun setCustomDns() {
        val dns1 = etCustomDns1.text.toString().trim()
        val dns2 = etCustomDns2.text.toString().trim()
        if (dns1.isEmpty()) { appendOutput("[!] Enter DNS 1 address\n"); return }

        scope.launch {
            appendOutput("[*] Setting custom DNS: $dns1${if (dns2.isNotEmpty()) ", $dns2" else ""}...\n")
            try {
                var cmd = "su -c 'setprop net.dns1 $dns1"
                if (dns2.isNotEmpty()) cmd += "; setprop net.dns2 $dns2"
                cmd += "'"
                val result = withContext(Dispatchers.IO) { ShellExecutor.execute(cmd) }
                if (result.error.isNotEmpty()) {
                    appendOutput("[!] Root required. Use Private DNS settings instead.\n")
                } else {
                    appendOutput("[+] Custom DNS set successfully\n")
                }
            } catch (e: Exception) {
                appendOutput("[E] Failed: ${e.message}\n")
            }
        }
    }

    private fun testDnsResolution() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║       DNS Resolution Test       ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val testDomains = listOf("google.com", "facebook.com", "amazon.com", "github.com", "cloudflare.com")
            for (domain in testDomains) {
                try {
                    val start = System.currentTimeMillis()
                    val addresses = withContext(Dispatchers.IO) {
                        java.net.InetAddress.getAllByName(domain)
                    }
                    val elapsed = System.currentTimeMillis() - start
                    val ips = addresses.map { it.hostAddress }.joinToString(", ")
                    appendOutput("║ $domain -> ${elapsed}ms\n")
                    appendOutput("║   $ips\n")
                } catch (e: Exception) {
                    appendOutput("║ $domain -> FAILED: ${e.message}\n")
                }
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun benchmarkAll() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║       DNS Benchmark             ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("║ Testing resolution time...\n\n")

            val results = withContext(Dispatchers.IO) {
                dnsPresets.map { (name, pair) ->
                    async(Dispatchers.IO) {
                        val times = mutableListOf<Long>()
                        for (i in 1..3) {
                            try {
                                val start = System.currentTimeMillis()
                                java.net.InetAddress.getByName(pair.first)
                                times.add(System.currentTimeMillis() - start)
                            } catch (_: Exception) {
                                times.add(9999)
                            }
                        }
                        val avg = times.average().toLong()
                        name to avg
                    }
                }.awaitAll().sortedBy { it.second }
            }

            for ((name, avg) in results) {
                val bar = "█".repeat((avg / 10).coerceAtMost(30))
                val grade = when {
                    avg < 30 -> "FAST"
                    avg < 100 -> "GOOD"
                    avg < 300 -> "OK"
                    else -> "SLOW"
                }
                appendOutput("║ $name: ${avg}ms $bar [$grade]\n")
                appendOutput("║   DNS: ${dnsPresets[name]?.first}\n")
            }
            appendOutput("║\n║ Fastest: ${results.first().first} (${results.first().second}ms)\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
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
        scope.cancel()
    }
}
