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
import java.net.URLEncoder
import java.net.URLDecoder

class WebTestFragment : Fragment() {

    private lateinit var tvWebOutput: TextView
    private lateinit var scrollView: ScrollView
    private val logger = Logger
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_webtest, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvWebOutput = view.findViewById(R.id.tvWebOutput)
        scrollView = view.findViewById(R.id.scrollViewWeb)
        val etUrl = view.findViewById<EditText>(R.id.etWebUrl)
        val spinnerOp = view.findViewById<Spinner>(R.id.spinnerWebOp)
        val btnGo = view.findViewById<Button>(R.id.btnWebGo)

        val ops = listOf(
            "HTTP GET", "HTTP POST", "HTTP Headers", "URL Encode", "URL Decode",
            "SQLi Payloads", "XSS Payloads", "Directory Brute", "View Source", "Status Check"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, ops)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerOp.adapter = adapter

        btnGo.setOnClickListener {
            val url = etUrl.text.toString().trim()
            val op = spinnerOp.selectedItemPosition
            if (url.isNotEmpty() || op in 4..6) {
                processRequest(url, op)
            } else {
                appendOutput("[!] Enter a URL first\n")
            }
        }
    }

    private fun processRequest(url: String, op: Int) {
        scope.launch {
            appendOutput("[*] Processing...\n")
            val result = withContext(Dispatchers.IO) {
                when (op) {
                    0 -> httpGet(url)
                    1 -> httpPost(url)
                    2 -> httpHeaders(url)
                    3 -> urlEncode(url)
                    4 -> urlDecode(url)
                    5 -> sqliPayloads()
                    6 -> xssPayloads()
                    7 -> directoryBrute(url)
                    8 -> viewSource(url)
                    9 -> statusCheck(url)
                    else -> "Unknown operation"
                }
            }
            appendOutput(result + "\n")
        }
    }

    private fun httpGet(url: String): String {
        val sb = StringBuilder("═══ HTTP GET: $url ═══\n")
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "HackerLauncher/1.0")

            val code = conn.responseCode
            sb.append("Status: $code ${conn.responseMessage}\n\n")
            sb.append("[Response Headers]\n")
            for ((key, value) in conn.headerFields) {
                if (key != null) sb.append("  $key: $value\n")
            }

            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText().take(2000) }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText().take(500) } ?: "No body"
            }
            sb.append("\n[Response Body (truncated)]\n$body\n")
            conn.disconnect()
        } catch (e: Exception) {
            sb.append("[E] Request failed: ${e.message}\n")
        }
        sb.append("════════════════════════\n")
        return sb.toString()
    }

    private fun httpPost(url: String): String {
        val sb = StringBuilder("═══ HTTP POST: $url ═══\n")
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.setRequestProperty("User-Agent", "HackerLauncher/1.0")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            val params = "test=hello&data=hackerlauncher"
            conn.outputStream.write(params.toByteArray())

            val code = conn.responseCode
            sb.append("Status: $code ${conn.responseMessage}\n")
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText().take(2000) }
            } else "Error response"
            sb.append("Body: $body\n")
            conn.disconnect()
        } catch (e: Exception) {
            sb.append("[E] POST failed: ${e.message}\n")
        }
        sb.append("════════════════════════\n")
        return sb.toString()
    }

    private fun httpHeaders(url: String): String {
        val sb = StringBuilder("═══ HTTP Headers: $url ═══\n")
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 8000
            for ((key, value) in conn.headerFields) {
                if (key != null) sb.append("  $key: $value\n")
            }
            sb.append("\nSecurity Headers Check:\n")
            val securityHeaders = listOf(
                "X-Frame-Options", "X-Content-Type-Options", "X-XSS-Protection",
                "Strict-Transport-Security", "Content-Security-Policy",
                "Referrer-Policy", "Permissions-Policy"
            )
            for (header in securityHeaders) {
                val present = conn.getHeaderField(header) != null
                sb.append("  ${if (present) "[+]" else "[-]"} $header\n")
            }
            conn.disconnect()
        } catch (e: Exception) {
            sb.append("[E] Header check failed: ${e.message}\n")
        }
        sb.append("════════════════════════\n")
        return sb.toString()
    }

    private fun urlEncode(text: String): String {
        val encoded = URLEncoder.encode(text, "UTF-8")
        return "═══ URL Encode ═══\nInput:   $text\nEncoded: $encoded\n════════════════════════"
    }

    private fun urlDecode(text: String): String {
        val decoded = URLDecoder.decode(text, "UTF-8")
        return "═══ URL Decode ═══\nInput:   $text\nDecoded: $decoded\n════════════════════════"
    }

    private fun sqliPayloads(): String {
        val payloads = listOf(
            "' OR '1'='1", "' OR '1'='1' --", "' UNION SELECT NULL--",
            "' UNION SELECT NULL,NULL--", "1; DROP TABLE users--",
            "' AND 1=1--", "' AND 1=2--", "admin'--",
            "1' ORDER BY 1--", "1' ORDER BY 10--",
            "' UNION SELECT username,password FROM users--",
            "1 AND (SELECT * FROM (SELECT(SLEEP(5)))a)",
            "1' AND EXTRACTVALUE(1,CONCAT(0x7e,VERSION()))--"
        )
        val sb = StringBuilder("═══ SQLi Demo Payloads ═══\n")
        sb.append("[!] For educational/testing on YOUR OWN systems only\n\n")
        payloads.forEach { sb.append("  $it\n") }
        sb.append("════════════════════════\n")
        return sb.toString()
    }

    private fun xssPayloads(): String {
        val payloads = listOf(
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "<svg/onload=alert('XSS')>",
            "javascript:alert('XSS')",
            "\"><script>alert('XSS')</script>",
            "'-alert('XSS')-'",
            "<body onload=alert('XSS')>",
            "<input onfocus=alert('XSS') autofocus>",
            "<marquee onstart=alert('XSS')>",
            "<details open ontoggle=alert('XSS')>"
        )
        val sb = StringBuilder("═══ XSS Demo Payloads ═══\n")
        sb.append("[!] For educational/testing on YOUR OWN systems only\n\n")
        payloads.forEach { sb.append("  $it\n") }
        sb.append("════════════════════════\n")
        return sb.toString()
    }

    private fun directoryBrute(baseUrl: String): String {
        val sb = StringBuilder("═══ Directory Brute: $baseUrl ═══\n")
        val dirs = listOf(
            "admin", "login", "dashboard", "api", "config",
            "backup", "uploads", "images", "css", "js",
            ".git", ".env", "wp-admin", "phpmyadmin",
            "robots.txt", "sitemap.xml", ".htaccess",
            "server-status", "debug", "test"
        )
        val cleanUrl = baseUrl.trimEnd('/')
        for (dir in dirs) {
            try {
                val url = "$cleanUrl/$dir"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val code = conn.responseCode
                val status = when {
                    code in 200..299 -> "[${code}] FOUND"
                    code == 403 -> "[403] FORBIDDEN"
                    code == 301 || code == 302 -> "[${code}] REDIRECT"
                    else -> "[${code}]"
                }
                sb.append("  $status /$dir\n")
                conn.disconnect()
            } catch (_: Exception) {
                sb.append("  [ERR] /$dir\n")
            }
        }
        sb.append("════════════════════════\n")
        return sb.toString()
    }

    private fun viewSource(url: String): String {
        val sb = StringBuilder("═══ View Source: $url ═══\n")
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.setRequestProperty("User-Agent", "HackerLauncher/1.0")
            val source = conn.inputStream.bufferedReader().use { it.readText().take(3000) }
            sb.append(source + "\n")
            conn.disconnect()
        } catch (e: Exception) {
            sb.append("[E] Failed: ${e.message}\n")
        }
        sb.append("════════════════════════\n")
        return sb.toString()
    }

    private fun statusCheck(url: String): String {
        val sb = StringBuilder("═══ Status Check: $url ═══\n")
        try {
            val start = System.currentTimeMillis()
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 8000
            val code = conn.responseCode
            val time = System.currentTimeMillis() - start
            sb.append("  Status: $code ${conn.responseMessage}\n")
            sb.append("  Time:   ${time}ms\n")
            sb.append("  Server: ${conn.getHeaderField("Server") ?: "N/A"}\n")
            conn.disconnect()
        } catch (e: Exception) {
            sb.append("[E] Check failed: ${e.message}\n")
        }
        sb.append("════════════════════════\n")
        return sb.toString()
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvWebOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
}
