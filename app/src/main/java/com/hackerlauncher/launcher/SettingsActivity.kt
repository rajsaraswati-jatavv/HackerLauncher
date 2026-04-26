package com.hackerlauncher.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.DropDownPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.PreferenceCategory
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

// ─── Settings Activity ────────────────────────────────────────────────────────

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_IMPORT_SETTINGS = 1001
        private const val REQUEST_USAGE_ACCESS = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme
        val themeManager = ThemeManager.getInstance(this)
        window.decorView.setBackgroundColor(Color.parseColor(themeManager.currentTheme.bgColor))

        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT_SETTINGS && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val fragment = supportFragmentManager.findFragmentById(android.R.id.content) as? SettingsFragment
                fragment?.importSettings(uri)
            }
        }
    }

    // ─── Settings Fragment ─────────────────────────────────────────────────

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var themeManager: ThemeManager
        private lateinit var gestureManager: GestureManager
        private lateinit var iconPackManager: IconPackManager

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val context = preferenceManager.context
            themeManager = ThemeManager.getInstance(context)
            gestureManager = GestureManager.getInstance(context)
            iconPackManager = IconPackManager.getInstance(context)

            // Build preferences programmatically since R.xml may not exist
            buildPreferences()
        }

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
        ): View {
            val view = super.onCreateView(inflater, container, savedInstanceState)
            view?.setBackgroundColor(Color.parseColor(themeManager.currentTheme.bgColor))
            return view
        }

        // ─── Build Preferences ─────────────────────────────────────────────

        private fun buildPreferences() {
            val screen = preferenceManager.createPreferenceScreen(requireContext())
            preferenceScreen = screen

            // ── Appearance Category ─────────────────────────────────────
            val appearanceCat = PreferenceCategory(requireContext()).apply {
                title = "> APPEARANCE"
                summary = "Customize the look & feel"
            }
            screen.addPreference(appearanceCat)

            // Theme selector
            val themePref = ListPreference(requireContext()).apply {
                key = "theme_selection"
                title = "Theme"
                summary = "Select color theme"
                val themes = themeManager.getAllThemes()
                entries = themes.map { it.name }.toTypedArray()
                entryValues = themes.map { it.name }.toTypedArray()
                value = themeManager.currentTheme.name
                setOnPreferenceChangeListener { _, newValue ->
                    val theme = themes.find { it.name == newValue as String }
                    if (theme != null) {
                        themeManager.applyTheme(theme)
                        activity?.recreate()
                        true
                    } else false
                }
            }
            appearanceCat.addPreference(themePref)

            // Icon pack selector
            val iconPackPref = ListPreference(requireContext()).apply {
                key = "icon_pack"
                title = "Icon Pack"
                summary = "Select icon pack"
                val packs = iconPackManager.scanInstalledPacks()
                val packNames = mutableListOf("Default (Hacker)")
                val packValues = mutableListOf("")
                packs.forEach { pack ->
                    packNames.add("${pack.name} (${pack.iconCount})")
                    packValues.add(pack.packageName)
                }
                entries = packNames.toTypedArray()
                entryValues = packValues.toTypedArray()
                value = iconPackManager.activePack
                setOnPreferenceChangeListener { _, newValue ->
                    val pkg = newValue as String
                    if (pkg.isEmpty()) {
                        iconPackManager.clearPack()
                    } else {
                        iconPackManager.applyPack(pkg)
                    }
                    true
                }
            }
            appearanceCat.addPreference(iconPackPref)

            // Wallpaper toggle
            val wallpaperPref = SwitchPreferenceCompat(requireContext()).apply {
                key = "wallpaper_enabled"
                title = "Matrix Wallpaper"
                summary = "Enable hacker-style live wallpaper"
                isChecked = themeManager.isWallpaperEnabled()
                setOnPreferenceChangeListener { _, newValue ->
                    themeManager.setWallpaperEnabled(newValue as Boolean)
                    true
                }
            }
            appearanceCat.addPreference(wallpaperPref)

            // Font size
            val fontSizePref = DropDownPreference(requireContext()).apply {
                key = "font_size"
                title = "Font Size"
                summary = "Adjust text size"
                entries = arrayOf("Small", "Medium", "Large", "XL")
                entryValues = arrayOf("12", "14", "16", "18")
                value = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString("font_size", "14")
                setOnPreferenceChangeListener { _, newValue ->
                    summary = "Size: $newValue"
                    true
                }
            }
            appearanceCat.addPreference(fontSizePref)

            // ── Home Screen Category ────────────────────────────────────
            val homeCat = PreferenceCategory(requireContext()).apply {
                title = "> HOME_SCREEN"
                summary = "Configure home screen behavior"
            }
            screen.addPreference(homeCat)

            // Grid columns
            val gridPref = ListPreference(requireContext()).apply {
                key = "grid_columns"
                title = "Grid Columns"
                summary = "Number of app columns"
                entries = arrayOf("3 Columns", "4 Columns", "5 Columns", "6 Columns")
                entryValues = arrayOf("3", "4", "5", "6")
                value = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString("grid_columns", "4")
                setOnPreferenceChangeListener { _, newValue ->
                    summary = "${newValue} columns"
                    true
                }
            }
            homeCat.addPreference(gridPref)

            // Show dock
            val dockPref = SwitchPreferenceCompat(requireContext()).apply {
                key = "show_dock"
                title = "Show Dock"
                summary = "Display dock bar at bottom"
                isChecked = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getBoolean("show_dock", true)
                setOnPreferenceChangeListener { _, _ -> true }
            }
            homeCat.addPreference(dockPref)

            // Show status bar
            val statusBarPref = SwitchPreferenceCompat(requireContext()).apply {
                key = "show_status_bar"
                title = "Show Status Bar"
                summary = "Display status bar"
                isChecked = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getBoolean("show_status_bar", true)
                setOnPreferenceChangeListener { _, _ -> true }
            }
            homeCat.addPreference(statusBarPref)

            // ── Gestures Category ───────────────────────────────────────
            val gestureCat = PreferenceCategory(requireContext()).apply {
                title = "> GESTURES"
                summary = "Configure gesture actions"
            }
            screen.addPreference(gestureCat)

            GestureType.values().forEach { gesture ->
                val gesturePref = ListPreference(requireContext()).apply {
                    key = "gesture_${gesture.name}"
                    title = gesture.label
                    val current = gestureManager.getGestureAction(gesture)
                    entries = ActionType.values().map { it.label }.toTypedArray()
                    entryValues = ActionType.values().map { it.name }.toTypedArray()
                    value = current?.action?.name ?: ActionType.NONE.name
                    summary = current?.action?.label ?: "None"
                    setOnPreferenceChangeListener { _, newValue ->
                        val actionType = ActionType.valueOf(newValue as String)
                        gestureManager.setGestureAction(
                            gesture,
                            GestureAction(gesture, actionType)
                        )
                        summary = actionType.label
                        true
                    }
                }
                gestureCat.addPreference(gesturePref)
            }

            // Sensitivity
            val sensitivityPref = DropDownPreference(requireContext()).apply {
                key = "gesture_sensitivity"
                title = "Gesture Sensitivity"
                entries = arrayOf("Low (0.5x)", "Medium (1.0x)", "High (1.5x)", "Max (2.0x)")
                entryValues = arrayOf("0.5", "1.0", "1.5", "2.0")
                value = gestureManager.sensitivity.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    gestureManager.setSensitivity((newValue as String).toFloat())
                    true
                }
            }
            gestureCat.addPreference(sensitivityPref)

            // ── Security Category ───────────────────────────────────────
            val securityCat = PreferenceCategory(requireContext()).apply {
                title = "> SECURITY"
                summary = "App lock & privacy settings"
            }
            screen.addPreference(securityCat)

            // App lock config
            val appLockPref = Preference(requireContext()).apply {
                key = "app_lock_config"
                title = "App Lock"
                summary = "Configure locked apps & PIN"
                setOnPreferenceClickListener {
                    openAppLockConfig()
                    true
                }
            }
            securityCat.addPreference(appLockPref)

            // Set PIN
            val pinPref = Preference(requireContext()).apply {
                key = "set_pin"
                title = "Set PIN"
                summary = "Change lock PIN code"
                setOnPreferenceClickListener {
                    showPinDialog()
                    true
                }
            }
            securityCat.addPreference(pinPref)

            // Fake crash
            val fakeCrashPref = SwitchPreferenceCompat(requireContext()).apply {
                key = "fake_crash"
                title = "Fake Crash Dialog"
                summary = "Show fake crash instead of lock screen"
                isChecked = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getBoolean("fake_crash_enabled", false)
                setOnPreferenceChangeListener { _, newValue ->
                    val prefs = requireContext().getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("fake_crash_enabled", newValue as Boolean).apply()
                    true
                }
            }
            securityCat.addPreference(fakeCrashPref)

            // Intruder photo
            val intruderPref = SwitchPreferenceCompat(requireContext()).apply {
                key = "intruder_photo"
                title = "Intruder Photo"
                summary = "Take photo on wrong PIN attempt"
                isChecked = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getBoolean("intruder_photo_enabled", false)
                setOnPreferenceChangeListener { _, newValue ->
                    val prefs = requireContext().getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("intruder_photo_enabled", newValue as Boolean).apply()
                    true
                }
            }
            securityCat.addPreference(intruderPref)

            // Lock timeout
            val timeoutPref = ListPreference(requireContext()).apply {
                key = "lock_timeout"
                title = "Re-lock Timeout"
                entries = arrayOf("1 min", "3 min", "5 min", "10 min", "30 min")
                entryValues = arrayOf("1", "3", "5", "10", "30")
                value = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString("timeout_minutes", "5")
                summary = "Re-lock after ${PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString("timeout_minutes", "5")} minutes"
                setOnPreferenceChangeListener { _, newValue ->
                    val prefs = requireContext().getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("timeout_minutes", (newValue as String).toInt()).apply()
                    summary = "Re-lock after $newValue minutes"
                    true
                }
            }
            securityCat.addPreference(timeoutPref)

            // ── Hidden Apps ─────────────────────────────────────────────
            val hiddenCat = PreferenceCategory(requireContext()).apply {
                title = "> HIDDEN_APPS"
                summary = "Manage hidden applications"
            }
            screen.addPreference(hiddenCat)

            val hiddenAppsPref = Preference(requireContext()).apply {
                key = "hidden_apps"
                title = "Manage Hidden Apps"
                summary = "Select apps to hide from launcher"
                setOnPreferenceClickListener {
                    openHiddenAppsManager()
                    true
                }
            }
            hiddenCat.addPreference(hiddenAppsPref)

            // ── Data Category ───────────────────────────────────────────
            val dataCat = PreferenceCategory(requireContext()).apply {
                title = "> DATA"
                summary = "Import, export & reset"
            }
            screen.addPreference(dataCat)

            // Export settings
            val exportPref = Preference(requireContext()).apply {
                key = "export_settings"
                title = "Export Settings"
                summary = "Save all settings to JSON file"
                setOnPreferenceClickListener {
                    exportSettings()
                    true
                }
            }
            dataCat.addPreference(exportPref)

            // Import settings
            val importPref = Preference(requireContext()).apply {
                key = "import_settings"
                title = "Import Settings"
                summary = "Load settings from JSON file"
                setOnPreferenceClickListener {
                    importSettings()
                    true
                }
            }
            dataCat.addPreference(importPref)

            // Reset all
            val resetPref = Preference(requireContext()).apply {
                key = "reset_all"
                title = "Reset All Settings"
                summary = "Restore default configuration"
                setOnPreferenceClickListener {
                    showResetConfirmDialog()
                    true
                }
            }
            dataCat.addPreference(resetPref)

            // ── System Category ─────────────────────────────────────────
            val systemCat = PreferenceCategory(requireContext()).apply {
                title = "> SYSTEM"
                summary = "System integration"
            }
            screen.addPreference(systemCat)

            // Default launcher
            val launcherPref = Preference(requireContext()).apply {
                key = "default_launcher"
                title = "Set Default Launcher"
                summary = "Set HackerLauncher as default"
                setOnPreferenceClickListener {
                    openDefaultLauncherSettings()
                    true
                }
            }
            systemCat.addPreference(launcherPref)

            // Usage access
            val usagePref = Preference(requireContext()).apply {
                key = "usage_access"
                title = "Usage Access"
                summary = "Grant usage stats permission"
                setOnPreferenceClickListener {
                    openUsageAccessSettings()
                    true
                }
            }
            systemCat.addPreference(usagePref)

            // System settings
            val sysSettingsPref = Preference(requireContext()).apply {
                key = "system_settings"
                title = "Android Settings"
                summary = "Open system settings"
                setOnPreferenceClickListener {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                    true
                }
            }
            systemCat.addPreference(sysSettingsPref)

            // ── About Category ──────────────────────────────────────────
            val aboutCat = PreferenceCategory(requireContext()).apply {
                title = "> ABOUT"
                summary = "Application info"
            }
            screen.addPreference(aboutCat)

            val versionPref = Preference(requireContext()).apply {
                key = "version_info"
                title = "Version"
                summary = try {
                    val pi = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
                    "HackerLauncher v${pi.versionName} (build ${if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode})"
                } catch (_: Exception) { "HackerLauncher v1.0" }
                isSelectable = false
            }
            aboutCat.addPreference(versionPref)

            val ossPref = Preference(requireContext()).apply {
                key = "open_source"
                title = "Open Source Licenses"
                summary = "View third-party licenses"
                setOnPreferenceClickListener {
                    showLicensesDialog()
                    true
                }
            }
            aboutCat.addPreference(ossPref)
        }

        // ─── Summary Providers ─────────────────────────────────────────────

        private fun updateListPreferenceSummary(pref: ListPreference, value: String?) {
            val index = pref.findIndexOfValue(value)
            if (index >= 0) {
                pref.summary = pref.entries[index]
            }
        }

        // ─── App Lock Config ───────────────────────────────────────────────

        private fun openAppLockConfig() {
            val ctx = requireContext()
            val prefs = ctx.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
            val lockedApps = AppLockService.Companion::class.java // Access companion

            // Build dialog with installed apps
            val pm = ctx.packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { it.packageName != ctx.packageName }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

            val lockedJson = prefs.getString("locked_apps", null)
            val lockedSet = mutableSetOf<String>()
            if (lockedJson != null) {
                try {
                    val arr = org.json.JSONArray(lockedJson)
                    for (i in 0 until arr.length()) lockedSet.add(arr.getString(i))
                } catch (_: Exception) { }
            }

            val appNames = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
            val checked = apps.map { it.packageName in lockedSet }.toBooleanArray()

            AlertDialog.Builder(ctx)
                .setTitle("> LOCKED_APPS")
                .setMultiChoiceItems(appNames, checked) { _, which, isChecked ->
                    if (isChecked) {
                        AppLockService.addLockedApp(prefs, apps[which].packageName)
                    } else {
                        AppLockService.removeLockedApp(prefs, apps[which].packageName)
                    }
                }
                .setPositiveButton("DONE", null)
                .show()
        }

        // ─── PIN Dialog ────────────────────────────────────────────────────

        private fun showPinDialog() {
            val ctx = requireContext()
            val prefs = ctx.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)

            val input = android.widget.EditText(ctx).apply {
                hint = "Enter new 4-digit PIN"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setTextColor(Color.parseColor("#00FF00"))
                setHintTextColor(Color.GRAY)
                setBackgroundColor(Color.parseColor("#1A1A1A"))
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(48, 24, 48, 16)
            }

            AlertDialog.Builder(ctx)
                .setTitle("> SET_PIN")
                .setView(input)
                .setPositiveButton("SET") { _, _ ->
                    val pin = input.text.toString().trim()
                    if (pin.length >= 4) {
                        AppLockService.setPin(prefs, pin)
                        Toast.makeText(ctx, "> PIN_UPDATED", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "> PIN_MUST_BE_4+_DIGITS", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }

        // ─── Hidden Apps Manager ───────────────────────────────────────────

        private fun openHiddenAppsManager() {
            val ctx = requireContext()
            val prefs = ctx.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            val pm = ctx.packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { it.packageName != ctx.packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

            val hiddenJson = prefs.getString("hidden_apps", null)
            val hiddenSet = mutableSetOf<String>()
            if (hiddenJson != null) {
                try {
                    val arr = org.json.JSONArray(hiddenJson)
                    for (i in 0 until arr.length()) hiddenSet.add(arr.getString(i))
                } catch (_: Exception) { }
            }

            val appNames = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
            val checked = apps.map { it.packageName in hiddenSet }.toBooleanArray()

            AlertDialog.Builder(ctx)
                .setTitle("> HIDDEN_APPS")
                .setMultiChoiceItems(appNames, checked) { _, which, isChecked ->
                    if (isChecked) {
                        hiddenSet.add(apps[which].packageName)
                    } else {
                        hiddenSet.remove(apps[which].packageName)
                    }
                }
                .setPositiveButton("SAVE") { _, _ ->
                    val arr = org.json.JSONArray()
                    hiddenSet.forEach { arr.put(it) }
                    prefs.edit().putString("hidden_apps", arr.toString()).apply()
                    Toast.makeText(ctx, "> HIDDEN_APPS_UPDATED", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }

        // ─── Export Settings ───────────────────────────────────────────────

        private fun exportSettings() {
            val ctx = requireContext()
            val allPrefs = PreferenceManager.getDefaultSharedPreferences(ctx).all
            val root = JSONObject()

            allPrefs.forEach { (key, value) ->
                when (value) {
                    is String -> root.put(key, value)
                    is Int -> root.put(key, value)
                    is Boolean -> root.put(key, value)
                    is Float -> root.put(key, value)
                    is Long -> root.put(key, value)
                }
            }

            // Also export theme
            root.put("__theme__", themeManager.currentTheme.toJson())

            // Also export gesture mappings
            val gesturesObj = JSONObject()
            gestureManager.getAllMappings().forEach { (type, action) ->
                gesturesObj.put(type.name, action.toJson())
            }
            root.put("__gestures__", gesturesObj)

            try {
                val dir = File(ctx.getExternalFilesDir(null), "exports")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "hackerlauncher_settings.json")
                file.writeText(root.toString(2))
                Toast.makeText(ctx, "> EXPORTED: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "> EXPORT_FAILED: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // ─── Import Settings ───────────────────────────────────────────────

        private fun importSettings() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, 1001)
        }

        fun importSettings(uri: Uri) {
            val ctx = requireContext()
            try {
                val inputStream = ctx.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val json = reader.readText()
                reader.close()

                val root = JSONObject(json)
                val editor = PreferenceManager.getDefaultSharedPreferences(ctx).edit()

                root.keys().forEach { key ->
                    if (key.startsWith("__")) return@forEach
                    val value = root.get(key)
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Long -> editor.putLong(key, value)
                    }
                }
                editor.apply()

                // Apply theme
                if (root.has("__theme__")) {
                    val theme = Theme.fromJson(root.getJSONObject("__theme__"))
                    themeManager.applyTheme(theme)
                }

                // Apply gestures
                if (root.has("__gestures__")) {
                    val gesturesObj = root.getJSONObject("__gestures__")
                    gesturesObj.keys().forEach { gestureName ->
                        try {
                            val type = GestureType.valueOf(gestureName)
                            val action = GestureAction.fromJson(gesturesObj.getJSONObject(gestureName))
                            gestureManager.setGestureAction(type, action)
                        } catch (_: Exception) { }
                    }
                }

                Toast.makeText(ctx, "> SETTINGS_IMPORTED", Toast.LENGTH_LONG).show()
                activity?.recreate()
            } catch (e: Exception) {
                Toast.makeText(ctx, "> IMPORT_FAILED: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // ─── Reset Settings ────────────────────────────────────────────────

        private fun showResetConfirmDialog() {
            AlertDialog.Builder(requireContext())
                .setTitle("> RESET_CONFIRM")
                .setMessage("This will reset ALL settings to defaults. Continue?")
                .setPositiveButton("RESET") { _, _ ->
                    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().clear().apply()
                    themeManager.applyTheme(HackerThemes.MATRIX_GREEN)
                    gestureManager.resetToDefaults()
                    iconPackManager.resetToDefault()
                    Toast.makeText(requireContext(), "> SETTINGS_RESET", Toast.LENGTH_SHORT).show()
                    activity?.recreate()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }

        // ─── System Settings Helpers ───────────────────────────────────────

        private fun openDefaultLauncherSettings() {
            try {
                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            }
        }

        private fun openUsageAccessSettings() {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "> CANNOT_OPEN_USAGE_SETTINGS", Toast.LENGTH_SHORT).show()
            }
        }

        // ─── Licenses ──────────────────────────────────────────────────────

        private fun showLicensesDialog() {
            AlertDialog.Builder(requireContext())
                .setTitle("> OPEN_SOURCE_LICENSES")
                .setMessage(
                    "HackerLauncher uses the following open source libraries:\n\n" +
                    "• AndroidX Libraries - Apache 2.0\n" +
                    "• Kotlin Coroutines - Apache 2.0\n" +
                    "• Material Components - Apache 2.0\n" +
                    "• Biometric Prompt - Apache 2.0\n" +
                    "• AndroidSVG - Apache 2.0\n"
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
