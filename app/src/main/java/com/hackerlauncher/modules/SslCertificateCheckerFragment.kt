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
import java.net.URL
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SslCertificateCheckerFragment : Fragment() {

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
            text = "[ SSL CERT CHECKER ]"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvTitle)

        etUrl = EditText(ctx).apply {
            hint = "URL (e.g. google.com)"
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
        btnRow.addView(makeBtn("Check SSL") { checkSsl() })
        btnRow.addView(makeBtn("Cert Chain") { showCertChain() })
        btnRow.addView(makeBtn("Protocols") { checkProtocols() })
        btnRow.addView(makeBtn("Security") { checkSecurityHeaders() })
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
        appendOutput("║     SSL CERTIFICATE CHECKER     ║\n")
        appendOutput("║  Verify SSL/TLS configuration   ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return rootLayout
    }

    private fun normalizeUrl(host: String): String {
        var url = host.trim()
        if (url.startsWith("https://")) return url
        if (url.startsWith("http://")) url = url.substring(7)
        return "https://$url"
    }

    private fun checkSsl() {
        val urlStr = etUrl.text.toString().trim()
        if (urlStr.isEmpty()) { appendOutput("[!] Enter a URL\n"); return }

        scope.launch {
            appendOutput("[*] Checking SSL certificate for $urlStr...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    val url = URL(normalizeUrl(urlStr))
                    val conn = url.openConnection() as HttpsURLConnection

                    try {
                        conn.connectTimeout = 10000
                        conn.readTimeout = 10000
                        conn.connect()

                        val certs = conn.serverCertificates
                        val session = (conn as? HttpsURLConnection)?.sslSession

                        sb.append("╔══════════════════════════════════╗\n")
                        sb.append("║   SSL Certificate Report        ║\n")
                        sb.append("╠══════════════════════════════════╣\n")

                        sb.append("║ Protocol:   ${session?.protocol}\n")
                        sb.append("║ Cipher:     ${session?.cipherSuite}\n")
                        sb.append("║ Host:       ${session?.peerHost}\n")
                        sb.append("║ Port:       ${session?.peerPort}\n")
                        sb.append("║ Valid:      ${session?.isValid}\n\n")

                        for ((idx, cert) in certs.withIndex()) {
                            if (cert is X509Certificate) {
                                val type = when (idx) {
                                    0 -> "SERVER"
                                    1 -> "INTERMEDIATE"
                                    else -> "CHAIN #$idx"
                                }
                                sb.append("║ [$type Certificate]\n")
                                sb.append("║   Subject:  ${cert.subjectX500Principal}\n")
                                sb.append("║   Issuer:   ${cert.issuerX500Principal}\n")
                                sb.append("║   Serial:   ${cert.serialNumber}\n")
                                sb.append("║   Not Before: ${cert.notBefore}\n")
                                sb.append("║   Not After:  ${cert.notAfter}\n")

                                val daysLeft = TimeUnit.MILLISECONDS.toDays(
                                    cert.notAfter.time - System.currentTimeMillis()
                                )
                                val expiryStatus = when {
                                    daysLeft < 0 -> "[EXPIRED ${-daysLeft}d ago]"
                                    daysLeft < 7 -> "[WARNING: ${daysLeft}d remaining!]"
                                    daysLeft < 30 -> "[CAUTION: ${daysLeft}d remaining]"
                                    else -> "[OK: ${daysLeft}d remaining]"
                                }
                                sb.append("║   Expiry:   $expiryStatus\n")

                                if (daysLeft in 1..30) {
                                    sb.append("║   [!] CERTIFICATE EXPIRING WITHIN 30 DAYS!\n")
                                }

                                // SHA-256 fingerprint
                                try {
                                    val md = MessageDigest.getInstance("SHA-256")
                                    val digest = md.digest(cert.encoded)
                                    val fingerprint = digest.joinToString(":") { "%02X".format(it) }
                                    sb.append("║   SHA-256:  $fingerprint\n")
                                } catch (_: Exception) {
                                    sb.append("║   SHA-256:  [Error computing]\n")
                                }

                                val sigAlg = cert.sigAlgName
                                val sigStrength = when {
                                    sigAlg.contains("SHA256") || sigAlg.contains("SHA384") || sigAlg.contains("SHA512") -> "STRONG"
                                    sigAlg.contains("SHA1") -> "WEAK (SHA-1)"
                                    sigAlg.contains("MD5") -> "BROKEN (MD5)"
                                    else -> "UNKNOWN"
                                }
                                sb.append("║   Sig Alg:  $sigAlg [$sigStrength]\n")
                                sb.append("║   Version:  ${cert.version}\n")
                                sb.append("║   Key Type: ${cert.publicKey.algorithm} (${cert.sigAlgName})\n\n")
                            }
                        }

                        sb.append("╚══════════════════════════════════╝\n")
                    } finally {
                        conn.disconnect()
                    }
                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] SSL check failed: ${e.message}\n")
                if (e.message?.contains("SSL") == true || e.message?.contains("cert") == true) {
                    appendOutput("[!] SSL Handshake failed - certificate may be invalid or self-signed\n")
                }
            }
        }
    }

    private fun showCertChain() {
        val urlStr = etUrl.text.toString().trim()
        if (urlStr.isEmpty()) { appendOutput("[!] Enter a URL\n"); return }

        scope.launch {
            appendOutput("[*] Retrieving certificate chain for $urlStr...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    val url = URL(normalizeUrl(urlStr))
                    val conn = url.openConnection() as HttpsURLConnection
                    try {
                        conn.connectTimeout = 10000
                        conn.readTimeout = 10000
                        conn.connect()
                        val certs = conn.serverCertificates

                        sb.append("╔══════════════════════════════════╗\n")
                        sb.append("║   Certificate Chain             ║\n")
                        sb.append("╠══════════════════════════════════╣\n")

                        for ((idx, cert) in certs.withIndex()) {
                            if (cert is X509Certificate) {
                                sb.append("║ Level $idx:\n")
                                sb.append("║   CN: ${extractCN(cert.subjectX500Principal.name)}\n")
                                sb.append("║   Issuer CN: ${extractCN(cert.issuerX500Principal.name)}\n")
                                sb.append("║   Valid: ${cert.notBefore} - ${cert.notAfter}\n")

                                val sans = cert.subjectAlternativeNames
                                if (sans != null) {
                                    sb.append("║   SANs:\n")
                                    for (san in sans) {
                                        if (san.size >= 2) sb.append("║     ${san[0]}: ${san[1]}\n")
                                    }
                                }
                                sb.append("║\n")
                            }
                        }
                        sb.append("║ Chain Depth: ${certs.size}\n")
                        sb.append("╚══════════════════════════════════╝\n")
                    } finally {
                        conn.disconnect()
                    }
                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] Chain retrieval failed: ${e.message}\n")
            }
        }
    }

    private fun checkProtocols() {
        val urlStr = etUrl.text.toString().trim()
        if (urlStr.isEmpty()) { appendOutput("[!] Enter a URL\n"); return }

        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Supported Protocols Check     ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val protocols = listOf("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3")
            val host = urlStr.trim().removePrefix("https://").removePrefix("http://").split("/")[0]

            for (protocol in protocols) {
                try {
                    val supported = withContext(Dispatchers.IO) {
                        try {
                            val sslContext = SSLContext.getInstance(protocol)
                            sslContext.init(null, arrayOf<TrustManager>(object : X509TrustManager {
                                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                            }), null)
                            val factory = sslContext.socketFactory
                            val socket = factory.createSocket(host, 443) as javax.net.ssl.SSLSocket
                            socket.enabledProtocols = arrayOf(protocol)
                            socket.startHandshake()
                            socket.close()
                            true
                        } catch (e: Exception) { false }
                    }

                    val status = if (supported) "[+]" else "[-]"
                    val security = when (protocol) {
                        "TLSv1.3" -> "BEST"
                        "TLSv1.2" -> "GOOD"
                        "TLSv1.1" -> "WEAK"
                        "TLSv1" -> "INSECURE"
                        else -> "UNKNOWN"
                    }
                    appendOutput("║ $status $protocol: ${if (supported) "SUPPORTED" else "NOT SUPPORTED"} [$security]\n")
                } catch (e: Exception) {
                    appendOutput("║ [-] $protocol: ERROR (${e.message})\n")
                }
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun checkSecurityHeaders() {
        val urlStr = etUrl.text.toString().trim()
        if (urlStr.isEmpty()) { appendOutput("[!] Enter a URL\n"); return }

        scope.launch {
            appendOutput("[*] Checking security headers for $urlStr...\n")
            try {
                val result = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    val url = URL(normalizeUrl(urlStr))
                    val conn = url.openConnection() as HttpsURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.requestMethod = "GET"

                    sb.append("╔══════════════════════════════════╗\n")
                    sb.append("║   Security Headers Analysis     ║\n")
                    sb.append("╠══════════════════════════════════╣\n")

                    val securityHeaders = mapOf(
                        "Strict-Transport-Security" to "HSTS - Forces HTTPS",
                        "Content-Security-Policy" to "CSP - Prevents XSS",
                        "X-Content-Type-Options" to "Prevents MIME sniffing",
                        "X-Frame-Options" to "Prevents clickjacking",
                        "X-XSS-Protection" to "XSS filter",
                        "Referrer-Policy" to "Controls referrer info",
                        "Permissions-Policy" to "Controls browser features",
                        "Cross-Origin-Opener-Policy" to "COOP - Isolation",
                        "Cross-Origin-Resource-Policy" to "CORP - Resource isolation"
                    )

                    var score = 0
                    for ((header, desc) in securityHeaders) {
                        val value = conn.getHeaderField(header)
                        if (value != null) {
                            sb.append("║ [+] $header\n")
                            sb.append("║     $desc\n")
                            sb.append("║     Value: ${value.take(60)}\n")
                            score++
                        } else {
                            sb.append("║ [-] $header: MISSING\n")
                            sb.append("║     $desc\n")
                        }
                    }

                    sb.append("║\n║ Security Score: $score/${securityHeaders.size}\n")
                    val grade = when {
                        score >= 8 -> "A+"
                        score >= 6 -> "A"
                        score >= 5 -> "B"
                        score >= 3 -> "C"
                        score >= 1 -> "D"
                        else -> "F"
                    }
                    sb.append("║ Grade: $grade\n")
                    sb.append("╚══════════════════════════════════╝\n")

                    conn.disconnect()
                    sb.toString()
                }
                appendOutput(result)
            } catch (e: Exception) {
                appendOutput("[E] Header check failed: ${e.message}\n")
            }
        }
    }

    private fun extractCN(dn: String): String {
        val parts = dn.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("CN=")) return trimmed.substring(3)
        }
        return dn
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
