package com.hackerlauncher.modules

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
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

class PermissionAnalyzerFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView
    private lateinit var spinnerFilter: Spinner
    private lateinit var etPackageFilter: EditText

    private val dangerPermissions = setOf(
        "android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR",
        "android.permission.CAMERA",
        "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS", "android.permission.GET_ACCOUNTS",
        "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_PHONE_STATE", "android.permission.CALL_PHONE",
        "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG",
        "android.permission.ADD_VOICEMAIL", "android.permission.USE_SIP", "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.BODY_SENSORS",
        "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS", "android.permission.READ_SMS",
        "android.permission.RECEIVE_WAP_PUSH", "android.permission.RECEIVE_MMS",
        "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.READ_PHONE_NUMBERS",
        "android.permission.ANSWER_PHONE_CALLS",
        "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO"
    )

    private val signaturePermissions = setOf(
        "android.permission.BIND_DEVICE_ADMIN", "android.permission.BIND_VPN_SERVICE",
        "android.permission.BIND_ACCESSIBILITY_SERVICE", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        "android.permission.BIND_INPUT_METHOD", "android.permission.BIND_VOICE_INTERACTION",
        "android.permission.BIND_PRINT_SERVICE", "android.permission.BIND_NFC_SERVICE",
        "android.permission.BIND_REMOTEVIEWS", "android.permission.BIND_APPWIDGET",
        "android.permission.BIND_CARRIER_SERVICES", "android.permission.BIND_TELECOM_CONNECTION_SERVICE",
        "android.permission.BIND_CALL_SCREENING_SERVICE", "android.permission.BIND_CONDITION_PROVIDER_SERVICE",
        "android.permission.BIND_MIDI_DEVICE_SERVICE"
    )

    private val permissionCategories = mapOf(
        "Location" to listOf("LOCATION", "ACCESS_FINE", "ACCESS_COARSE", "BACKGROUND_LOCATION", "NEARBY_WIFI"),
        "Camera" to listOf("CAMERA"),
        "SMS" to listOf("SMS", "MMS", "WAP_PUSH"),
        "Phone" to listOf("PHONE", "CALL", "VOICEMAIL", "SIP"),
        "Contacts" to listOf("CONTACTS", "GET_ACCOUNTS"),
        "Storage" to listOf("STORAGE", "EXTERNAL_STORAGE", "MANAGE_EXTERNAL", "READ_MEDIA"),
        "Audio" to listOf("RECORD_AUDIO"),
        "Sensors" to listOf("BODY_SENSORS", "ACTIVITY_RECOGNITION"),
        "Bluetooth" to listOf("BLUETOOTH"),
        "Calendar" to listOf("CALENDAR")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
        }

        // Title
        root.addView(TextView(context).apply {
            text = ">> PERMISSION ANALYZER v1.1"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        })

        // Filter dropdown
        root.addView(TextView(context).apply {
            text = "Filter by category:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        })

        val categories = listOf("All") + permissionCategories.keys.toList()
        spinnerFilter = Spinner(context).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
            setBackgroundColor(0xFF1A1A1A.toInt())
            setPadding(8, 4, 8, 4)
        }
        root.addView(spinnerFilter)

        // Package filter
        etPackageFilter = EditText(context).apply {
            hint = "Filter by package name"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 4, 8, 4)
        }
        root.addView(etPackageFilter)

        // Buttons
        val btnRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow1.addView(makeBtn("Scan All") { scanAllApps() })
        btnRow1.addView(makeBtn("Dangerous") { showDangerousApps() })
        btnRow1.addView(makeBtn("Filtered") { showFiltered() })
        root.addView(btnRow1)

        val btnRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(makeBtn("Top 10 Risk") { showTopRiskApps() })
        btnRow2.addView(makeBtn("Signature") { showSignatureApps() })
        btnRow2.addView(makeBtn("Export") { exportReport() })
        root.addView(btnRow2)

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
        appendOutput("║  PERMISSION ANALYZER v1.1       ║\n")
        appendOutput("║  Scan app permissions & risk    ║\n")
        appendOutput("║  NORMAL / DANGEROUS / SIGNATURE ║\n")
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

    private fun categorizePermission(perm: String): String {
        return when {
            signaturePermissions.contains(perm) -> "SIGNATURE"
            dangerPermissions.contains(perm) -> "DANGEROUS"
            else -> "NORMAL"
        }
    }

    private data class AppPermInfo(
        val packageName: String,
        val appName: String,
        val permissions: List<String>,
        val dangerousCount: Int,
        val signatureCount: Int,
        val normalCount: Int
    )

    private suspend fun scanPermissions(): List<AppPermInfo> = withContext(Dispatchers.IO) {
        val pm = requireContext().packageManager
        val apps = pm.getInstalledApplications(0)

        apps.mapNotNull { appInfo ->
            try {
                val packageName = appInfo.packageName
                val appName = pm.getApplicationLabel(appInfo).toString()
                val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                val perms = pkgInfo.requestedPermissions?.toList() ?: emptyList()

                var dangerous = 0
                var signature = 0
                var normal = 0
                for (p in perms) {
                    when (categorizePermission(p)) {
                        "DANGEROUS" -> dangerous++
                        "SIGNATURE" -> signature++
                        else -> normal++
                    }
                }
                AppPermInfo(packageName, appName, perms, dangerous, signature, normal)
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.dangerousCount }
    }

    private fun scanAllApps() {
        scope.launch {
            appendOutput("[*] Scanning all installed apps...\n")
            try {
                val apps = scanPermissions()
                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║  All Apps Permission Scan       ║\n")
                appendOutput("║  Total: ${apps.size} apps            ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                val pkgFilter = etPackageFilter.text.toString().trim().lowercase()
                val filtered = if (pkgFilter.isNotEmpty()) {
                    apps.filter { it.packageName.lowercase().contains(pkgFilter) || it.appName.lowercase().contains(pkgFilter) }
                } else {
                    apps
                }

                for (app in filtered.take(30)) {
                    appendOutput("[${app.appName}]\n")
                    appendOutput("  Pkg: ${app.packageName}\n")
                    appendOutput("  Normal: ${app.normalCount} | Dangerous: ${app.dangerousCount} | Signature: ${app.signatureCount}\n")

                    val dangerousPerms = app.permissions.filter { categorizePermission(it) == "DANGEROUS" }
                    if (dangerousPerms.isNotEmpty()) {
                        appendOutput("  Dangerous:\n")
                        for (p in dangerousPerms.take(8)) {
                            appendOutput("    ! ${p.removePrefix("android.permission.")}\n")
                        }
                        if (dangerousPerms.size > 8) {
                            appendOutput("    ... +${dangerousPerms.size - 8} more\n")
                        }
                    }
                    appendOutput("\n")
                }
                if (filtered.size > 30) {
                    appendOutput("[*] Showing top 30 of ${filtered.size}. Use filters.\n\n")
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] Scan failed: ${e.message}\n")
            }
        }
    }

    private fun showDangerousApps() {
        scope.launch {
            appendOutput("[*] Finding apps with dangerous permissions...\n")
            try {
                val apps = scanPermissions().filter { it.dangerousCount > 0 }
                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║ Apps with Dangerous Perms: ${apps.size}  ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                for ((idx, app) in apps.withIndex()) {
                    appendOutput("${idx + 1}. ${app.appName}\n")
                    appendOutput("   Dangerous: ${app.dangerousCount} perms\n")
                    val dangerousPerms = app.permissions.filter { categorizePermission(it) == "DANGEROUS" }
                    for (p in dangerousPerms) {
                        appendOutput("   ! ${p.removePrefix("android.permission.")}\n")
                    }
                    appendOutput("\n")
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun showFiltered() {
        val selectedCategory = spinnerFilter.selectedItem.toString()
        if (selectedCategory == "All") {
            scanAllApps()
            return
        }

        val keywords = permissionCategories[selectedCategory] ?: emptyList()
        scope.launch {
            appendOutput("[*] Filtering by: $selectedCategory\n")
            try {
                val apps = scanPermissions()
                val filtered = apps.filter { app ->
                    app.permissions.any { perm ->
                        keywords.any { kw -> perm.contains(kw, ignoreCase = true) }
                    }
                }

                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║ Category: $selectedCategory\n")
                appendOutput("║ Apps: ${filtered.size}\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                for (app in filtered) {
                    val matchingPerms = app.permissions.filter { perm ->
                        keywords.any { kw -> perm.contains(kw, ignoreCase = true) }
                    }
                    appendOutput("[${app.appName}]\n")
                    for (p in matchingPerms) {
                        val cat = categorizePermission(p)
                        val marker = when (cat) {
                            "DANGEROUS" -> "!"
                            "SIGNATURE" -> "#"
                            else -> " "
                        }
                        appendOutput("  $marker ${p.removePrefix("android.permission.")} [$cat]\n")
                    }
                    appendOutput("\n")
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun showTopRiskApps() {
        scope.launch {
            appendOutput("[*] Calculating risk scores...\n")
            try {
                val apps = scanPermissions().sortedByDescending { it.dangerousCount * 3 + it.signatureCount }
                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║   Top 10 Riskiest Apps          ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                for ((idx, app) in apps.take(10).withIndex()) {
                    val riskScore = app.dangerousCount * 3 + app.signatureCount * 2 + app.normalCount
                    val riskLevel = when {
                        app.dangerousCount >= 8 -> "CRITICAL"
                        app.dangerousCount >= 5 -> "HIGH"
                        app.dangerousCount >= 3 -> "MEDIUM"
                        app.dangerousCount >= 1 -> "LOW"
                        else -> "MINIMAL"
                    }
                    appendOutput("${idx + 1}. ${app.appName}\n")
                    appendOutput("   Pkg: ${app.packageName}\n")
                    appendOutput("   Risk: $riskScore | Level: $riskLevel\n")
                    appendOutput("   D:${app.dangerousCount} S:${app.signatureCount} N:${app.normalCount}\n\n")
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun showSignatureApps() {
        scope.launch {
            appendOutput("[*] Finding apps with signature permissions...\n")
            try {
                val apps = scanPermissions().filter { it.signatureCount > 0 }
                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║ Apps with Signature Perms: ${apps.size}  ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                for (app in apps) {
                    val sigPerms = app.permissions.filter { categorizePermission(it) == "SIGNATURE" }
                    appendOutput("[${app.appName}]\n")
                    for (p in sigPerms) {
                        appendOutput("  # ${p.removePrefix("android.permission.")}\n")
                    }
                    appendOutput("\n")
                }
                appendOutput("╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun exportReport() {
        scope.launch {
            appendOutput("[*] Generating report...\n")
            try {
                val apps = scanPermissions()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(requireContext().getExternalFilesDir(null), "perm_report_$timestamp.txt")

                val sb = StringBuilder()
                sb.append("HACKER LAUNCHER - PERMISSION REPORT\n")
                sb.append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
                sb.append("Total apps: ${apps.size}\n")
                sb.append("=".repeat(60)).append("\n\n")

                for (app in apps) {
                    sb.append("[${app.appName}] (${app.packageName})\n")
                    sb.append("  Normal: ${app.normalCount} | Dangerous: ${app.dangerousCount} | Signature: ${app.signatureCount}\n")
                    for (p in app.permissions) {
                        val cat = categorizePermission(p)
                        sb.append("  [$cat] ${p.removePrefix("android.permission.")}\n")
                    }
                    sb.append("\n")
                }

                FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
                appendOutput("[+] Report saved: ${file.absolutePath}\n")
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
