package com.hackerlauncher.modules

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeepLinkScannerFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView
    private lateinit var etTestUri: EditText

    data class DeepLinkInfo(
        val packageName: String,
        val appName: String,
        val scheme: String,
        val host: String?,
        val pathPrefix: String?,
        val mimeType: String?
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
        }

        // Title
        root.addView(TextView(context).apply {
            text = ">> DEEP LINK SCANNER v1.1"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        })

        // Test URI input
        root.addView(TextView(context).apply {
            text = "Test Deep Link URI:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        })
        etTestUri = EditText(context).apply {
            hint = "myapp://path/to/page"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setPadding(8, 8, 8, 8)
        }
        root.addView(etTestUri)

        // Buttons row 1
        val btnRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow1.addView(makeBtn("Scan All") { scanAllDeepLinks() })
        btnRow1.addView(makeBtn("Schemes") { listAllSchemes() })
        btnRow1.addView(makeBtn("Hosts") { listAllHosts() })
        root.addView(btnRow1)

        // Buttons row 2
        val btnRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(makeBtn("Test Link") { testDeepLink() })
        btnRow2.addView(makeBtn("Http Links") { showHttpDeepLinks() })
        btnRow2.addView(makeBtn("Custom") { showCustomSchemes() })
        root.addView(btnRow2)

        // Buttons row 3
        val btnRow3 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow3.addView(makeBtn("Export") { exportDeepLinks() })
        btnRow3.addView(makeBtn("App Links") { showAppLinks() })
        btnRow3.addView(makeBtn("Mime Types") { showMimeTypes() })
        root.addView(btnRow3)

        // Output
        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        tvOutput = TextView(context).apply {
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
        }
        scrollView.addView(tvOutput)
        root.addView(scrollView)

        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║   DEEP LINK SCANNER v1.1        ║\n")
        appendOutput("║   Scan app intent-filters       ║\n")
        appendOutput("║   URI schemes, hosts, paths     ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return root
    }

    private fun makeBtn(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(0xFF00FF00.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(6, 2, 6, 2)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
        }
    }

    private suspend fun scanDeepLinks(): List<DeepLinkInfo> = withContext(Dispatchers.IO) {
        val pm = requireContext().packageManager
        val apps = pm.getInstalledApplications(0)
        val deepLinks = mutableListOf<DeepLinkInfo>()

        for (appInfo in apps) {
            try {
                val packageName = appInfo.packageName
                val appName = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { packageName }

                // Scan known schemes via queryIntentActivities
                val knownSchemes = listOf("http", "https", "tel", "mailto", "geo", "market",
                    "sms", "smsto", "mms", "mmsto", "fb", "twitter", "instagram",
                    "whatsapp", "tg", "zoomus", "spotify", "youtube", "vnd.youtube")

                for (scheme in knownSchemes) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse("$scheme://test")
                        val resolveInfos = pm.queryIntentActivities(intent, 0)
                        for (ri in resolveInfos) {
                            if (ri.activityInfo.packageName == packageName) {
                                deepLinks.add(DeepLinkInfo(
                                    packageName, appName, scheme, null, null, null
                                ))
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Check for app-specific custom schemes
                try {
                    val customSchemeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("$packageName://test"))
                    val resolveInfos = pm.queryIntentActivities(customSchemeIntent, 0)
                    for (ri in resolveInfos) {
                        deepLinks.add(DeepLinkInfo(
                            packageName, appName, packageName, null, null, null
                        ))
                    }
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }

        // Scan common custom scheme patterns
        val allCustomSchemes = listOf("app", "mobile", "my", "open", "go", "link", "redirect", "callback", "auth", "login", "share")
        for (scheme in allCustomSchemes) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://test"))
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                for (ri in resolveInfos) {
                    val pkg = ri.activityInfo.packageName
                    if (deepLinks.none { it.packageName == pkg && it.scheme == scheme }) {
                        val appName = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (_: Exception) { pkg }
                        deepLinks.add(DeepLinkInfo(pkg, appName, scheme, null, null, null))
                    }
                }
            } catch (_: Exception) {}
        }

        deepLinks.distinctBy { it.packageName to it.scheme }
    }

    private fun scanAllDeepLinks() {
        scope.launch {
            appendOutput("[*] Scanning all apps for deep links...\n")
            try {
                val links = scanDeepLinks()
                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║   Deep Link Scan Results        ║\n")
                appendOutput("║   Found: ${links.size} deep links      ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                val groupedByApp = links.groupBy { it.packageName }
                for ((pkg, appLinks) in groupedByApp.entries.take(30)) {
                    val appName = appLinks.first().appName
                    appendOutput("[$appName]\n")
                    appendOutput("  Pkg: $pkg\n")
                    for (link in appLinks) {
                        val uri = "${link.scheme}://${link.host ?: "*"}"
                        appendOutput("  > $uri\n")
                    }
                    appendOutput("\n")
                }

                if (groupedByApp.size > 30) {
                    appendOutput("[*] Showing 30 of ${groupedByApp.size} apps\n\n")
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] Scan: ${e.message}\n")
            }
        }
    }

    private fun listAllSchemes() {
        scope.launch {
            appendOutput("[*] Enumerating URI schemes...\n")
            try {
                val links = scanDeepLinks()
                val schemes = links.map { it.scheme }.distinct().sorted()

                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║   URI Schemes Found             ║\n")
                appendOutput("║   Total: ${schemes.size}                   ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                for (scheme in schemes) {
                    val count = links.count { it.scheme == scheme }
                    appendOutput("  ${scheme}://  ($count apps)\n")
                }
                appendOutput("\n╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun listAllHosts() {
        scope.launch {
            appendOutput("[*] Scanning for deep link hosts...\n")
            try {
                val pm = requireContext().packageManager
                val hosts = mutableMapOf<String, MutableList<String>>()

                val knownHosts = listOf(
                    "example.com", "www.example.com", "open.app", "app.link",
                    "fb.me", "t.co", "goo.gl", "bit.ly", "amzn.to",
                    "play.google.com", "maps.google.com", "drive.google.com"
                )

                for (host in knownHosts) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://$host"))
                        val resolveInfos = pm.queryIntentActivities(intent, 0)
                        if (resolveInfos.isNotEmpty()) {
                            for (ri in resolveInfos) {
                                val pkg = ri.activityInfo.packageName
                                hosts.getOrPut(host) { mutableListOf() }.add(pkg)
                            }
                        }
                    } catch (_: Exception) {}
                }

                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║   Deep Link Hosts               ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                if (hosts.isEmpty()) {
                    appendOutput("  No deep link hosts discovered\n")
                } else {
                    for ((host, pkgs) in hosts) {
                        appendOutput("  https://$host\n")
                        for (pkg in pkgs) {
                            appendOutput("    -> $pkg\n")
                        }
                        appendOutput("\n")
                    }
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun testDeepLink() {
        try {
            val uri = etTestUri.text.toString().trim()
            if (uri.isEmpty()) {
                appendOutput("[!] Enter a URI to test\n")
                return
            }

            appendOutput("[*] Testing: $uri\n")

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            val pm = requireContext().packageManager
            val resolveInfos = pm.queryIntentActivities(intent, 0)

            if (resolveInfos.isEmpty()) {
                appendOutput("[!] No apps can handle this URI\n\n")
            } else {
                appendOutput("[+] ${resolveInfos.size} app(s) can handle:\n")
                for (ri in resolveInfos) {
                    appendOutput("  -> ${ri.activityInfo.packageName}\n")
                    appendOutput("     Activity: ${ri.activityInfo.name}\n")
                }

                // Launch the deep link
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    appendOutput("[*] Deep link launched!\n\n")
                } catch (e: Exception) {
                    appendOutput("[E] Launch: ${e.message}\n\n")
                }
            }
        } catch (e: Exception) {
            appendOutput("[E] Test: ${e.message}\n")
        }
    }

    private fun showHttpDeepLinks() {
        scope.launch {
            appendOutput("[*] Finding HTTP/HTTPS deep links...\n")
            try {
                val links = scanDeepLinks().filter { it.scheme == "http" || it.scheme == "https" }
                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║   HTTP/HTTPS Deep Links         ║\n")
                appendOutput("║   Found: ${links.size}                   ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                for (link in links) {
                    appendOutput("[${link.appName}]\n")
                    appendOutput("  ${link.scheme}://${link.host ?: "*"}\n\n")
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun showCustomSchemes() {
        scope.launch {
            appendOutput("[*] Finding custom URI schemes...\n")
            try {
                val links = scanDeepLinks().filter { it.scheme !in listOf("http", "https", "tel", "mailto", "geo", "sms") }
                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║   Custom URI Schemes            ║\n")
                appendOutput("║   Found: ${links.size}                   ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                for (link in links) {
                    appendOutput("[${link.appName}]\n")
                    appendOutput("  ${link.scheme}://${link.host ?: "*"}\n\n")
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun showAppLinks() {
        scope.launch {
            appendOutput("[*] Checking for verified App Links...\n")
            try {
                val pm = requireContext().packageManager
                val domains = listOf("google.com", "facebook.com", "twitter.com", "youtube.com", "instagram.com")
                var count = 0

                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║   Android App Links             ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                for (domain in domains) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://$domain"))
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        if (resolveInfos.isNotEmpty()) {
                            val ri = resolveInfos[0]
                            val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(ri.activityInfo.packageName, 0)) } catch (_: Exception) { ri.activityInfo.packageName }
                            appendOutput("  https://$domain\n")
                            appendOutput("    -> $appName (${ri.activityInfo.packageName})\n")
                            count++
                        }
                    } catch (_: Exception) {}
                }

                if (count == 0) {
                    appendOutput("  No verified App Links detected\n")
                }
                appendOutput("\n╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun showMimeTypes() {
        scope.launch {
            appendOutput("[*] Scanning for MIME type handlers...\n")
            try {
                val pm = requireContext().packageManager
                val mimeTypes = listOf(
                    "text/html", "application/pdf", "image/*", "video/*",
                    "application/zip", "application/json", "text/plain"
                )

                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║   MIME Type Handlers            ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                for (mime in mimeTypes) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.type = mime
                        val resolveInfos = pm.queryIntentActivities(intent, 0)
                        appendOutput("  $mime (${resolveInfos.size} apps)\n")
                        for (ri in resolveInfos.take(3)) {
                            appendOutput("    -> ${ri.activityInfo.packageName}\n")
                        }
                        if (resolveInfos.size > 3) {
                            appendOutput("    ... +${resolveInfos.size - 3} more\n")
                        }
                        appendOutput("\n")
                    } catch (_: Exception) {}
                }

                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun exportDeepLinks() {
        scope.launch {
            appendOutput("[*] Exporting deep link list...\n")
            try {
                val links = scanDeepLinks()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(requireContext().getExternalFilesDir(null), "deeplinks_$timestamp.txt")

                val sb = StringBuilder()
                sb.append("HACKER LAUNCHER - DEEP LINK REPORT\n")
                sb.append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
                sb.append("Total deep links: ${links.size}\n")
                sb.append("=".repeat(60)).append("\n\n")

                val grouped = links.groupBy { it.packageName }
                for ((pkg, appLinks) in grouped) {
                    val appName = appLinks.first().appName
                    sb.append("[$appName] ($pkg)\n")
                    for (link in appLinks) {
                        sb.append("  ${link.scheme}://${link.host ?: "*"}${link.pathPrefix ?: ""}\n")
                    }
                    sb.append("\n")
                }

                FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
                appendOutput("[+] Exported: ${file.absolutePath}\n")
                appendOutput("[+] Size: ${file.length() / 1024}KB\n")
            } catch (e: Exception) {
                appendOutput("[E] Export: ${e.message}\n")
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
