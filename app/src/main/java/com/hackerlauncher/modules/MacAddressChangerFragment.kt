package com.hackerlauncher.modules

import android.content.Context
import android.graphics.Typeface
import android.net.wifi.WifiManager
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
import com.hackerlauncher.utils.ShellExecutor
import kotlinx.coroutines.*
import java.net.NetworkInterface

class MacAddressChangerFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etCustomMac: EditText
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val vendorOuis = mapOf(
        "001122" to "Cisco", "AABBCC" to "Apple", "DC2B2A" to "Apple",
        "3C22FB" to "Apple", "B827EB" to "Raspberry Pi", "DCA632" to "Samsung",
        "9C3AAF" to "Samsung", "F43D7C" to "Samsung", "F8FF0A" to "Apple",
        "001A2B" to "Dell", "001CC0" to "Intel", "001B21" to "Intel",
        "0023AE" to "Dell", "00236C" to "Intel", "0024D7" to "Dell",
        "0025AA" to "Intel", "001E64" to "Hewlett Packard", "00215A" to "IBM",
        "0018FE" to "Cisco", "0019AA" to "Cisco", "001B54" to "Cisco",
        "F01898" to "Samsung", "CC3A61" to "Samsung", "5C6A3D" to "Samsung"
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
            text = "[ MAC CHANGER ]"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvTitle)

        etCustomMac = EditText(ctx).apply {
            hint = "Custom MAC (AA:BB:CC:DD:EE:FF)"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        rootLayout.addView(etCustomMac)

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

        btnRow1.addView(makeBtn("Current MAC") { showCurrentMac() })
        btnRow1.addView(makeBtn("All Ifaces") { showAllInterfaces() })
        btnRow1.addView(makeBtn("Random MAC") { randomizeMac() })
        btnRow2.addView(makeBtn("Set Custom") { setCustomMac() })
        btnRow2.addView(makeBtn("Vendor Lookup") { vendorLookup() })
        btnRow2.addView(makeBtn("Reset MAC") { resetMac() })

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
        appendOutput("║      MAC ADDRESS CHANGER        ║\n")
        appendOutput("║  MAC randomization & lookup     ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")
        appendOutput("[!] MAC changing requires ROOT access\n\n")

        return rootLayout
    }

    private fun showCurrentMac() {
        try {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║       Current MAC Address       ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val currentMac = info.macAddress ?: "Unavailable"

            appendOutput("║ WiFi MAC: $currentMac\n")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                appendOutput("║ [i] Android 6+ uses MAC randomization\n")
                appendOutput("║ [i] This may be a randomized MAC\n")
            }

            // Get real MAC via network interfaces
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val netIf = interfaces.nextElement()
                    val mac = netIf.hardwareAddress
                    if (mac != null && mac.isNotEmpty()) {
                        val macStr = mac.joinToString(":") { "%02X".format(it) }
                        val vendor = lookupVendor(macStr)
                        appendOutput("║ ${netIf.name}: $macStr ($vendor)\n")
                    }
                }
            } catch (e: Exception) {
                appendOutput("║ [E] Interface scan: ${e.message}\n")
            }

            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] MAC check failed: ${e.message}\n")
        }
    }

    private fun showAllInterfaces() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║    All Network Interfaces       ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            try {
                val interfaces = withContext(Dispatchers.IO) {
                    NetworkInterface.getNetworkInterfaces().toList()
                }
                for (netIf in interfaces) {
                    val mac = try {
                        val hw = netIf.hardwareAddress
                        if (hw != null) hw.joinToString(":") { "%02X".format(it) } else "N/A"
                    } catch (_: Exception) { "N/A" }

                    val vendor = if (mac != "N/A") lookupVendor(mac) else ""
                    appendOutput("║ ${netIf.name} (${netIf.displayName})\n")
                    appendOutput("║   MAC: $mac $vendor\n")
                    appendOutput("║   Up: ${netIf.isUp} | MTU: ${netIf.mtu}\n")
                    appendOutput("║   Virtual: ${netIf.isVirtual}\n")
                    for (addr in netIf.interfaceAddresses) {
                        appendOutput("║   IP: ${addr.address.hostAddress}/${addr.networkPrefixLength}\n")
                    }
                    appendOutput("║\n")
                }
            } catch (e: Exception) {
                appendOutput("║ [E] ${e.message}\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun randomizeMac() {
        scope.launch {
            appendOutput("[*] Generating random MAC address...\n")
            try {
                val randomMac = generateRandomMac()
                appendOutput("[+] Random MAC: $randomMac\n")
                appendOutput("[*] Attempting to set MAC (requires root)...\n")

                val result = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("su -c 'ifconfig wlan0 down; ifconfig wlan0 hw ether $randomMac; ifconfig wlan0 up'")
                }

                if (result.error.isNotEmpty()) {
                    appendOutput("[!] Root access required\n")
                    appendOutput("[*] Manual command:\n")
                    appendOutput("    su -c 'ifconfig wlan0 down'\n")
                    appendOutput("    su -c 'ifconfig wlan0 hw ether $randomMac'\n")
                    appendOutput("    su -c 'ifconfig wlan0 up'\n")
                } else {
                    appendOutput("[+] MAC address changed to $randomMac\n")
                    showCurrentMac()
                }
            } catch (e: Exception) {
                appendOutput("[E] MAC randomization failed: ${e.message}\n")
            }
        }
    }

    private fun setCustomMac() {
        val mac = etCustomMac.text.toString().trim()
        if (mac.isEmpty()) { appendOutput("[!] Enter a MAC address\n"); return }
        if (!mac.matches(Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"))) {
            appendOutput("[!] Invalid MAC format. Use AA:BB:CC:DD:EE:FF\n")
            return
        }

        scope.launch {
            appendOutput("[*] Setting MAC to $mac (requires root)...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("su -c 'ifconfig wlan0 down; ifconfig wlan0 hw ether $mac; ifconfig wlan0 up'")
                }
                if (result.error.isNotEmpty()) {
                    appendOutput("[!] Root access required\n")
                } else {
                    appendOutput("[+] MAC changed to $mac\n")
                    showCurrentMac()
                }
            } catch (e: Exception) {
                appendOutput("[E] Failed: ${e.message}\n")
            }
        }
    }

    private fun vendorLookup() {
        val mac = etCustomMac.text.toString().trim()
        if (mac.isEmpty()) {
            try {
                val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val currentMac = wifiManager.connectionInfo.macAddress ?: ""
                if (currentMac.isNotEmpty()) {
                    appendOutput("[*] Vendor for current MAC $currentMac:\n")
                    appendOutput("    ${lookupVendor(currentMac)}\n")
                }
            } catch (e: Exception) {
                appendOutput("[!] Enter a MAC address or connect to WiFi\n")
            }
        } else {
            appendOutput("[*] Vendor lookup for $mac:\n")
            appendOutput("    Local: ${lookupVendor(mac)}\n")

            scope.launch {
                try {
                    val oui = mac.replace(":", "").replace("-", "").uppercase().take(6)
                    val result = withContext(Dispatchers.IO) {
                        val url = java.net.URL("https://api.macvendors.com/$oui")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.setRequestProperty("User-Agent", "HackerLauncher/1.0")
                        if (conn.responseCode == 200) {
                            conn.inputStream.bufferedReader().readText()
                        } else "Not found"
                    }
                    appendOutput("    Online: $result\n\n")
                } catch (e: Exception) {
                    appendOutput("    Online lookup failed: ${e.message}\n")
                }
            }
        }
    }

    private fun resetMac() {
        scope.launch {
            appendOutput("[*] Resetting MAC to original (requires root)...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("su -c 'ifconfig wlan0 down; macchanger -r wlan0; ifconfig wlan0 up 2>/dev/null || ip link set wlan0 up'")
                }
                if (result.error.isNotEmpty()) {
                    appendOutput("[!] Root required. Alternatively, toggle WiFi off/on.\n")
                } else {
                    appendOutput("[+] MAC reset\n")
                    showCurrentMac()
                }
            } catch (e: Exception) {
                appendOutput("[E] Reset failed: ${e.message}\n")
            }
        }
    }

    private fun generateRandomMac(): String {
        val random = java.util.Random()
        val mac = ByteArray(6)
        random.nextBytes(mac)
        mac[0] = (mac[0].toInt() or 0x02).toByte()
        mac[0] = (mac[0].toInt() and 0xFE.inv()).toByte()
        return mac.joinToString(":") { "%02X".format(it) }
    }

    private fun lookupVendor(mac: String): String {
        val oui = mac.replace(":", "").replace("-", "").uppercase().take(6)
        return vendorOuis[oui] ?: "Unknown ($oui)"
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
