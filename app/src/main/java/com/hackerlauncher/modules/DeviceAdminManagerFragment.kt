package com.hackerlauncher.modules

import android.app.admin.DeviceAdminInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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

class DeviceAdminManagerFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
        }

        // Title
        root.addView(TextView(context).apply {
            text = ">> DEVICE ADMIN MANAGER v1.1"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        })

        // Note about BIND_DEVICE_ADMIN
        root.addView(TextView(context).apply {
            text = "[i] Requires BIND_DEVICE_ADMIN in manifest"
            setTextColor(0xFFFFFF00.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, 2, 0, 4)
        })

        // Buttons row 1
        val btnRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow1.addView(makeBtn("List Admins") { listDeviceAdmins() })
        btnRow1.addView(makeBtn("Active Admins") { listActiveAdmins() })
        btnRow1.addView(makeBtn("Activate") { activateAdmin() })
        root.addView(btnRow1)

        // Buttons row 2
        val btnRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(makeBtn("Deactivate") { deactivateAdmin() })
        btnRow2.addView(makeBtn("Password Policy") { showPasswordPolicy() })
        btnRow2.addView(makeBtn("Encryption") { showEncryptionStatus() })
        root.addView(btnRow2)

        // Buttons row 3
        val btnRow3 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow3.addView(makeBtn("Lock Options") { showLockOptions() })
        btnRow3.addView(makeBtn("Admin Info") { showAdminInfo() })
        btnRow3.addView(makeBtn("Capabilities") { showCapabilities() })
        root.addView(btnRow3)

        // Buttons row 4
        val btnRow4 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow4.addView(makeBtn("Remove Admin") { removeAdmin() })
        btnRow4.addView(makeBtn("Device Info") { showDeviceInfo() })
        btnRow4.addView(makeBtn("Screen Lock") { showScreenLockOptions() })
        root.addView(btnRow4)

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
        appendOutput("║  DEVICE ADMIN MANAGER v1.1      ║\n")
        appendOutput("║  Manage device admin apps       ║\n")
        appendOutput("║  Password policies, encryption  ║\n")
        appendOutput("║  Lock screen, admin control     ║\n")
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

    private fun getDpm(): DevicePolicyManager {
        return requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private fun listDeviceAdmins() {
        try {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   All Device Admin Apps         ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            val dpm = getDpm()
            val pm = requireContext().packageManager
            val activeAdmins = dpm.activeAdmins

            // Show active admins with enabled status
            appendOutput("[Active Device Admins]\n\n")
            if (activeAdmins.isNullOrEmpty()) {
                appendOutput("  No active device admins\n\n")
            } else {
                for ((idx, componentName) in activeAdmins.withIndex()) {
                    val pkg = componentName.packageName
                    val cls = componentName.className
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) { pkg }

                    val status = if (dpm.isAdminActive(componentName)) "ENABLED" else "DISABLED"
                    appendOutput("  ${idx + 1}. $appName\n")
                    appendOutput("     Pkg: $pkg\n")
                    appendOutput("     Class: ${cls.substringAfterLast(".")}\n")
                    appendOutput("     Status: $status\n")

                    // Check admin capabilities
                    try {
                        val adminInfo = dpm.getAdminInfo(componentName)
                        if (adminInfo != null) {
                            appendOutput("     Policies:\n")
                            if (adminInfo.usesEncryptedStorage()) appendOutput("       - Encrypted storage\n")
                            if (adminInfo.usesForceLock()) appendOutput("       - Force lock\n")
                            if (adminInfo.usesWipeData()) appendOutput("       - Wipe data\n")
                            if (adminInfo.usesResetPassword()) appendOutput("       - Reset password\n")
                            if (adminInfo.usesLimitPassword()) appendOutput("       - Limit password\n")
                            if (adminInfo.usesWatchLogin()) appendOutput("       - Watch login\n")
                            if (adminInfo.usesDisableCamera()) appendOutput("       - Disable camera\n")
                            if (adminInfo.usesDisableKeyguardFeatures()) appendOutput("       - Disable keyguard\n")
                        }
                    } catch (e: Exception) {
                        appendOutput("     [!] Info unavailable\n")
                    }
                    appendOutput("\n")
                }
            }

            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] List: ${e.message}\n")
        }
    }

    private fun listActiveAdmins() {
        try {
            val dpm = getDpm()
            val pm = requireContext().packageManager
            val activeAdmins = dpm.activeAdmins

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Active Device Admins          ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            if (activeAdmins.isNullOrEmpty()) {
                appendOutput("  No active device administrators\n")
            } else {
                for ((idx, componentName) in activeAdmins.withIndex()) {
                    val pkg = componentName.packageName
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) { pkg }

                    appendOutput("  ${idx + 1}. $appName\n")
                    appendOutput("     Package: $pkg\n")
                    appendOutput("     Component: ${componentName.className}\n")

                    // Check if it's device owner
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            val isDeviceOwner = dpm.isDeviceOwnerApp(pkg)
                            val isProfileOwner = dpm.isProfileOwnerApp(pkg)
                            if (isDeviceOwner) appendOutput("     Role: DEVICE OWNER\n")
                            else if (isProfileOwner) appendOutput("     Role: PROFILE OWNER\n")
                            else appendOutput("     Role: Admin\n")
                        } catch (e: Exception) {
                            appendOutput("     Role: Admin\n")
                        }
                    }
                    appendOutput("\n")
                }
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Active: ${e.message}\n")
        }
    }

    private fun activateAdmin() {
        try {
            appendOutput("[*] Activating device admin...\n")

            val componentName = ComponentName(requireContext(), "com.hackerlauncher.receiver.DeviceAdminReceiver")

            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Hacker Launcher requires device admin for:\n" +
                "- Remote lock\n- Password policies\n- Factory reset\n- Screen pinning")

            try {
                startActivity(intent)
                appendOutput("[*] Device admin activation dialog opened\n")
                appendOutput("[*] User must confirm activation\n\n")
            } catch (e: Exception) {
                appendOutput("[E] Activate: ${e.message}\n")
                appendOutput("[*] DeviceAdminReceiver must be declared in manifest\n")
                appendOutput("[*] with BIND_DEVICE_ADMIN permission\n\n")
            }
        } catch (e: Exception) {
            appendOutput("[E] ${e.message}\n")
        }
    }

    private fun deactivateAdmin() {
        try {
            val dpm = getDpm()
            val activeAdmins = dpm.activeAdmins

            if (activeAdmins.isNullOrEmpty()) {
                appendOutput("[!] No active admins to deactivate\n")
                return
            }

            val componentName = ComponentName(requireContext(), "com.hackerlauncher.receiver.DeviceAdminReceiver")

            if (dpm.isAdminActive(componentName)) {
                dpm.removeActiveAdmin(componentName)
                appendOutput("[+] Deactivated: ${componentName.className}\n\n")
            } else {
                appendOutput("[!] Our admin is not active\n")
                appendOutput("[*] To deactivate other admins:\n")
                appendOutput("[*] Settings > Security > Device Admin Apps\n\n")

                try {
                    val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                    startActivity(intent)
                    appendOutput("[*] Opened security settings\n\n")
                } catch (e: Exception) {
                    appendOutput("[E] Settings: ${e.message}\n")
                }
            }
        } catch (e: Exception) {
            appendOutput("[E] Deactivate: ${e.message}\n")
        }
    }

    private fun showPasswordPolicy() {
        try {
            val dpm = getDpm()

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Password Policies             ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            val pwQuality = when (dpm.getPasswordQuality(null)) {
                DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED -> "UNSPECIFIED"
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING -> "SOMETHING (pattern/pin/password)"
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC -> "NUMERIC"
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX -> "NUMERIC_COMPLEX"
                DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC -> "ALPHABETIC"
                DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC -> "ALPHANUMERIC"
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX -> "COMPLEX"
                else -> "UNKNOWN"
            }
            appendOutput("  Quality: $pwQuality\n")

            appendOutput("  Min length: ${dpm.getPasswordMinimumLength(null)}\n")
            appendOutput("  Min letters: ${dpm.getPasswordMinimumLetters(null)}\n")
            appendOutput("  Min lowercase: ${dpm.getPasswordMinimumLowerCase(null)}\n")
            appendOutput("  Min uppercase: ${dpm.getPasswordMinimumUpperCase(null)}\n")
            appendOutput("  Min numeric: ${dpm.getPasswordMinimumNumeric(null)}\n")
            appendOutput("  Min symbols: ${dpm.getPasswordMinimumSymbols(null)}\n")
            appendOutput("  Min non-letter: ${dpm.getPasswordMinimumNonLetter(null)}\n")
            appendOutput("  History length: ${dpm.getPasswordHistoryLength(null)}\n")

            try {
                appendOutput("  Expiration timeout: ${dpm.getPasswordExpirationTimeout(null)}ms\n")
                appendOutput("  Expiration date: ${dpm.getPasswordExpiration(null)}\n")
            } catch (e: Exception) {
                appendOutput("  Expiration: N/A\n")
            }

            appendOutput("  Max failed pw for wipe: ${dpm.getMaximumFailedPasswordsForWipe(null)}\n")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val complexity = when (dpm.getPasswordComplexity()) {
                        DevicePolicyManager.PASSWORD_COMPLEXITY_NONE -> "NONE"
                        DevicePolicyManager.PASSWORD_COMPLEXITY_LOW -> "LOW"
                        DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM -> "MEDIUM"
                        DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH -> "HIGH"
                        else -> "UNKNOWN"
                    }
                    appendOutput("  Complexity (API 29): $complexity\n")
                } catch (_: Exception) {}
            }

            appendOutput("\n╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Password: ${e.message}\n")
        }
    }

    private fun showEncryptionStatus() {
        try {
            val dpm = getDpm()

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Encryption Status             ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            val encryptionStatus = when (dpm.getStorageEncryptionStatus()) {
                DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> "UNSUPPORTED"
                DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> "INACTIVE"
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING -> "ACTIVATING"
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE -> "ACTIVE"
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> "ACTIVE (default key)"
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> "ACTIVE (per user)"
                else -> "UNKNOWN"
            }
            appendOutput("  Storage encryption: $encryptionStatus\n")

            try {
                appendOutput("  Encryption requested: ${dpm.getStorageEncryption(null)}\n")
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    appendOutput("  FBE supported: ${dpm.isFileBasedEncryptionEnabled()}\n")
                } catch (_: Exception) {}
            }

            appendOutput("\n  [*] Modern Android uses encryption by default\n")
            appendOutput("  [*] File-Based Encryption (FBE) since Android 7\n")
            appendOutput("  [*] Full-Disk Encryption (FDE) on older devices\n")

            appendOutput("\n╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Encryption: ${e.message}\n")
        }
    }

    private fun showLockOptions() {
        try {
            val dpm = getDpm()

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Lock Screen Options           ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            val activeAdmin = dpm.activeAdmins?.firstOrNull()
            if (activeAdmin != null) {
                appendOutput("  Active admin: ${activeAdmin.packageName}\n")
                appendOutput("  Can lock: ${dpm.isAdminActive(activeAdmin)}\n")

                try {
                    val timeout = dpm.getMaximumTimeToLock(null)
                    appendOutput("  Max time to lock: ${if (timeout == 0L) "None" else "${timeout / 1000}s"}\n")
                } catch (_: Exception) {}

                try {
                    val disabled = dpm.getKeyguardDisabledFeatures(null)
                    val features = mutableListOf<String>()
                    if ((disabled and DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL) != 0) features.add("ALL")
                    if ((disabled and DevicePolicyManager.KEYGUARD_DISABLE_WIDGETS_ALL) != 0) features.add("Widgets")
                    if ((disabled and DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA) != 0) features.add("Secure Camera")
                    if ((disabled and DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS) != 0) features.add("Secure Notifications")
                    if ((disabled and DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS) != 0) features.add("Unredacted Notifications")
                    if ((disabled and DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS) != 0) features.add("Trust Agents")
                    if ((disabled and DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT) != 0) features.add("Fingerprint")
                    if ((disabled and DevicePolicyManager.KEYGUARD_DISABLE_FACE) != 0) features.add("Face Unlock")
                    if ((disabled and DevicePolicyManager.KEYGUARD_DISABLE_IRIS) != 0) features.add("Iris Unlock")

                    appendOutput("  Keyguard disabled:\n")
                    if (features.isEmpty()) {
                        appendOutput("    (none)\n")
                    } else {
                        for (f in features) appendOutput("    - $f\n")
                    }
                } catch (_: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val lockTaskFeatures = dpm.getLockTaskFeatures(activeAdmin)
                        appendOutput("  Lock task features: $lockTaskFeatures\n")
                    } catch (_: Exception) {}
                }
            } else {
                appendOutput("  No active device admin\n")
            }

            appendOutput("\n  Available lock actions:\n")
            appendOutput("  - lockNow() - Immediate lock\n")
            appendOutput("  - resetPassword() - Change PIN/password\n")
            appendOutput("  - setMaximumTimeToLock() - Auto-lock timeout\n")
            appendOutput("  - setKeyguardDisabledFeatures() - Lock screen config\n")
            appendOutput("  - setLockTaskPackages() - Screen pinning\n")

            appendOutput("\n╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Lock: ${e.message}\n")
        }
    }

    private fun showAdminInfo() {
        try {
            val dpm = getDpm()
            val activeAdmins = dpm.activeAdmins

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Device Admin Info             ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            if (activeAdmins.isNullOrEmpty()) {
                appendOutput("  No active device administrators\n\n")
            } else {
                for (componentName in activeAdmins) {
                    try {
                        val adminInfo = dpm.getAdminInfo(componentName)
                        if (adminInfo != null) {
                            appendOutput("  [${componentName.packageName}]\n")
                            appendOutput("  Component: ${componentName.className}\n")

                            try { appendOutput("  Label: ${adminInfo.loadLabel(requireContext().packageManager)}\n") } catch (_: Exception) {}
                            try { appendOutput("  Description: ${adminInfo.loadDescription(requireContext().packageManager)}\n") } catch (_: Exception) {}

                            appendOutput("  Uses:\n")
                            if (adminInfo.usesEncryptedStorage()) appendOutput("    - Encrypted Storage\n")
                            if (adminInfo.usesForceLock()) appendOutput("    - Force Lock\n")
                            if (adminInfo.usesWipeData()) appendOutput("    - Wipe Data\n")
                            if (adminInfo.usesResetPassword()) appendOutput("    - Reset Password\n")
                            if (adminInfo.usesLimitPassword()) appendOutput("    - Limit Password\n")
                            if (adminInfo.usesWatchLogin()) appendOutput("    - Watch Login\n")
                            if (adminInfo.usesDisableCamera()) appendOutput("    - Disable Camera\n")
                            if (adminInfo.usesDisableKeyguardFeatures()) appendOutput("    - Disable Keyguard\n")

                            appendOutput("\n")
                        }
                    } catch (e: Exception) {
                        appendOutput("  [E] ${componentName.packageName}: ${e.message}\n\n")
                    }
                }
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Info: ${e.message}\n")
        }
    }

    private fun showCapabilities() {
        try {
            val dpm = getDpm()

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Device Admin Capabilities     ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            val ourComponent = ComponentName(requireContext(), "com.hackerlauncher.receiver.DeviceAdminReceiver")
            val isAdmin = dpm.isAdminActive(ourComponent)

            appendOutput("  Our app is admin: $isAdmin\n\n")

            appendOutput("  Available capabilities:\n")
            appendOutput("  - lockNow(): Lock screen immediately\n")
            appendOutput("  - resetPassword(): Set new lock credential\n")
            appendOutput("  - wipeData(): Factory reset\n")
            appendOutput("  - wipeData(WIPE_EXTERNAL_STORAGE): Clear SD\n")
            appendOutput("  - setMaximumTimeToLock(): Auto-lock timeout\n")
            appendOutput("  - setPasswordQuality(): Enforce pw rules\n")
            appendOutput("  - setCameraDisabled(): Disable camera\n")
            appendOutput("  - setKeyguardDisabledFeatures(): Lock screen\n")
            appendOutput("  - setLockTaskPackages(): Screen pinning\n")
            appendOutput("  - setApplicationHidden(): Hide apps\n")
            appendOutput("  - setUninstallBlocked(): Prevent uninstall\n")
            appendOutput("  - setPermittedInputMethods(): Keyboard control\n")
            appendOutput("  - setNetworkLoggingEnabled(): Network logs\n")
            appendOutput("  - setSecurityLoggingEnabled(): Security logs\n")
            appendOutput("  - requestBugreport(): Get bug report\n")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val isDeviceOwner = dpm.isDeviceOwnerApp(requireContext().packageName)
                    appendOutput("\n  Device Owner: $isDeviceOwner\n")
                    if (isDeviceOwner) {
                        appendOutput("  Additional Device Owner capabilities:\n")
                        appendOutput("  - setLockTaskPackages()\n")
                        appendOutput("  - setGlobalSetting()\n")
                        appendOutput("  - setSecureSetting()\n")
                        appendOutput("  - installCaCert()\n")
                        appendOutput("  - setApplicationHidden()\n")
                        appendOutput("  - enableSystemApp()\n")
                        appendOutput("  - setAutoTimeRequired()\n")
                    }
                } catch (_: Exception) {}
            }

            appendOutput("\n╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Capabilities: ${e.message}\n")
        }
    }

    private fun removeAdmin() {
        try {
            val dpm = getDpm()
            val activeAdmins = dpm.activeAdmins

            if (activeAdmins.isNullOrEmpty()) {
                appendOutput("[!] No active admins\n")
                return
            }

            val ourComponent = ComponentName(requireContext(), "com.hackerlauncher.receiver.DeviceAdminReceiver")
            if (dpm.isAdminActive(ourComponent)) {
                dpm.removeActiveAdmin(ourComponent)
                appendOutput("[+] Removed our device admin\n\n")
            } else {
                appendOutput("[!] Can only remove our own admin\n")
                appendOutput("[*] For other admins, use:\n")
                appendOutput("[*] Settings > Security > Device Admin Apps\n\n")

                try {
                    val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            appendOutput("[E] Remove: ${e.message}\n")
        }
    }

    private fun showDeviceInfo() {
        try {
            val dpm = getDpm()

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Device Admin Status           ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            val activeAdmins = dpm.activeAdmins
            appendOutput("  Active admins: ${activeAdmins?.size ?: 0}\n")

            try {
                val isSufficient = dpm.isActivePasswordSufficient()
                appendOutput("  Password sufficient: $isSufficient\n")
            } catch (e: Exception) {
                appendOutput("  Password status: N/A (no admin)\n")
            }

            try {
                val failedAttempts = dpm.getCurrentFailedPasswordAttempts()
                appendOutput("  Failed pw attempts: $failedAttempts\n")
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val deviceOwner = dpm.deviceOwner
                    appendOutput("  Device owner: ${deviceOwner ?: "None"}\n")
                } catch (_: Exception) {
                    appendOutput("  Device owner: N/A\n")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val profileOwner = dpm.profileOwner
                    appendOutput("  Profile owner: ${profileOwner ?: "None"}\n")
                } catch (_: Exception) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val policy = dpm.systemUpdatePolicy
                    appendOutput("  System update policy: ${policy?.toString() ?: "None"}\n")
                } catch (_: Exception) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    appendOutput("  Auto time required: ${dpm.getAutoTimeRequired()}\n")
                } catch (_: Exception) {}
            }

            appendOutput("\n╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Device info: ${e.message}\n")
        }
    }

    private fun showScreenLockOptions() {
        try {
            val dpm = getDpm()

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Screen Lock Options           ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            val ourComponent = ComponentName(requireContext(), "com.hackerlauncher.receiver.DeviceAdminReceiver")
            val isAdmin = dpm.isAdminActive(ourComponent)

            appendOutput("  Our admin active: $isAdmin\n\n")

            if (isAdmin) {
                appendOutput("  Available screen lock actions:\n")
                appendOutput("  - dpm.lockNow() - Immediate screen lock\n")
                appendOutput("  - dpm.resetPassword(pin, 0) - Set new PIN\n")
                appendOutput("  - dpm.setMaximumTimeToLock() - Auto-lock\n")
                appendOutput("  - dpm.setKeyguardDisabledFeatures() - Lock config\n")
                appendOutput("  - dpm.setCameraDisabled() - Lock screen camera\n\n")

                try {
                    val timeout = dpm.getMaximumTimeToLock(ourComponent)
                    appendOutput("  Current auto-lock: ${if (timeout == 0L) "None" else "${timeout / 1000}s"}\n")
                } catch (_: Exception) {}

                try {
                    val disabled = dpm.getKeyguardDisabledFeatures(ourComponent)
                    appendOutput("  Keyguard disabled flags: $disabled\n")
                } catch (_: Exception) {}
            } else {
                appendOutput("  [!] Activate admin first to use lock options\n")
                appendOutput("  [*] Use 'Activate' button above\n")
            }

            appendOutput("\n  Quick actions:\n")
            appendOutput("  [*] Open Security Settings for manual lock config\n")

            try {
                val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                startActivity(intent)
                appendOutput("  [+] Opened Security Settings\n")
            } catch (e: Exception) {
                appendOutput("  [E] Could not open settings: ${e.message}\n")
            }

            appendOutput("\n╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Screen lock: ${e.message}\n")
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
