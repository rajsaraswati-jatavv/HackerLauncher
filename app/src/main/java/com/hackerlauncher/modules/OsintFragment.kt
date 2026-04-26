package com.hackerlauncher.modules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.hackerlauncher.R
import com.hackerlauncher.utils.Logger
import kotlinx.coroutines.*

class OsintFragment : Fragment() {

    private lateinit var tvOsintOutput: TextView
    private lateinit var scrollView: ScrollView
    private val logger = Logger()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_osint, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvOsintOutput = view.findViewById(R.id.tvOsintOutput)
        scrollView = view.findViewById(R.id.scrollViewOsint)
        val etQuery = view.findViewById<EditText>(R.id.etOsintQuery)
        val spinnerType = view.findViewById<Spinner>(R.id.spinnerOsintType)
        val btnSearch = view.findViewById<Button>(R.id.btnOsintSearch)

        val types = listOf("Username Search", "Email Breach Check", "Phone Lookup", "Domain Recon", "IP Geolocation", "Reverse Image")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapter

        btnSearch.setOnClickListener {
            val query = etQuery.text.toString().trim()
            val type = spinnerType.selectedItemPosition
            if (query.isNotEmpty()) {
                performOsint(query, type)
            } else {
                appendOutput("[!] Enter a query first\n")
            }
        }
    }

    private fun performOsint(query: String, type: Int) {
        scope.launch {
            appendOutput("[*] Searching: $query (${getLabel(type)})\n")
            val result = withContext(Dispatchers.IO) {
                when (type) {
                    0 -> searchUsername(query)
                    1 -> checkEmailBreach(query)
                    2 -> phoneLookup(query)
                    3 -> domainRecon(query)
                    4 -> ipGeolocation(query)
                    5 -> reverseImage(query)
                    else -> "Unknown search type"
                }
            }
            appendOutput(result + "\n")
        }
    }

    private fun getLabel(type: Int) = when (type) {
        0 -> "Username"; 1 -> "Email"; 2 -> "Phone"; 3 -> "Domain"; 4 -> "IP"; 5 -> "Image"; else -> "Unknown"
    }

    private fun searchUsername(username: String): String {
        val sb = StringBuilder("═══ Username Search: $username ═══\n")
        val platforms = listOf(
            "github.com", "twitter.com", "instagram.com", "reddit.com",
            "youtube.com", "tiktok.com", "pinterest.com", "medium.com",
            "dev.to", "gitlab.com", "steamcommunity.com", "keybase.io",
            "hackerone.com", "bugcrowd.com", "about.me"
        )
        for (platform in platforms) {
            val url = "https://$platform/$username"
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true
                val code = connection.responseCode
                val status = when {
                    code in 200..299 -> "[FOUND]"
                    code == 404 -> "[NOT FOUND]"
                    code == 403 -> "[EXISTS (Forbidden)]"
                    code in 300..399 -> "[REDIRECT]"
                    else -> "[UNKNOWN ($code)]"
                }
                sb.append("  $status $platform\n")
                connection.disconnect()
            } catch (e: Exception) {
                sb.append("  [ERROR] $platform - ${e.message}\n")
            }
        }
        sb.append("═══════════════════════════════\n")
        return sb.toString()
    }

    private fun checkEmailBreach(email: String): String {
        val sb = StringBuilder("═══ Email Breach Check: $email ═══\n")
        if (!email.contains("@") || !email.contains(".")) {
            sb.append("[!] Invalid email format\n")
            return sb.toString()
        }
        try {
            // Using HaveIBeenPwned API (breachedaccount endpoint)
            val url = java.net.URL("https://haveibeenpwned.com/api/v3/breachedaccount/$email")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("hibp-api-key", "") // User must provide their own key
            conn.connectTimeout = 10000
            val code = conn.responseCode
            when (code) {
                200 -> {
                    val response = conn.inputStream.bufferedReader().readText()
                    sb.append("[!] BREACHES FOUND!\n")
                    // Parse JSON array manually
                    val breaches = response.split("},{")
                    for (breach in breaches) {
                        val name = breach.substringAfter("\"Name\":\"").substringBefore("\"")
                        val domain = breach.substringAfter("\"Domain\":\"").substringBefore("\"")
                        if (name.isNotEmpty()) {
                            sb.append("  - $name ($domain)\n")
                        }
                    }
                }
                404 -> sb.append("[+] No breaches found for this email\n")
                401 -> sb.append("[!] API key required. Set your HIBP API key in settings.\n")
                429 -> sb.append("[!] Rate limited. Try again later.\n")
                else -> sb.append("[?] Unexpected response: $code\n")
            }
            conn.disconnect()
        } catch (e: Exception) {
            sb.append("[E] Check failed: ${e.message}\n")
            sb.append("[*] Visit https://haveibeenpwned.com manually\n")
        }
        sb.append("═══════════════════════════════\n")
        return sb.toString()
    }

    private fun phoneLookup(phone: String): String {
        val sb = StringBuilder("═══ Phone Lookup: $phone ═══\n")
        try {
            val cleanPhone = phone.replace(Regex("[^+0-9]"), "")
            val url = java.net.URL("https://ip-api.com/json/?fields=status,message") // Placeholder API
            sb.append("[*] Number format: $cleanPhone\n")
            sb.append("[*] Country code: ${cleanPhone.take(2)}\n")
            sb.append("[*] For full lookup, use NumVerify or Truecaller API\n")
            sb.append("[*] Manual check: https://www.truecaller.com/search/$cleanPhone\n")
        } catch (e: Exception) {
            sb.append("[E] Lookup failed: ${e.message}\n")
        }
        sb.append("═══════════════════════════════\n")
        return sb.toString()
    }

    private fun domainRecon(domain: String): String {
        val sb = StringBuilder("═══ Domain Recon: $domain ═══\n")
        try {
            // DNS lookup
            val cmd = "nslookup $domain"
            val proc = Runtime.getRuntime().exec(cmd)
            val output = proc.inputStream.bufferedReader().readText()
            sb.append("[DNS Records]\n$output\n")

            // WHOIS
            sb.append("[WHOIS] Check: https://who.is/whois/$domain\n")

            // HTTP headers
            try {
                val url = java.net.URL("https://$domain")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 5000
                sb.append("[HTTP Headers]\n")
                sb.append("  Server: ${conn.getHeaderField("Server") ?: "N/A"}\n")
                sb.append("  X-Powered-By: ${conn.getHeaderField("X-Powered-By") ?: "N/A"}\n")
                sb.append("  Content-Type: ${conn.getHeaderField("Content-Type") ?: "N/A"}\n")
                sb.append("  Status: ${conn.responseCode}\n")
                conn.disconnect()
            } catch (e: Exception) {
                sb.append("[E] HTTP header check failed: ${e.message}\n")
            }
        } catch (e: Exception) {
            sb.append("[E] Domain recon failed: ${e.message}\n")
        }
        sb.append("═══════════════════════════════\n")
        return sb.toString()
    }

    private fun ipGeolocation(ip: String): String {
        val sb = StringBuilder("═══ IP Geolocation: $ip ═══\n")
        try {
            val url = java.net.URL("http://ip-api.com/json/$ip")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // Parse JSON manually
            val extract = { key: String -> 
                response.substringAfter("\"$key\":\"").substringBefore("\"").ifEmpty { "N/A" }
            }
            sb.append("  IP:       ${extract("query")}\n")
            sb.append("  Country:  ${extract("country")}\n")
            sb.append("  Region:   ${extract("regionName")}\n")
            sb.append("  City:     ${extract("city")}\n")
            sb.append("  ISP:      ${extract("isp")}\n")
            sb.append("  Org:      ${extract("org")}\n")
            sb.append("  Timezone: ${extract("timezone")}\n")
            sb.append("  Lat/Lon:  ${extract("lat")}, ${extract("lon")}\n")
        } catch (e: Exception) {
            sb.append("[E] Geolocation failed: ${e.message}\n")
        }
        sb.append("═══════════════════════════════\n")
        return sb.toString()
    }

    private fun reverseImage(query: String): String {
        val sb = StringBuilder("═══ Reverse Image Search ═══\n")
        sb.append("[*] Query: $query\n")
        sb.append("[*] Google: https://images.google.com/searchbyimage?image_url=$query\n")
        sb.append("[*] TinEye: https://tineye.com/search/?url=$query\n")
        sb.append("[*] Yandex: https://yandex.com/images/search?rpt=imageview&url=$query\n")
        sb.append("═══════════════════════════════\n")
        return sb.toString()
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOsintOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
}
