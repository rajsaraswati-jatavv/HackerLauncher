package com.hackerlauncher.modules

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Browser
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
import java.io.File

class PanicButtonFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView
    private lateinit var etEmergencySms: EditText
    private lateinit var etEmergencyNumber: EditText
    private lateinit var switchBrowserHistory: Switch
    private lateinit var switchClipboard: Switch
    private lateinit var switchVaultFiles: Switch
    private lateinit var switchLockDevice: Switch
    private lateinit var switchFactoryReset: Switch
    private lateinit var switchEmergencySms: Switch
    private lateinit var switchVolumeTrigger: Switch
    private lateinit var switchPowerTrigger: Switch

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
        }

        // Title
        root.addView(TextView(context).apply {
            text = ">> PANIC BUTTON v1.1"
            setTextColor(0xFFFF0000.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        })

        // Big panic button
        val btnPanic = Button(context).apply {
            text = "!! PANIC !!"
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFFFF0000.toInt())
            textSize = 18f
            typeface = Typeface.MONOSPACE
            setPadding(16, 24, 16, 24)
            setOnClickListener { executePanic() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(4, 4, 4, 8)
            }
        }
        root.addView(btnPanic)

        // Emergency SMS settings
        root.addView(TextView(context).apply {
            text = "Emergency SMS:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 0)
        })
        etEmergencyNumber = EditText(context).apply {
            hint = "Emergency phone number"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_PHONE
            setPadding(8, 8, 8, 8)
        }
        root.addView(etEmergencyNumber)
        etEmergencySms = EditText(context).apply {
            hint = "Emergency message text"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        root.addView(etEmergencySms)

        // Action switches
        root.addView(TextView(context).apply {
            text = "Panic Actions:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 4)
        })

        switchBrowserHistory = makeSwitch("Clear Browser History", true)
        switchClipboard = makeSwitch("Clear Clipboard", true)
        switchVaultFiles = makeSwitch("Delete Vault Files", false)
        switchLockDevice = makeSwitch("Lock Device", true)
        switchFactoryReset = makeSwitch("Factory Reset Shortcut", false)
        switchEmergencySms = makeSwitch("Send Emergency SMS", false)

        root.addView(switchBrowserHistory)
        root.addView(switchClipboard)
        root.addView(switchVaultFiles)
        root.addView(switchLockDevice)
        root.addView(switchFactoryReset)
        root.addView(switchEmergencySms)

        // Trigger switches
        root.addView(TextView(context).apply {
            text = "Panic Triggers:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 4)
        })

        switchVolumeTrigger = makeSwitch("Volume Keys Triple Press", false)
        switchPowerTrigger = makeSwitch("Power Button Triple Press", false)
        root.addView(switchVolumeTrigger)
        root.addView(switchPowerTrigger)

        // Individual action buttons
        root.addView(TextView(context).apply {
            text = "Individual Actions:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 4)
        })

        val btnRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow1.addView(makeBtn("Clr History") { confirmAndRun("Clear Browser History") { clearBrowserHistory() } })
        btnRow1.addView(makeBtn("Clr Clipboard") { confirmAndRun("Clear Clipboard") { clearClipboard() } })
        btnRow1.addView(makeBtn("Lock Device") { confirmAndRun("Lock Device") { lockDevice() } })
        root.addView(btnRow1)

        val btnRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(makeBtn("Send SMS") { confirmAndRun("Send Emergency SMS") { sendEmergencySms() } })
        btnRow2.addView(makeBtn("Factory Reset") { confirmAndRun("Factory Reset") { factoryResetShortcut() } })
        btnRow2.addView(makeBtn("Del Vault") { confirmAndRun("Delete Vault Files") { deleteVaultFiles() } })
        root.addView(btnRow2)

        // Config buttons
        val btnRow3 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow3.addView(makeBtn("Save Config") { saveConfig() })
        btnRow3.addView(makeBtn("Load Config") { loadConfig() })
        btnRow3.addView(makeBtn("Test (Safe)") { testPanicSafe() })
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

        loadConfig()
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║     PANIC BUTTON v1.1           ║\n")
        appendOutput("║   Emergency actions on trigger   ║\n")
        appendOutput("║   Configure actions & triggers   ║\n")
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

    private fun confirmAndRun(actionName: String, action: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm: $actionName")
            .setMessage("Are you sure you want to: $actionName?")
            .setPositiveButton("EXECUTE") { _, _ -> action() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveConfig() {
        try {
            val prefs = requireContext().getSharedPreferences("panic_button", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("clear_browser", switchBrowserHistory.isChecked)
                .putBoolean("clear_clipboard", switchClipboard.isChecked)
                .putBoolean("delete_vault", switchVaultFiles.isChecked)
                .putBoolean("lock_device", switchLockDevice.isChecked)
                .putBoolean("factory_reset", switchFactoryReset.isChecked)
                .putBoolean("emergency_sms", switchEmergencySms.isChecked)
                .putString("sms_number", etEmergencyNumber.text.toString().trim())
                .putString("sms_message", etEmergencySms.text.toString().trim())
                .putBoolean("volume_trigger", switchVolumeTrigger.isChecked)
                .putBoolean("power_trigger", switchPowerTrigger.isChecked)
                .apply()
            appendOutput("[+] Panic config saved\n")
        } catch (e: Exception) {
            appendOutput("[E] Save: ${e.message}\n")
        }
    }

    private fun loadConfig() {
        try {
            val prefs = requireContext().getSharedPreferences("panic_button", Context.MODE_PRIVATE)
            switchBrowserHistory.isChecked = prefs.getBoolean("clear_browser", true)
            switchClipboard.isChecked = prefs.getBoolean("clear_clipboard", true)
            switchVaultFiles.isChecked = prefs.getBoolean("delete_vault", false)
            switchLockDevice.isChecked = prefs.getBoolean("lock_device", true)
            switchFactoryReset.isChecked = prefs.getBoolean("factory_reset", false)
            switchEmergencySms.isChecked = prefs.getBoolean("emergency_sms", false)
            etEmergencyNumber.setText(prefs.getString("sms_number", ""))
            etEmergencySms.setText(prefs.getString("sms_message", "EMERGENCY! I need help!"))
            switchVolumeTrigger.isChecked = prefs.getBoolean("volume_trigger", false)
            switchPowerTrigger.isChecked = prefs.getBoolean("power_trigger", false)
        } catch (e: Exception) {
            appendOutput("[E] Load: ${e.message}\n")
        }
    }

    private fun executePanic() {
        AlertDialog.Builder(requireContext())
            .setTitle("!! PANIC CONFIRMATION !!")
            .setMessage("This will execute ALL enabled panic actions.\nThis may be destructive!\n\nAre you sure?")
            .setPositiveButton("EXECUTE") { _, _ -> runPanicActions() }
            .setNegativeButton("ABORT") { _, _ -> appendOutput("[*] Panic aborted\n\n") }
            .show()
    }

    private fun runPanicActions() {
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║    !! PANIC ACTIVATED !!        ║\n")
        appendOutput("╠══════════════════════════════════╣\n\n")

        if (switchBrowserHistory.isChecked) {
            try {
                clearBrowserHistory()
                appendOutput("[+] Browser history cleared\n")
            } catch (e: Exception) {
                appendOutput("[E] Browser: ${e.message}\n")
            }
        }

        if (switchClipboard.isChecked) {
            try {
                clearClipboard()
                appendOutput("[+] Clipboard cleared\n")
            } catch (e: Exception) {
                appendOutput("[E] Clipboard: ${e.message}\n")
            }
        }

        if (switchVaultFiles.isChecked) {
            try {
                deleteVaultFiles()
            } catch (e: Exception) {
                appendOutput("[E] Vault: ${e.message}\n")
            }
        }

        if (switchLockDevice.isChecked) {
            try {
                lockDevice()
            } catch (e: Exception) {
                appendOutput("[E] Lock: ${e.message}\n")
            }
        }

        if (switchFactoryReset.isChecked) {
            try {
                factoryResetShortcut()
            } catch (e: Exception) {
                appendOutput("[E] Reset: ${e.message}\n")
            }
        }

        if (switchEmergencySms.isChecked) {
            try {
                sendEmergencySms()
            } catch (e: Exception) {
                appendOutput("[E] SMS: ${e.message}\n")
            }
        }

        appendOutput("\n╚══════════════════════════════════╝\n\n")
    }

    private fun clearBrowserHistory() {
        try {
            requireContext().contentResolver.delete(
                Browser.BOOKMARKS_URI,
                Browser.BookmarkColumns.BOOKMARK + " = 0",
                null
            )
            appendOutput("[+] Browser history cleared\n")
        } catch (e: Exception) {
            appendOutput("[*] Browser clear attempted (${e.message})\n")
        }

        try {
            val intent = Intent("com.android.browser.action.CLEAR_HISTORY")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            requireContext().startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun clearClipboard() {
        try {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (cm.hasPrimaryClip()) {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            appendOutput("[+] Clipboard cleared\n")
        } catch (e: Exception) {
            appendOutput("[E] Clipboard: ${e.message}\n")
        }
    }

    private fun deleteVaultFiles() {
        try {
            val vaultDir = File(requireContext().filesDir, "secure_vault")
            if (vaultDir.exists()) {
                val files = vaultDir.listFiles()
                var deleted = 0
                files?.forEach { if (it.delete()) deleted++ }
                appendOutput("[+] Vault: $deleted files deleted\n")
            } else {
                appendOutput("[*] No vault files found\n")
            }
        } catch (e: Exception) {
            appendOutput("[E] Vault: ${e.message}\n")
        }
    }

    private fun lockDevice() {
        try {
            val dpm = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(requireContext(), javaClass)
            if (dpm.isAdminActive(componentName)) {
                dpm.lockNow()
                appendOutput("[+] Device locked via Device Admin\n")
            } else {
                appendOutput("[!] Device Admin not active\n")
                appendOutput("[*] Use power button to lock\n")
            }
        } catch (e: Exception) {
            appendOutput("[E] Lock: ${e.message}\n")
        }
    }

    private fun factoryResetShortcut() {
        try {
            appendOutput("[*] Opening factory reset settings...\n")
            val intent = Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                appendOutput("[E] Reset: ${e2.message}\n")
            }
        }
    }

    private fun sendEmergencySms() {
        try {
            val number = etEmergencyNumber.text.toString().trim()
            val message = etEmergencySms.text.toString().trim()
            if (number.isNotEmpty() && message.isNotEmpty()) {
                val sms = android.telephony.SmsManager.getDefault()
                sms.sendTextMessage(number, null, message, null, null)
                appendOutput("[+] Emergency SMS sent to $number\n")
            } else {
                appendOutput("[!] Emergency SMS: number or message empty\n")
            }
        } catch (e: Exception) {
            appendOutput("[E] SMS: ${e.message}\n")
        }
    }

    private fun testPanicSafe() {
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║   Panic Test (Safe Mode)        ║\n")
        appendOutput("╠══════════════════════════════════╣\n\n")
        appendOutput("Actions that WOULD be executed:\n\n")

        if (switchBrowserHistory.isChecked) appendOutput("  [x] Clear Browser History\n")
        if (switchClipboard.isChecked) appendOutput("  [x] Clear Clipboard\n")
        if (switchVaultFiles.isChecked) appendOutput("  [x] Delete Vault Files\n")
        if (switchLockDevice.isChecked) appendOutput("  [x] Lock Device\n")
        if (switchFactoryReset.isChecked) appendOutput("  [x] Factory Reset Shortcut\n")
        if (switchEmergencySms.isChecked) {
            val number = etEmergencyNumber.text.toString().trim()
            appendOutput("  [x] Send SMS to: ${if (number.isNotEmpty()) number else "(not set)"}\n")
        }

        appendOutput("\nTriggers configured:\n")
        if (switchVolumeTrigger.isChecked) appendOutput("  [x] Volume Keys Triple Press\n")
        if (switchPowerTrigger.isChecked) appendOutput("  [x] Power Button Triple Press\n")
        if (!switchVolumeTrigger.isChecked && !switchPowerTrigger.isChecked) {
            appendOutput("  [ ] No triggers configured\n")
        }

        appendOutput("\n[i] No actions were actually executed\n")
        appendOutput("╚══════════════════════════════════╝\n\n")
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
