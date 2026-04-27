package com.hackerlauncher.modules

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenPinningFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView
    private lateinit var etSearchApp: EditText
    private lateinit var etUnpinPin: EditText
    private lateinit var switchAutoPin: Switch
    private lateinit var switchKiosk: Switch
    private lateinit var switchPinProtection: Switch
    private lateinit var tvPinnedLabel: TextView

    private var installedApps = listOf<AppInfo>()
    private var currentlyPinnedPackage: String? = null

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
        }

        // Title
        root.addView(TextView(context).apply {
            text = ">> SCREEN PINNING v1.1"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        })

        // Currently pinned app display
        tvPinnedLabel = TextView(context).apply {
            text = "Currently Pinned: None"
            setTextColor(0xFFFFFF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 4)
        }
        root.addView(tvPinnedLabel)

        // Search
        root.addView(TextView(context).apply {
            text = "Search app:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        })
        etSearchApp = EditText(context).apply {
            hint = "Filter by name or package"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        root.addView(etSearchApp)

        // Unpin PIN
        root.addView(TextView(context).apply {
            text = "Unpin PIN (protection):"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        })
        etUnpinPin = EditText(context).apply {
            hint = "PIN required to unpin"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(8, 8, 8, 8)
        }
        root.addView(etUnpinPin)

        // Settings switches
        switchAutoPin = makeSwitch("Auto-pin on app launch", false)
        switchKiosk = makeSwitch("Kiosk mode (full lock)", false)
        switchPinProtection = makeSwitch("Require PIN to unpin", false)
        root.addView(switchAutoPin)
        root.addView(switchKiosk)
        root.addView(switchPinProtection)

        // Buttons row 1
        val btnRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow1.addView(makeBtn("List Apps") { listApps() })
        btnRow1.addView(makeBtn("Search") { searchApps() })
        btnRow1.addView(makeBtn("Pin App") { pinApp() })
        root.addView(btnRow1)

        // Buttons row 2
        val btnRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(makeBtn("Unpin") { unpinApp() })
        btnRow2.addView(makeBtn("Current") { showCurrentPinned() })
        btnRow2.addView(makeBtn("Save Config") { saveConfig() })
        root.addView(btnRow2)

        // Buttons row 3
        val btnRow3 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow3.addView(makeBtn("Kiosk Start") { startKiosk() })
        btnRow3.addView(makeBtn("Kiosk Stop") { stopKiosk() })
        root.addView(btnRow3)

        // Info
        root.addView(TextView(context).apply {
            text = "[i] Uses startLockTask() for pinning\n[i] Enable Screen Pinning in Settings > Security"
            setTextColor(0xFF005500.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 4)
        })

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

        loadConfig()
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║   SCREEN PINNING v1.1           ║\n")
        appendOutput("║   Pin apps, kiosk mode          ║\n")
        appendOutput("║   startLockTask() based         ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return root
    }

    private fun makeSwitch(label: String, default: Boolean): Switch {
        return Switch(context).apply {
            text = label
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            isChecked = default
        }
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

    private fun loadApps() {
        try {
            val pm = requireContext().packageManager
            val apps = pm.getInstalledApplications(0)
            installedApps = apps.mapNotNull { appInfo ->
                try {
                    val packageName = appInfo.packageName
                    val appName = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { packageName }
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    AppInfo(packageName, appName, isSystem)
                } catch (_: Exception) { null }
            }.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
        } catch (e: Exception) {
            appendOutput("[E] Load apps: ${e.message}\n")
        }
    }

    private fun listApps() {
        scope.launch {
            appendOutput("[*] Loading installed apps...\n")
            try {
                val apps = withContext(Dispatchers.IO) {
                    loadApps()
                    installedApps
                }

                appendOutput("╔══════════════════════════════════╗\n")
                appendOutput("║   Installed Apps (${apps.size})          ║\n")
                appendOutput("╠══════════════════════════════════╣\n\n")

                val userApps = apps.filter { !it.isSystemApp }
                val systemApps = apps.filter { it.isSystemApp }

                appendOutput("[User Apps] (${userApps.size})\n\n")
                for ((idx, app) in userApps.withIndex()) {
                    val pinned = if (app.packageName == currentlyPinnedPackage) " [PINNED]" else ""
                    appendOutput("  ${idx + 1}. ${app.appName}$pinned\n")
                    appendOutput("     ${app.packageName}\n")
                }

                appendOutput("\n[System Apps] (${systemApps.size})\n\n")
                for ((idx, app) in systemApps.take(20).withIndex()) {
                    appendOutput("  ${idx + 1}. ${app.appName}\n")
                    appendOutput("     ${app.packageName}\n")
                }
                if (systemApps.size > 20) {
                    appendOutput("  ... +${systemApps.size - 20} more (use Search)\n")
                }

                appendOutput("\n╚══════════════════════════════════╝\n\n")
            } catch (e: Exception) {
                appendOutput("[E] ${e.message}\n")
            }
        }
    }

    private fun searchApps() {
        val query = etSearchApp.text.toString().trim().lowercase()
        if (query.isEmpty()) {
            listApps()
            return
        }

        try {
            loadApps()
            val results = installedApps.filter {
                it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Search: \"$query\"              ║\n")
            appendOutput("║   Found: ${results.size} apps              ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            for ((idx, app) in results.withIndex()) {
                val pinned = if (app.packageName == currentlyPinnedPackage) " [PINNED]" else ""
                appendOutput("  ${idx + 1}. ${app.appName}$pinned\n")
                appendOutput("     ${app.packageName}\n")
                appendOutput("     Type: ${if (app.isSystemApp) "System" else "User"}\n\n")
            }

            if (results.isEmpty()) {
                appendOutput("  No apps found\n")
            }

            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Search: ${e.message}\n")
        }
    }

    private fun pinApp() {
        try {
            val query = etSearchApp.text.toString().trim()
            if (query.isEmpty()) {
                appendOutput("[!] Enter app name or package to pin\n")
                return
            }

            loadApps()
            val results = installedApps.filter {
                it.appName.lowercase().contains(query.lowercase()) ||
                it.packageName.lowercase().contains(query.lowercase())
            }

            if (results.isEmpty()) {
                appendOutput("[!] No matching app found\n")
                return
            }

            val app = results[0]
            currentlyPinnedPackage = app.packageName

            // Update pinned label
            tvPinnedLabel.text = "Currently Pinned: ${app.appName}"

            appendOutput("[*] Pinning: ${app.appName}\n")
            appendOutput("[*] Package: ${app.packageName}\n")

            // Try to launch with lock task
            try {
                val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

                    appendOutput("[*] Launching app...\n")
                    appendOutput("[!] startLockTask() requires Device Owner or\n")
                    appendOutput("[    activity to call it from within.\n")
                    appendOutput("[*] Alternative: Use Settings > Screen Pinning\n")
                    appendOutput("[    then pin from recents menu.\n\n")

                    try {
                        startActivity(launchIntent)
                    } catch (e: Exception) {
                        appendOutput("[E] Launch: ${e.message}\n")
                    }
                } else {
                    appendOutput("[!] No launch intent for this app\n\n")
                }
            } catch (e: Exception) {
                appendOutput("[E] Pin: ${e.message}\n")
            }

            // Save pinned app
            val prefs = requireContext().getSharedPreferences("screen_pinning", Context.MODE_PRIVATE)
            prefs.edit().putString("pinned_app", app.packageName).apply()

        } catch (e: Exception) {
            appendOutput("[E] Pin: ${e.message}\n")
        }
    }

    private fun unpinApp() {
        try {
            if (currentlyPinnedPackage == null) {
                appendOutput("[!] No app is currently pinned\n")
                return
            }

            if (switchPinProtection.isChecked) {
                val pin = etUnpinPin.text.toString().trim()
                val prefs = requireContext().getSharedPreferences("screen_pinning", Context.MODE_PRIVATE)
                val storedPin = prefs.getString("unpin_pin", "")

                if (storedPin.isNullOrEmpty()) {
                    appendOutput("[!] No unpin PIN set. Set one first.\n")
                    return
                }

                if (pin != storedPin) {
                    appendOutput("[!] Incorrect PIN\n")
                    return
                }
            }

            val prevPinned = currentlyPinnedPackage
            currentlyPinnedPackage = null
            tvPinnedLabel.text = "Currently Pinned: None"

            appendOutput("[+] Unpinned: $prevPinned\n")
            appendOutput("[*] stopLockTask() should be called from\n")
            appendOutput("    the pinned activity.\n")
            appendOutput("[*] User can also unpin via recents.\n\n")

            val prefs = requireContext().getSharedPreferences("screen_pinning", Context.MODE_PRIVATE)
            prefs.edit().remove("pinned_app").apply()
        } catch (e: Exception) {
            appendOutput("[E] Unpin: ${e.message}\n")
        }
    }

    private fun showCurrentPinned() {
        try {
            val prefs = requireContext().getSharedPreferences("screen_pinning", Context.MODE_PRIVATE)
            val pinned = prefs.getString("pinned_app", null)

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Current Pin Status            ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            if (currentlyPinnedPackage != null) {
                val pm = requireContext().packageManager
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(currentlyPinnedPackage!!, 0)).toString()
                } catch (_: Exception) { currentlyPinnedPackage }

                appendOutput("  Pinned: $appName\n")
                appendOutput("  Package: $currentlyPinnedPackage\n")
            } else if (pinned != null) {
                appendOutput("  Last pinned: $pinned\n")
                appendOutput("  Status: Not currently pinned\n")
            } else {
                appendOutput("  No app pinned\n")
            }

            appendOutput("  Auto-pin: ${switchAutoPin.isChecked}\n")
            appendOutput("  Kiosk mode: ${switchKiosk.isChecked}\n")
            appendOutput("  PIN protection: ${switchPinProtection.isChecked}\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] ${e.message}\n")
        }
    }

    private fun startKiosk() {
        try {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   KIOSK MODE                    ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            if (currentlyPinnedPackage == null) {
                appendOutput("[!] Pin an app first before starting kiosk\n")
                appendOutput("╚══════════════════════════════════╝\n\n")
                return
            }

            switchKiosk.isChecked = true

            appendOutput("[*] Kiosk mode configuration:\n")
            appendOutput("  App: $currentlyPinnedPackage\n")
            appendOutput("  Status: KIOSK ACTIVE\n\n")
            appendOutput("[*] Kiosk mode requires:\n")
            appendOutput("  - Device Owner (for lockTaskFeatures)\n")
            appendOutput("  - Or COSU (Corporate Owned Single Use)\n")
            appendOutput("  - startLockTask() from whitelisted app\n\n")
            appendOutput("[*] Implementation:\n")
            appendOutput("  1. Set Device Owner via dpm command\n")
            appendOutput("  2. setLockTaskPackages() whitelist\n")
            appendOutput("  3. Activity.startLockTask()\n")
            appendOutput("  4. DevicePolicyManager.setLockTaskFeatures()\n\n")

            val prefs = requireContext().getSharedPreferences("screen_pinning", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("kiosk_mode", true)
                .putString("kiosk_app", currentlyPinnedPackage)
                .apply()

            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Kiosk: ${e.message}\n")
        }
    }

    private fun stopKiosk() {
        try {
            switchKiosk.isChecked = false
            currentlyPinnedPackage = null
            tvPinnedLabel.text = "Currently Pinned: None"

            val prefs = requireContext().getSharedPreferences("screen_pinning", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("kiosk_mode", false)
                .remove("kiosk_app")
                .remove("pinned_app")
                .apply()

            appendOutput("[+] Kiosk mode stopped\n")
            appendOutput("[*] stopLockTask() should be called\n")
            appendOutput("[*] from the pinned activity\n\n")
        } catch (e: Exception) {
            appendOutput("[E] ${e.message}\n")
        }
    }

    private fun saveConfig() {
        try {
            val prefs = requireContext().getSharedPreferences("screen_pinning", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("auto_pin", switchAutoPin.isChecked)
                .putBoolean("kiosk_mode", switchKiosk.isChecked)
                .putBoolean("pin_protection", switchPinProtection.isChecked)
                .putString("unpin_pin", etUnpinPin.text.toString().trim())
                .putString("pinned_app", currentlyPinnedPackage)
                .apply()
            appendOutput("[+] Configuration saved\n")
        } catch (e: Exception) {
            appendOutput("[E] Save: ${e.message}\n")
        }
    }

    private fun loadConfig() {
        try {
            val prefs = requireContext().getSharedPreferences("screen_pinning", Context.MODE_PRIVATE)
            switchAutoPin.isChecked = prefs.getBoolean("auto_pin", false)
            switchKiosk.isChecked = prefs.getBoolean("kiosk_mode", false)
            switchPinProtection.isChecked = prefs.getBoolean("pin_protection", false)
            etUnpinPin.setText(prefs.getString("unpin_pin", ""))
            currentlyPinnedPackage = prefs.getString("pinned_app", null)

            if (currentlyPinnedPackage != null) {
                try {
                    val pm = requireContext().packageManager
                    val appName = pm.getApplicationLabel(
                        pm.getApplicationInfo(currentlyPinnedPackage!!, 0)
                    ).toString()
                    tvPinnedLabel.text = "Currently Pinned: $appName"
                } catch (_: Exception) {
                    tvPinnedLabel.text = "Currently Pinned: $currentlyPinnedPackage"
                }
            }
        } catch (e: Exception) {
            appendOutput("[E] Load: ${e.message}\n")
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
