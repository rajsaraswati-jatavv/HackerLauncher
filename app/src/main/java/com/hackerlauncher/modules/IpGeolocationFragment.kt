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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class IpGeolocationFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etIp: EditText
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()

        val rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val tvTitle = TextView(ctx).apply {
            text = "[ IP GEOLOCATION ]"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvTitle)

        etIp = EditText(ctx).apply {
            hint = "IP Address (leave empty for your IP)"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        rootLayout.addView(etIp)

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

        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(makeBtn("My IP Info") { lookupMyIp() })
        btnRow.addView(makeBtn("Lookup IP") { lookupIp() })
        btnRow.addView(makeBtn("External IP") { getExternalIp() })
        btnRow.addView(makeBtn("Map Link") { generateMapLink() })
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
        appendOutput("║     IP GEOLOCATION v1.0         ║\n")
        appendOutput("║  IP lookup & geolocation data   ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return rootLayout
    }

    private fun lookupMyIp() {
        scope.launch {
            appendOutput("[*] Looking up your IP geolocation...\n")
            try {
                val json = withContext(Dispatchers.IO) { fetchJson("http://ip-api.com/json/") }
                parseAndDisplay(json)
            } catch (e: Exception) {
                appendOutput("[E] Lookup failed: ${e.message}\n")
                try {
                    appendOutput("[*] Trying fallback API...\n")
                    val json = withContext(Dispatchers.IO) { fetchJson("https://ipapi.co/json/") }
                    parseAndDisplayFallback(json)
                } catch (e2: Exception) {
                    appendOutput("[E] Fallback also failed: ${e2.message}\n")
                }
            }
        }
    }

    private fun lookupIp() {
        val ip = etIp.text.toString().trim()
        if (ip.isEmpty()) { lookupMyIp(); return }
        scope.launch {
            appendOutput("[*] Looking up $ip...\n")
            try {
                val json = withContext(Dispatchers.IO) { fetchJson("http://ip-api.com/json/$ip") }
                parseAndDisplay(json)
            } catch (e: Exception) {
                appendOutput("[E] Lookup failed: ${e.message}\n")
            }
        }
    }

    private fun getExternalIp() {
        scope.launch {
            appendOutput("[*] Getting external IP...\n")
            try {
                val ip = withContext(Dispatchers.IO) {
                    val services = listOf("https://api.ipify.org", "https://ifconfig.me/ip", "https://icanhazip.com")
                    for (service in services) {
                        try {
                            val result = fetchPlainText(service)
                            if (result.isNotBlank()) return@withContext result.trim()
                        } catch (_: Exception) { continue }
                    }
                    ""
                }
                if (ip.isNotEmpty()) {
                    appendOutput("╔══════════════════════════════════╗\n")
                    appendOutput("║ External IP: $ip\n")
                    appendOutput("╚══════════════════════════════════╝\n\n")
                } else {
                    appendOutput("[!] Could not determine external IP\n")
                }
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun generateMapLink() {
        val lat = lastLat
        val lon = lastLon
        if (lat != null && lon != null) {
            val gmapsLink = "https://www.google.com/maps?q=$lat,$lon"
            val osmLink = "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=12/$lat/$lon"
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║ Google Maps:\n║ $gmapsLink\n")
            appendOutput("║ OpenStreetMap:\n║ $osmLink\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
        } else {
            appendOutput("[!] Look up an IP first to get coordinates\n")
        }
    }

    private fun parseAndDisplay(json: String) {
        try {
            val query = extractJson(json, "query")
            val status = extractJson(json, "status")
            val country = extractJson(json, "country")
            val countryCode = extractJson(json, "countryCode")
            val region = extractJson(json, "regionName")
            val city = extractJson(json, "city")
            val zip = extractJson(json, "zip")
            val lat = extractJson(json, "lat").toDoubleOrNull()
            val lon = extractJson(json, "lon").toDoubleOrNull()
            val timezone = extractJson(json, "timezone")
            val isp = extractJson(json, "isp")
            val org = extractJson(json, "org")
            val asInfo = extractJson(json, "as")

            lastLat = lat
            lastLon = lon

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║      IP Geolocation Result      ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("║ IP:        $query\n")
            appendOutput("║ Status:    $status\n")
            appendOutput("║ Country:   $country ($countryCode)\n")
            appendOutput("║ Region:    $region\n")
            appendOutput("║ City:      $city\n")
            appendOutput("║ ZIP:       $zip\n")
            appendOutput("║ Lat/Lon:   $lat / $lon\n")
            appendOutput("║ Timezone:  $timezone\n")
            appendOutput("║ ISP:       $isp\n")
            appendOutput("║ Org:       $org\n")
            appendOutput("║ AS:        $asInfo\n")
            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("║ Map: google.com/maps?q=$lat,$lon\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Parse error: ${e.message}\n")
            appendOutput("[D] Raw: ${json.take(500)}\n")
        }
    }

    private fun parseAndDisplayFallback(json: String) {
        try {
            val ip = extractJson(json, "ip")
            val city = extractJson(json, "city")
            val region = extractJson(json, "region")
            val country = extractJson(json, "country_name")
            val lat = extractJson(json, "latitude").toDoubleOrNull()
            val lon = extractJson(json, "longitude").toDoubleOrNull()
            val timezone = extractJson(json, "timezone")
            val isp = extractJson(json, "org")

            lastLat = lat
            lastLon = lon

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║ IP: $ip | $city, $region, $country\n")
            appendOutput("║ Lat/Lon: $lat / $lon\n")
            appendOutput("║ Timezone: $timezone | ISP: $isp\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Parse error: ${e.message}\n")
        }
    }

    private fun extractJson(json: String, key: String): String {
        val patterns = listOf("\"$key\":\"", "\"$key\": \"")
        for (pattern in patterns) {
            val startIdx = json.indexOf(pattern)
            if (startIdx >= 0) {
                val valueStart = startIdx + pattern.length
                val valueEnd = json.indexOf("\"", valueStart)
                if (valueEnd > valueStart) return json.substring(valueStart, valueEnd)
            }
        }
        val numPattern = "\"$key\":"
        val numStart = json.indexOf(numPattern)
        if (numStart >= 0) {
            val valueStart = numStart + numPattern.length
            val valueEnd = json.indexOfAny(charArrayOf(',', '}', '\n'), valueStart)
            if (valueEnd > valueStart) return json.substring(valueStart, valueEnd).trim()
        }
        return "N/A"
    }

    private fun fetchJson(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "HackerLauncher/1.0")
        val responseCode = conn.responseCode
        if (responseCode != 200) throw Exception("HTTP $responseCode")
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = reader.readText()
        reader.close()
        conn.disconnect()
        return response
    }

    private fun fetchPlainText(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "curl/7.64.1")
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = reader.readText()
        reader.close()
        conn.disconnect()
        return response
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
