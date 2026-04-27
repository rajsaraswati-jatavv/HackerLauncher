package com.hackerlauncher.modules

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FEATURE 4: PrivacyGuardFragment
 * Show app permissions, privacy score
 * Hacker-themed dark UI with green text (#00FF00) on black background (#0D0D0D)
 */
class PrivacyGuardFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val YELLOW = Color.parseColor("#FFFF00")
    private val RED = Color.parseColor("#FF4444")
    private val CYAN = Color.parseColor("#00FFFF")
    private val BLACK = Color.parseColor("#0D0D0D")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var tvStatus: TextView
    private lateinit var tvPrivacyScore: TextView
    private lateinit var privacyProgressBar: ProgressBar
    private var scanJob: Job? = null

    data class AppPermissionInfo(
        val packageName: String,
        val appName: String,
        val permissions: List<String>,
        val dangerScore: Int,
        val isSystem: Boolean
    )

    private val dangerousPermissions = mapOf(
        "android.permission.READ_CONTACTS" to 3,
        "android.permission.WRITE_CONTACTS" to 3,
        "android.permission.READ_CALL_LOG" to 4,
        "android.permission.WRITE_CALL_LOG" to 4,
        "android.permission.READ_PHONE_STATE" to 3,
        "android.permission.CALL_PHONE" to 4,
        "android.permission.PROCESS_OUTGOING_CALLS" to 4,
        "android.permission.READ_SMS" to 5,
        "android.permission.SEND_SMS" to 5,
        "android.permission.RECEIVE_SMS" to 4,
        "android.permission.READ_CALENDAR" to 2,
        "android.permission.WRITE_CALENDAR" to 2,
        "android.permission.CAMERA" to 3,
        "android.permission.RECORD_AUDIO" to 4,
        "android.permission.READ_EXTERNAL_STORAGE" to 2,
        "android.permission.WRITE_EXTERNAL_STORAGE" to 2,
        "android.permission.ACCESS_FINE_LOCATION" to 4,
        "android.permission.ACCESS_COARSE_LOCATION" to 3,
        "android.permission.ACCESS_BACKGROUND_LOCATION" to 5,
        "android.permission.BODY_SENSORS" to 3,
        "android.permission.READ_PHONE_NUMBERS" to 4,
        "android.permission.ANSWER_PHONE_CALLS" to 3,
        "android.permission.ACCEPT_HANDOVER" to 3,
        "android.permission.MANAGE_EXTERNAL_STORAGE" to 4,
        "android.permission.QUERY_ALL_PACKAGES" to 3,
        "android.permission.SYSTEM_ALERT_WINDOW" to 2,
        "android.permission.REQUEST_INSTALL_PACKAGES" to 2,
        "android.permission.BIND_VPN_SERVICE" to 3,
        "android.permission.PACKAGE_USAGE_STATS" to 2
    )

    private val permissionLabels = mapOf(
        "android.permission.READ_CONTACTS" to "READ_CONTACTS",
        "android.permission.WRITE_CONTACTS" to "WRITE_CONTACTS",
        "android.permission.READ_CALL_LOG" to "READ_CALL_LOG",
        "android.permission.CAMERA" to "CAMERA",
        "android.permission.RECORD_AUDIO" to "RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION" to "LOCATION(FINE)",
        "android.permission.ACCESS_COARSE_LOCATION" to "LOCATION(COARSE)",
        "android.permission.READ_SMS" to "READ_SMS",
        "android.permission.SEND_SMS" to "SEND_SMS",
        "android.permission.READ_EXTERNAL_STORAGE" to "READ_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE" to "WRITE_STORAGE",
        "android.permission.READ_PHONE_STATE" to "READ_PHONE_STATE",
        "android.permission.CALL_PHONE" to "CALL_PHONE",
        "android.permission.BODY_SENSORS" to "BODY_SENSORS",
        "android.permission.READ_CALENDAR" to "READ_CALENDAR"
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
            text = "[ PRIVACY GUARD ]"
            setTextColor(GREEN)
            textSize = 16f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        })

        // Privacy score
        tvPrivacyScore = TextView(ctx).apply {
            text = "🔒 Score: --"
            setTextColor(CYAN)
            textSize = 20f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 4)
        }
        rootLayout.addView(tvPrivacyScore)

        privacyProgressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(privacyProgressBar)

        // Status
        tvStatus = TextView(ctx).apply {
            text = "[~] Ready to scan"
            setTextColor(YELLOW)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 4)
        }
        rootLayout.addView(tvStatus)

        // Buttons
        rootLayout.addView(makeSectionHeader("SCAN OPTIONS"))

        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeBtn("FULL SCAN") { fullPrivacyScan() })
        row1.addView(makeBtn("DANGEROUS") { scanDangerousApps() })
        row1.addView(makeBtn("QUICK SCAN") { quickScan() })
        rootLayout.addView(row1)

        val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(makeBtn("CAMERA APPS") { scanByPermission("android.permission.CAMERA") })
        row2.addView(makeBtn("MIC APPS") { scanByPermission("android.permission.RECORD_AUDIO") })
        row2.addView(makeBtn("LOC APPS") { scanByPermission("android.permission.ACCESS_FINE_LOCATION") })
        rootLayout.addView(row2)

        val row3 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(makeBtn("SMS APPS") { scanByPermission("android.permission.READ_SMS") })
        row3.addView(makeBtn("CONTACTS") { scanByPermission("android.permission.READ_CONTACTS") })
        row3.addView(makeBtn("STORAGE") { scanByPermission("android.permission.WRITE_EXTERNAL_STORAGE") })
        rootLayout.addView(row3)

        // Output
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

        return rootLayout
    }

    private fun fullPrivacyScan() {
        if (scanJob?.isActive == true) return
        tvStatus.text = "[*] Running full privacy scan..."
        privacyProgressBar.progress = 0

        scanJob = lifecycleScope.launch {
            val pm = requireContext().packageManager
            val packages = withContext(Dispatchers.IO) { pm.getInstalledApplications(0) }
            val appInfos = mutableListOf<AppPermissionInfo>()
            var totalDangerScore = 0

            for ((index, appInfo) in packages.withIndex()) {
                val perms = withContext(Dispatchers.IO) {
                    try {
                        pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                            .requestedPermissions?.toList() ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }

                val dangerScore = perms.sumOf { perm ->
                    dangerousPermissions[perm] ?: 0
                }

                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) { appInfo.packageName }

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                if (dangerScore > 0) {
                    appInfos.add(AppPermissionInfo(
                        packageName = appInfo.packageName,
                        appName = appName,
                        permissions = perms,
                        dangerScore = dangerScore,
                        isSystem = isSystem
                    ))
                }
                totalDangerScore += dangerScore

                withContext(Dispatchers.Main) {
                    privacyProgressBar.progress = ((index + 1) * 100 / packages.size)
                }
            }

            appInfos.sortByDescending { it.dangerScore }

            // Calculate privacy score (lower danger = better score)
            val maxPossibleScore = packages.size * 30 // theoretical max
            val privacyScore = (100 - (totalDangerScore * 100 / maxPossibleScore.coerceAtLeast(1))).coerceIn(0, 100)

            withContext(Dispatchers.Main) {
                tvPrivacyScore.text = "🔒 Score: $privacyScore/100"
                tvPrivacyScore.setTextColor(
                    when {
                        privacyScore > 70 -> GREEN
                        privacyScore > 40 -> YELLOW
                        else -> RED
                    }
                )
                privacyProgressBar.progress = privacyScore
            }

            appendOutput("═══ PRIVACY SCAN RESULTS ═══\n\n")
            appendOutput("Privacy Score: $privacyScore/100\n")
            appendOutput("Apps with dangerous perms: ${appInfos.size}\n")
            appendOutput("Total danger score: $totalDangerScore\n\n")

            for (app in appInfos.take(25)) {
                val dangerLabel = when {
                    app.dangerScore > 20 -> "🔴 CRITICAL"
                    app.dangerScore > 10 -> "🟡 HIGH"
                    app.dangerScore > 5 -> "🟠 MEDIUM"
                    else -> "🟢 LOW"
                }
                appendOutput("$dangerLabel [${app.dangerScore}] ${app.appName.take(25)}\n")
                val dangerousPerms = app.permissions.filter { dangerousPermissions.containsKey(it) }
                for (perm in dangerousPerms.take(5)) {
                    val label = permissionLabels[perm] ?: perm.removePrefix("android.permission.")
                    appendOutput("    → $label\n")
                }
                if (dangerousPerms.size > 5) {
                    appendOutput("    ... +${dangerousPerms.size - 5} more\n")
                }
            }

            appendOutput("\n───────────────────────\n")
            appendOutput("Scan complete. ${appInfos.size} apps analyzed.\n\n")

            tvStatus.text = "[+] Privacy scan complete. Score: $privacyScore"
        }
    }

    private fun scanDangerousApps() {
        appendOutput("[*] Scanning for apps with dangerous permissions...\n\n")
        lifecycleScope.launch {
            val pm = requireContext().packageManager
            val packages = withContext(Dispatchers.IO) { pm.getInstalledApplications(0) }

            appendOutput("═══ DANGEROUS APPS ═══\n\n")

            for (appInfo in packages) {
                val perms = withContext(Dispatchers.IO) {
                    try {
                        pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                            .requestedPermissions?.toList() ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }

                val dangerPerms = perms.filter { dangerousPermissions.containsKey(it) }
                if (dangerPerms.size >= 3) {
                    val appName = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { appInfo.packageName }
                    val score = dangerPerms.sumOf { dangerousPermissions[it] ?: 0 }
                    appendOutput("🔴 [$score] $appName\n")
                    for (perm in dangerPerms) {
                        val label = permissionLabels[perm] ?: perm.removePrefix("android.permission.")
                        appendOutput("    → $label (${dangerousPermissions[perm]})\n")
                    }
                    appendOutput("\n")
                }
            }
            appendOutput("[+] Scan complete\n\n")
        }
    }

    private fun quickScan() {
        appendOutput("[*] Quick privacy scan...\n\n")
        lifecycleScope.launch {
            val pm = requireContext().packageManager
            val criticalPerms = listOf(
                "android.permission.READ_SMS", "android.permission.SEND_SMS",
                "android.permission.RECORD_AUDIO", "android.permission.CAMERA",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.READ_CALL_LOG", "android.permission.CALL_PHONE"
            )
            val packages = withContext(Dispatchers.IO) { pm.getInstalledApplications(0) }
            var alertCount = 0

            for (appInfo in packages) {
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem) continue

                val perms = withContext(Dispatchers.IO) {
                    try {
                        pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                            .requestedPermissions?.toList() ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }

                val hasCritical = perms.any { it in criticalPerms }
                if (hasCritical) {
                    val appName = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { appInfo.packageName }
                    val critical = perms.filter { it in criticalPerms }
                    appendOutput("⚠️ $appName\n")
                    for (perm in critical) {
                        val label = permissionLabels[perm] ?: perm.removePrefix("android.permission.")
                        appendOutput("   → $label\n")
                    }
                    alertCount++
                }
            }

            appendOutput("\n[+] $alertCount user apps with critical permissions\n\n")
        }
    }

    private fun scanByPermission(targetPerm: String) {
        val permLabel = permissionLabels[targetPerm] ?: targetPerm.removePrefix("android.permission.")
        appendOutput("[*] Scanning for apps with $permLabel...\n\n")

        lifecycleScope.launch {
            val pm = requireContext().packageManager
            val packages = withContext(Dispatchers.IO) { pm.getInstalledApplications(0) }
            val apps = mutableListOf<String>()

            for (appInfo in packages) {
                val perms = withContext(Dispatchers.IO) {
                    try {
                        pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                            .requestedPermissions?.toList() ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }

                if (perms.contains(targetPerm)) {
                    val appName = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { appInfo.packageName }
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    apps.add("${if (isSystem) "[SYS]" else "[USR]"} $appName")
                }
            }

            appendOutput("═══ APPS WITH $permLabel ═══\n\n")
            for (app in apps) {
                appendOutput("  → $app\n")
            }
            appendOutput("\nTotal: ${apps.size} apps\n\n")
        }
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun makeSectionHeader(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = "▸ $text"
            setTextColor(YELLOW)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 12, 0, 4)
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

    override fun onDestroyView() {
        super.onDestroyView()
        scanJob?.cancel()
    }
}
