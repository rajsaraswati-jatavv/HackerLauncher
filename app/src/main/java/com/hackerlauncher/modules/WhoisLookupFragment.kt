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

class WhoisLookupFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etDomain: EditText
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
            text = "[ WHOIS LOOKUP ]"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvTitle)

        etDomain = EditText(ctx).apply {
            hint = "Domain (e.g. google.com)"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        rootLayout.addView(etDomain)

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
        btnRow.addView(makeBtn("Whois") { doWhois() })
        btnRow.addView(makeBtn("Quick Info") { quickInfo() })
        btnRow.addView(makeBtn("Name Servers") { nameServers() })
        btnRow.addView(makeBtn("DNS Records") { dnsRecords() })
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
        appendOutput("║       WHOIS LOOKUP v1.0         ║\n")
        appendOutput("║  Domain registration info       ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return rootLayout
    }

    private fun doWhois() {
        val domain = etDomain.text.toString().trim()
        if (domain.isEmpty()) { appendOutput("[!] Enter a domain name\n"); return }

        scope.launch {
            appendOutput("[*] Performing WHOIS lookup for $domain...\n")
            try {
                val whoisData = withContext(Dispatchers.IO) { fetchWhois(domain) }
                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║  WHOIS: $domain\n")
                appendOutput("╠══════════════════════════════════╣\n")

                val lines = whoisData.lines().filter { it.isNotBlank() }
                for (line in lines.take(80)) {
                    appendOutput("║ $line\n")
                }
                if (lines.size > 80) {
                    appendOutput("║ ... (${lines.size - 80} more lines)\n")
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] WHOIS failed: ${e.message}\n")
            }
        }
    }

    private fun quickInfo() {
        val domain = etDomain.text.toString().trim()
        if (domain.isEmpty()) { appendOutput("[!] Enter a domain name\n"); return }

        scope.launch {
            appendOutput("[*] Quick info for $domain...\n")
            try {
                val whoisData = withContext(Dispatchers.IO) { fetchWhois(domain) }

                val registrar = extractField(whoisData, "Registrar")
                val created = extractField(whoisData, "Creation Date") ?: extractField(whoisData, "Created Date")
                val expires = extractField(whoisData, "Registry Expiry Date") ?: extractField(whoisData, "Expiry Date")
                val updated = extractField(whoisData, "Updated Date")
                val status = extractField(whoisData, "Domain Status")
                val nameServers = extractAllFields(whoisData, "Name Server")

                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║  Quick Info: $domain\n")
                appendOutput("╠══════════════════════════════════╣\n")
                appendOutput("║ Registrar:    $registrar\n")
                appendOutput("║ Created:      $created\n")
                appendOutput("║ Expires:      $expires\n")
                appendOutput("║ Updated:      $updated\n")
                appendOutput("║ Status:       $status\n")
                if (nameServers.isNotEmpty()) {
                    appendOutput("║ Name Servers:\n")
                    for (ns in nameServers.take(4)) {
                        appendOutput("║   $ns\n")
                    }
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] Quick info failed: ${e.message}\n")
            }
        }
    }

    private fun nameServers() {
        val domain = etDomain.text.toString().trim()
        if (domain.isEmpty()) { appendOutput("[!] Enter a domain name\n"); return }

        scope.launch {
            appendOutput("[*] Looking up name servers for $domain...\n")
            try {
                val whoisData = withContext(Dispatchers.IO) { fetchWhois(domain) }
                val nsList = extractAllFields(whoisData, "Name Server")

                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║  Name Servers: $domain\n")
                appendOutput("╠══════════════════════════════════╣\n")
                if (nsList.isEmpty()) {
                    appendOutput("║ No name servers found in WHOIS\n")
                } else {
                    for ((idx, ns) in nsList.withIndex()) {
                        appendOutput("║ ${idx + 1}. $ns\n")
                        try {
                            val addresses = withContext(Dispatchers.IO) {
                                java.net.InetAddress.getAllByName(ns)
                            }
                            for (addr in addresses) {
                                appendOutput("║    -> ${addr.hostAddress}\n")
                            }
                        } catch (_: Exception) {
                            appendOutput("║    -> Could not resolve\n")
                        }
                    }
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] NS lookup failed: ${e.message}\n")
            }
        }
    }

    private fun dnsRecords() {
        val domain = etDomain.text.toString().trim()
        if (domain.isEmpty()) { appendOutput("[!] Enter a domain name\n"); return }

        scope.launch {
            appendOutput("[*] DNS Records for $domain...\n")
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║  DNS Records: $domain\n")
            appendOutput("╠══════════════════════════════════╣\n")

            try {
                val addresses = withContext(Dispatchers.IO) { java.net.InetAddress.getAllByName(domain) }
                for (addr in addresses) {
                    appendOutput("║ A:    ${addr.hostAddress}\n")
                }
            } catch (e: Exception) { appendOutput("║ A:    [Failed: ${e.message}]\n") }

            try {
                val records = withContext(Dispatchers.IO) {
                    val attrs = javax.naming.directory.InitialDirContext()
                        .getAttributes("dns:/$domain", arrayOf("MX"))
                    attrs.get("MX")?.all?.toList()?.map { it.toString() } ?: emptyList()
                }
                for (mx in records) { appendOutput("║ MX:   $mx\n") }
            } catch (_: Exception) { appendOutput("║ MX:   [Not available]\n") }

            try {
                val records = withContext(Dispatchers.IO) {
                    val attrs = javax.naming.directory.InitialDirContext()
                        .getAttributes("dns:/$domain", arrayOf("TXT"))
                    attrs.get("TXT")?.all?.toList()?.map { it.toString() } ?: emptyList()
                }
                for (txt in records) { appendOutput("║ TXT:  $txt\n") }
            } catch (_: Exception) { appendOutput("║ TXT:  [Not available]\n") }

            try {
                val records = withContext(Dispatchers.IO) {
                    val attrs = javax.naming.directory.InitialDirContext()
                        .getAttributes("dns:/$domain", arrayOf("NS"))
                    attrs.get("NS")?.all?.toList()?.map { it.toString() } ?: emptyList()
                }
                for (ns in records) { appendOutput("║ NS:   $ns\n") }
            } catch (_: Exception) { appendOutput("║ NS:   [Not available]\n") }

            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun fetchWhois(domain: String): String {
        val url = URL("https://whois.freeaitools.me/$domain")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "HackerLauncher/1.0")
        if (conn.responseCode != 200) {
            val rdapUrl = URL("https://rdap.org/domain/$domain")
            val rdapConn = rdapUrl.openConnection() as HttpURLConnection
            rdapConn.requestMethod = "GET"
            rdapConn.connectTimeout = 15000
            rdapConn.readTimeout = 15000
            rdapConn.setRequestProperty("Accept", "application/rdap+json")
            val reader = BufferedReader(InputStreamReader(rdapConn.inputStream))
            val response = reader.readText()
            reader.close()
            rdapConn.disconnect()
            return formatRdap(response)
        }
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = reader.readText()
        reader.close()
        conn.disconnect()
        return response
    }

    private fun formatRdap(json: String): String {
        val sb = StringBuilder()
        val ldhName = extractJsonField(json, "ldhName")
        sb.append("Domain Name: $ldhName\n")
        val createdMatch = Regex("\"eventDate\":\"([^\"]+)\".*\"eventAction\":\"registration\"").find(json)
        val expiresMatch = Regex("\"eventDate\":\"([^\"]+)\".*\"eventAction\":\"expiration\"").find(json)
        val updatedMatch = Regex("\"eventDate\":\"([^\"]+)\".*\"eventAction\":\"last changed\"").find(json)
        if (createdMatch != null) sb.append("Creation Date: ${createdMatch.groupValues[1]}\n")
        if (expiresMatch != null) sb.append("Expiry Date: ${expiresMatch.groupValues[1]}\n")
        if (updatedMatch != null) sb.append("Updated Date: ${updatedMatch.groupValues[1]}\n")
        return sb.toString()
    }

    private fun extractJsonField(json: String, key: String): String {
        val pattern = "\"$key\":\""
        val idx = json.indexOf(pattern)
        if (idx < 0) return "N/A"
        val start = idx + pattern.length
        val end = json.indexOf("\"", start)
        return if (end > start) json.substring(start, end) else "N/A"
    }

    private fun extractField(data: String, fieldName: String): String? {
        for (line in data.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("$fieldName:", ignoreCase = true)) {
                return trimmed.substringAfter(":").trim()
            }
        }
        return null
    }

    private fun extractAllFields(data: String, fieldName: String): List<String> {
        return data.lines()
            .map { it.trim() }
            .filter { it.startsWith("$fieldName:", ignoreCase = true) }
            .map { it.substringAfter(":").trim() }
            .distinct()
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
