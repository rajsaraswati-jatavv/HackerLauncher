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
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class HttpHeaderInspectorFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etUrl: EditText
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
            text = "[ HTTP HEADER INSPECTOR ]"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvTitle)

        etUrl = EditText(ctx).apply {
            hint = "URL (e.g. https://google.com)"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        rootLayout.addView(etUrl)

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
        btnRow.addView(makeBtn("Fetch Headers") { fetchHeaders() })
        btnRow.addView(makeBtn("Security") { securityAnalysis() })
        btnRow.addView(makeBtn("Server Info") { serverInfo() })
        btnRow.addView(makeBtn("Compare HTTP/S") { compareHttpHttps() })
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
        appendOutput("║    HTTP HEADER INSPECTOR v1.0   ║\n")
        appendOutput("║  Analyze HTTP response headers  ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return rootLayout
    }

    private fun normalizeUrl(urlStr: String): String {
        var url = urlStr.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url
    }

    private fun fetchHeaders() {
        val urlStr = etUrl.text.toString().trim()
        if (urlStr.isEmpty()) { appendOutput("[!] Enter a URL\n"); return }

        scope.launch {
            appendOutput("[*] Fetching headers for $urlStr...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    val url = URL(normalizeUrl(urlStr))
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.requestMethod = "GET"

                    sb.append("╔══════════════════════════════════╗\n")
                    sb.append("║   Response Headers              ║\n")
                    sb.append("╠══════════════════════════════════╣\n")
                    sb.append("║ Status: ${conn.responseCode} ${conn.responseMessage}\n")
                    sb.append("║ URL: ${conn.url}\n")
                    sb.append("║ Method: ${conn.requestMethod}\n\n")

                    val headers = conn.headerFields
                    for ((key, values) in headers) {
                        if (key != null) {
                            for (value in values) {
                                sb.append("║ $key: $value\n")
                            }
                        }
                    }

                    sb.append("\n╚══════════════════════════════════╝\n")
                    conn.disconnect()
                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] Header fetch failed: ${e.message}\n")
            }
        }
    }

    private fun securityAnalysis() {
        val urlStr = etUrl.text.toString().trim()
        if (urlStr.isEmpty()) { appendOutput("[!] Enter a URL\n"); return }

        scope.launch {
            appendOutput("[*] Running security header analysis...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    val url = URL(normalizeUrl(urlStr))
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.requestMethod = "GET"

                    sb.append("╔══════════════════════════════════╗\n")
                    sb.append("║   Security Header Analysis      ║\n")
                    sb.append("╠══════════════════════════════════╣\n")

                    val checks = listOf(
                        Triple("Strict-Transport-Security", "HSTS") { conn.getHeaderField("Strict-Transport-Security") },
                        Triple("Content-Security-Policy", "CSP") { conn.getHeaderField("Content-Security-Policy") },
                        Triple("X-Content-Type-Options", "NoSniff") { conn.getHeaderField("X-Content-Type-Options") },
                        Triple("X-Frame-Options", "FrameGuard") { conn.getHeaderField("X-Frame-Options") },
                        Triple("X-XSS-Protection", "XSS Protection") { conn.getHeaderField("X-XSS-Protection") },
                        Triple("Referrer-Policy", "Referrer Control") { conn.getHeaderField("Referrer-Policy") },
                        Triple("Permissions-Policy", "Feature Policy") { conn.getHeaderField("Permissions-Policy") },
                        Triple("Cross-Origin-Opener-Policy", "COOP") { conn.getHeaderField("Cross-Origin-Opener-Policy") },
                        Triple("Cross-Origin-Resource-Policy", "CORP") { conn.getHeaderField("Cross-Origin-Resource-Policy") },
                        Triple("X-Request-ID", "Request Tracking") { conn.getHeaderField("X-Request-ID") }
                    )

                    var score = 0
                    for ((header, label, getter) in checks) {
                        val value = getter()
                        if (value != null) {
                            score++
                            sb.append("║ [+] $label: PRESENT\n")
                            sb.append("║     $header: ${value.take(50)}\n")

                            when (header) {
                                "Strict-Transport-Security" -> {
                                    val maxAge = Regex("max-age=(\\d+)").find(value)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                                    val includeSubDomains = value.contains("includeSubDomains")
                                    val preload = value.contains("preload")
                                    sb.append("║     Max-Age: ${maxAge / 86400} days\n")
                                    sb.append("║     IncludeSubDomains: $includeSubDomains\n")
                                    sb.append("║     Preload: $preload\n")
                                }
                                "Content-Security-Policy" -> {
                                    val hasUnsafeInline = value.contains("unsafe-inline")
                                    val hasUnsafeEval = value.contains("unsafe-eval")
                                    if (hasUnsafeInline) sb.append("║     [!] Contains unsafe-inline\n")
                                    if (hasUnsafeEval) sb.append("║     [!] Contains unsafe-eval\n")
                                }
                                "X-Frame-Options" -> {
                                    val secure = value.equals("DENY", ignoreCase = true) || value.equals("SAMEORIGIN", ignoreCase = true)
                                    sb.append("║     Secure: $secure\n")
                                }
                            }
                        } else {
                            sb.append("║ [-] $label: MISSING\n")
                            when (header) {
                                "Strict-Transport-Security" -> sb.append("║     [!] Vulnerable to protocol downgrade\n")
                                "Content-Security-Policy" -> sb.append("║     [!] No XSS protection via CSP\n")
                                "X-Frame-Options" -> sb.append("║     [!] Vulnerable to clickjacking\n")
                                "X-Content-Type-Options" -> sb.append("║     [!] MIME type sniffing possible\n")
                            }
                        }
                    }

                    sb.append("║\n║ Score: $score/${checks.size}\n")
                    val grade = when {
                        score >= 9 -> "A+ (Excellent)"
                        score >= 7 -> "A (Good)"
                        score >= 5 -> "B (Fair)"
                        score >= 3 -> "C (Poor)"
                        score >= 1 -> "D (Bad)"
                        else -> "F (Critical)"
                    }
                    sb.append("║ Grade: $grade\n")
                    sb.append("╚══════════════════════════════════╝\n")

                    conn.disconnect()
                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] Security analysis failed: ${e.message}\n")
            }
        }
    }

    private fun serverInfo() {
        val urlStr = etUrl.text.toString().trim()
        if (urlStr.isEmpty()) { appendOutput("[!] Enter a URL\n"); return }

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    val url = URL(normalizeUrl(urlStr))
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.requestMethod = "HEAD"

                    sb.append("╔══════════════════════════════════╗\n")
                    sb.append("║   Server Information            ║\n")
                    sb.append("╠══════════════════════════════════╣\n")

                    val server = conn.getHeaderField("Server")
                    sb.append("║ Server:     ${server ?: "Not disclosed"}\n")

                    val poweredBy = conn.getHeaderField("X-Powered-By")
                    sb.append("║ Powered-By: ${poweredBy ?: "Not disclosed"}\n")
                    if (poweredBy != null) sb.append("║ [!] X-Powered-By reveals tech stack\n")

                    val xAspNet = conn.getHeaderField("X-AspNet-Version")
                    if (xAspNet != null) sb.append("║ ASP.NET:    $xAspNet [!] Info leak\n")

                    val xGenerator = conn.getHeaderField("X-Generator")
                    if (xGenerator != null) sb.append("║ Generator:  $xGenerator [!] Info leak\n")

                    val contentType = conn.getHeaderField("Content-Type")
                    sb.append("║ Content:    $contentType\n")

                    val contentEncoding = conn.getHeaderField("Content-Encoding")
                    if (contentEncoding != null) sb.append("║ Encoding:   $contentEncoding\n")

                    val cacheControl = conn.getHeaderField("Cache-Control")
                    sb.append("║ Cache:      ${cacheControl ?: "Not set"}\n")

                    val cookies = conn.getHeaderField("Set-Cookie")
                    if (cookies != null) {
                        sb.append("║ Cookies:    Set\n")
                        val hasSecure = cookies.contains("Secure", ignoreCase = true)
                        val hasHttpOnly = cookies.contains("HttpOnly", ignoreCase = true)
                        val hasSameSite = cookies.contains("SameSite", ignoreCase = true)
                        sb.append("║   Secure:    $hasSecure\n")
                        sb.append("║   HttpOnly:  $hasHttpOnly\n")
                        sb.append("║   SameSite:  $hasSameSite\n")
                        if (!hasSecure) sb.append("║   [!] Cookie missing Secure flag\n")
                        if (!hasHttpOnly) sb.append("║   [!] Cookie missing HttpOnly flag\n")
                    }

                    sb.append("║\n║ Fingerprint:\n")
                    if (server != null) {
                        when {
                            server.contains("nginx", ignoreCase = true) -> sb.append("║   Web Server: Nginx\n")
                            server.contains("apache", ignoreCase = true) -> sb.append("║   Web Server: Apache\n")
                            server.contains("iis", ignoreCase = true) -> sb.append("║   Web Server: IIS\n")
                            server.contains("caddy", ignoreCase = true) -> sb.append("║   Web Server: Caddy\n")
                            server.contains("cloudflare", ignoreCase = true) -> sb.append("║   CDN: Cloudflare\n")
                            server.contains("gws", ignoreCase = true) -> sb.append("║   Web Server: Google\n")
                            else -> sb.append("║   Web Server: $server\n")
                        }
                    }

                    sb.append("╚══════════════════════════════════╝\n")
                    conn.disconnect()
                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] Server info failed: ${e.message}\n")
            }
        }
    }

    private fun compareHttpHttps() {
        val urlStr = etUrl.text.toString().trim()
        if (urlStr.isEmpty()) { appendOutput("[!] Enter a URL\n"); return }

        scope.launch {
            appendOutput("[*] Comparing HTTP vs HTTPS headers...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    var host = urlStr.trim().removePrefix("https://").removePrefix("http://").split("/")[0]

                    sb.append("╔══════════════════════════════════╗\n")
                    sb.append("║   HTTP vs HTTPS Comparison      ║\n")
                    sb.append("╠══════════════════════════════════╣\n")

                    try {
                        val httpUrl = URL("http://$host")
                        val httpConn = httpUrl.openConnection() as HttpURLConnection
                        httpConn.connectTimeout = 8000
                        httpConn.readTimeout = 8000
                        httpConn.requestMethod = "HEAD"
                        sb.append("║ HTTP Status:  ${httpConn.responseCode}\n")
                        val hsts = httpConn.getHeaderField("Strict-Transport-Security")
                        sb.append("║ HTTP HSTS:    ${hsts ?: "Not set"}\n")
                        httpConn.disconnect()
                    } catch (e: Exception) {
                        sb.append("║ HTTP:  Connection failed (${e.message})\n")
                    }

                    try {
                        val httpsUrl = URL("https://$host")
                        val httpsConn = httpsUrl.openConnection() as javax.net.ssl.HttpsURLConnection
                        httpsConn.connectTimeout = 8000
                        httpsConn.readTimeout = 8000
                        httpsConn.requestMethod = "HEAD"
                        sb.append("║ HTTPS Status: ${httpsConn.responseCode}\n")
                        val hsts = httpsConn.getHeaderField("Strict-Transport-Security")
                        sb.append("║ HTTPS HSTS:   ${hsts ?: "Not set"}\n")
                        val sslSess: javax.net.ssl.SSLSession? = try { httpsConn.session } catch (_: Exception) { null }
                        sb.append("║ TLS Protocol: ${sslSess?.protocol ?: "N/A"}\n")
                        sb.append("║ Cipher:       ${sslSess?.cipherSuite ?: "N/A"}\n")
                        httpsConn.disconnect()
                    } catch (e: Exception) {
                        sb.append("║ HTTPS: Connection failed (${e.message})\n")
                    }

                    sb.append("╚══════════════════════════════════╝\n")
                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] Comparison failed: ${e.message}\n")
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
        scope.cancel()
    }
}
