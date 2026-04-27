package com.hackerlauncher.modules

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hackerlauncher.utils.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FEATURE 4: AdBlockerFragment
 * Block ads by modifying hosts file (show blocked domains count)
 * Hacker-themed dark UI with green text (#00FF00) on black background (#0D0D0D)
 */
class AdBlockerFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val YELLOW = Color.parseColor("#FFFF00")
    private val RED = Color.parseColor("#FF4444")
    private val BLACK = Color.parseColor("#0D0D0D")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")
    private val MED_GRAY = Color.parseColor("#333333")

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var tvStatus: TextView
    private lateinit var tvBlockedCount: TextView

    private val adDomains = listOf(
        "ad.doubleclick.net", "ads.google.com", "ads.youtube.com",
        "pagead2.googlesyndication.com", "admob.com", "adservice.google.com",
        "adservice.google.dk", "googleadservices.com", "googleads.g.doubleclick.net",
        "ad.collector.imgur.com", "ads.reddit.com", "ads.twitter.com",
        "analytics.google.com", "analytics.yahoo.com", "ads.facebook.com",
        "ads.instagram.com", "ads.tiktok.com", "ads.snapchat.com",
        "ad.360yield.com", "ad.adriver.ru", "adsrvr.org",
        "adnxs.com", "ads.yahoo.com", "advertising.com",
        "cdn.adnxs.com", "ib.adnxs.com", "m.adnxs.com",
        "ads.mopub.com", "sdk.mopub.com", "ads.flurry.com",
        "ads.inmobi.com", "sdk.inmobi.com", "ads.unity3d.com",
        "ads.chartboost.com", "ads.vungle.com", "ads.ironsrc.com",
        "ads.applovin.com", "sdk.applovin.com", "ads.tapjoy.com",
        "ads.startapp.com", "ads.leadbolt.com", "ads.airpush.com",
        "cdn.mopub.com", "ads.heyzap.com", "ads.revmob.com",
        "ads.chartboost.net", "ads.supersonic.com", "ads.fyber.com",
        "openx.net", "rubiconproject.com", "pubmatic.com",
        "casalemedia.com", "criteo.com", "taboola.com",
        "outbrain.com", "mgid.com", "revcontent.com",
        "zedo.com", "adbrite.com", "buysellads.com",
        "quantserve.com", "scorecardresearch.com", "moatads.com",
        "adsafeprotected.com", "doubleverify.com", "integralads.com",
        "ads.stickyadstv.com", "cdn.stickyadstv.com", "ads.smartclip.net",
        "tracking.mosier.fi", "ads.trafficjunky.net", "adsterra.com",
        "propellerads.com", "hilltopads.com", "popads.net",
        "adcrun.ch", "yllix.com", "evadav.com", "richpush.com",
        "pushnotifications.com", "push.js", "notix.io"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        val rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
            setPadding(12, 12, 12, 12)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        rootLayout.addView(TextView(ctx).apply {
            text = "[ ADBLOCKER ]"
            setTextColor(GREEN)
            textSize = 16f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        })

        // Status
        tvStatus = TextView(ctx).apply {
            text = "[~] Initializing ad blocker..."
            setTextColor(YELLOW)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 4)
        }
        rootLayout.addView(tvStatus)

        // Blocked count
        tvBlockedCount = TextView(ctx).apply {
            text = "Blocked Domains: ${adDomains.size}"
            setTextColor(GREEN)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(tvBlockedCount)

        // Buttons
        val btnRow1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow1.addView(makeBtn("BLOCK ADS") { blockAds() })
        btnRow1.addView(makeBtn("CHECK STATUS") { checkStatus() })
        btnRow1.addView(makeBtn("UNBLOCK") { unblockAds() })
        rootLayout.addView(btnRow1)

        val btnRow2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(makeBtn("ADD DOMAIN") { showAddDomainDialog() })
        btnRow2.addView(makeBtn("LIST BLOCKED") { listBlockedDomains() })
        btnRow2.addView(makeBtn("TEST AD") { testAdBlocking() })
        rootLayout.addView(btnRow2)

        // ScrollView for output
        scrollView = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }
        tvOutput = TextView(ctx).apply {
            setTextColor(GREEN)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
        }
        scrollView.addView(tvOutput)
        rootLayout.addView(scrollView)

        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║       ADBLOCKER v1.0            ║\n")
        appendOutput("║  Block ads via hosts file       ║\n")
        appendOutput("║  ${adDomains.size} ad domains ready to block     ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return rootLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkStatus()
    }

    private fun blockAds() {
        tvStatus.text = "[*] Blocking ad domains..."
        lifecycleScope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║       Blocking Ad Domains       ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            var blockedCount = 0
            for (domain in adDomains) {
                val result = withContext(Dispatchers.IO) {
                    ShellExecutor.execute("su -c 'echo \"127.0.0.1 $domain\" >> /etc/hosts'")
                }
                if (result.error.isEmpty()) {
                    blockedCount++
                    appendOutput("║ [+] Blocked: $domain\n")
                } else {
                    appendOutput("║ [!] Failed: $domain (no root)\n")
                }
            }

            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("║ Blocked: $blockedCount / ${adDomains.size} domains\n")
            appendOutput("╚══════════════════════════════════╝\n\n")

            tvBlockedCount.text = "Blocked Domains: $blockedCount"
            tvStatus.text = if (blockedCount > 0) "[+] Ad blocking active ($blockedCount domains)" else "[!] Root required for hosts modification"
        }
    }

    private fun checkStatus() {
        lifecycleScope.launch {
            appendOutput("[*] Checking ad blocker status...\n")
            val result = withContext(Dispatchers.IO) {
                ShellExecutor.execute("su -c 'cat /etc/hosts | grep -c 127.0.0.1'")
            }
            val count = result.output.trim().toIntOrNull() ?: 0
            if (count > 2) {
                tvStatus.text = "[+] Ad blocker ACTIVE ($count entries in hosts)"
                appendOutput("[+] Hosts file has $count entries\n")
            } else {
                tvStatus.text = "[-] Ad blocker INACTIVE"
                appendOutput("[-] Only $count default entries in hosts file\n")
                appendOutput("[*] Tap BLOCK ADS to activate\n")
            }

            // Alternative: Private DNS method
            appendOutput("\n[*] Alternative: Use Private DNS\n")
            appendOutput("    Settings > Network > Private DNS\n")
            appendOutput("    dns.adguard.com = AdGuard DNS (blocks ads)\n")
            appendOutput("    dns-family.adguard.com = Family protection\n\n")
        }
    }

    private fun unblockAds() {
        lifecycleScope.launch {
            appendOutput("[*] Restoring default hosts file...\n")
            val result = withContext(Dispatchers.IO) {
                ShellExecutor.execute("su -c 'echo \"127.0.0.1 localhost\" > /etc/hosts && echo \"::1 ip6-localhost\" >> /etc/hosts'")
            }
            if (result.error.isEmpty()) {
                tvStatus.text = "[-] Ad blocker DISABLED"
                tvBlockedCount.text = "Blocked Domains: 0"
                appendOutput("[+] Hosts file restored to default\n\n")
            } else {
                appendOutput("[!] Root required to modify hosts file\n\n")
            }
        }
    }

    private fun showAddDomainDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "e.g., ads.example.com"
            setTextColor(GREEN)
            setHintTextColor(DARK_GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(12, 8, 12, 8)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Domain to Block")
            .setView(input)
            .setPositiveButton("Block") { _, _ ->
                val domain = input.text.toString().trim()
                if (domain.isNotEmpty()) {
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ShellExecutor.execute("su -c 'echo \"127.0.0.1 $domain\" >> /etc/hosts'")
                        }
                        if (result.error.isEmpty()) {
                            appendOutput("[+] Blocked: $domain\n")
                        } else {
                            appendOutput("[!] Failed: root required\n")
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun listBlockedDomains() {
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║     Blocked Ad Domains List     ║\n")
        appendOutput("╠══════════════════════════════════╣\n")
        for ((index, domain) in adDomains.withIndex()) {
            appendOutput("║ ${index + 1}. $domain\n")
        }
        appendOutput("╠══════════════════════════════════╣\n")
        appendOutput("║ Total: ${adDomains.size} domains\n")
        appendOutput("╚══════════════════════════════════╝\n\n")
    }

    private fun testAdBlocking() {
        appendOutput("[*] Testing ad domain resolution...\n\n")
        lifecycleScope.launch {
            val testDomains = adDomains.take(10)
            for (domain in testDomains) {
                try {
                    val addresses = withContext(Dispatchers.IO) {
                        java.net.InetAddress.getAllByName(domain)
                    }
                    val ips = addresses.map { it.hostAddress }.joinToString(", ")
                    if (ips.startsWith("127.0.0.1")) {
                        appendOutput("  ✅ $domain -> $ips (BLOCKED)\n")
                    } else {
                        appendOutput("  ❌ $domain -> $ips (NOT BLOCKED)\n")
                    }
                } catch (e: Exception) {
                    appendOutput("  ✅ $domain -> FAILED TO RESOLVE (BLOCKED)\n")
                }
            }
            appendOutput("\n")
        }
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun makeBtn(label: String, listener: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label
            setTextColor(GREEN)
            setBackgroundColor(DARK_GRAY)
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setPadding(2, 2, 2, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(1, 1, 1, 1)
            }
            setOnClickListener { listener() }
        }
    }
}
