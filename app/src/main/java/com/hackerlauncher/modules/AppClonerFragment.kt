package com.hackerlauncher.modules

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CloneInfo(
    val packageName: String,
    val appName: String,
    val isCloned: Boolean,
    val userId: Int = 0
)

class AppClonerFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val BLACK = Color.parseColor("#000000")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")
    private val MED_GRAY = Color.parseColor("#333333")

    private lateinit var prefs: SharedPreferences
    private val installedApps = mutableListOf<CloneInfo>()
    private val clonedApps = mutableListOf<CloneInfo>()

    private lateinit var mainLayout: LinearLayout
    private lateinit var searchEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var cloneTargetEdit: EditText
    private lateinit var appsContainer: LinearLayout
    private lateinit var clonedContainer: LinearLayout
    private lateinit var userManagerInfo: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        prefs = requireContext().getSharedPreferences("app_cloner", Context.MODE_PRIVATE)

        val scrollView = ScrollView(requireContext()).apply {
            setBackgroundColor(BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        mainLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        mainLayout.addView(makeTitle("[>] APP CLONER"))

        // Status
        statusText = makeLabel("[~] Initializing...")
        mainLayout.addView(statusText)

        // UserManager info
        userManagerInfo = TextView(requireContext()).apply {
            text = "[!] This module uses Android's UserManager and multiple user profiles\n" +
                "    to create app clones. Requires MANAGE_USERS and CREATE_USERS permissions.\n" +
                "    May not work on all devices/ROMs."
            setTextColor(Color.parseColor("#FFFF00"))
            textSize = 11f
            setTypeface(Typeface.MONOSPACE)
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#1A1A00"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(userManagerInfo)

        // Search
        mainLayout.addView(makeSectionHeader("SEARCH APPS"))
        searchEdit = EditText(requireContext()).apply {
            hint = "Search by app name or package..."
            setTextColor(GREEN)
            setHintTextColor(DARK_GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 13f
            setTypeface(Typeface.MONOSPACE)
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(searchEdit)

        // Clone by package name
        mainLayout.addView(makeSectionHeader("CLONE BY PACKAGE"))

        cloneTargetEdit = EditText(requireContext()).apply {
            hint = "com.example.app"
            setTextColor(GREEN)
            setHintTextColor(DARK_GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(cloneTargetEdit)

        val cloneRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        cloneRow.addView(makeHalfButton("CLONE APP") { cloneApp() })
        cloneRow.addView(makeHalfButton("REFRESH") { loadApps() })
        mainLayout.addView(cloneRow)

        // User profile management
        mainLayout.addView(makeSectionHeader("USER PROFILES"))

        mainLayout.addView(makeButton("LIST USER PROFILES") { listUserProfiles() })
        mainLayout.addView(makeButton("CREATE CLONE PROFILE") { createCloneProfile() })
        mainLayout.addView(makeButton("DELETE CLONE PROFILE") { deleteCloneProfile() })

        // Installed apps list
        mainLayout.addView(makeSectionHeader("INSTALLABLE APPS"))

        appsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(appsContainer)

        // Cloned apps list
        mainLayout.addView(makeSectionHeader("CLONED APPS"))

        clonedContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(clonedContainer)

        scrollView.addView(mainLayout)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadApps()
    }

    private fun loadApps() {
        statusText.text = "[~] Loading apps..."
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { getInstalledApps() }
            installedApps.clear()
            installedApps.addAll(apps)
            renderApps()
            statusText.text = "[>] Found ${installedApps.size} apps"
        }
    }

    private fun getInstalledApps(): List<CloneInfo> {
        val pm = requireContext().packageManager
        val packages = pm.getInstalledApplications(0)
        val result = mutableListOf<CloneInfo>()

        for (appInfo in packages) {
            try {
                val appName = pm.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                // Only show non-system apps for cloning
                if (!isSystem) {
                    result.add(CloneInfo(
                        packageName = appInfo.packageName,
                        appName = appName,
                        isCloned = false
                    ))
                }
            } catch (_: Exception) { }
        }

        return result.sortedBy { it.appName.lowercase() }
    }

    private fun renderApps() {
        appsContainer.removeAllViews()
        val query = searchEdit.text.toString().lowercase()

        val filtered = if (query.isBlank()) installedApps
        else installedApps.filter {
            it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }

        for ((index, app) in filtered.take(50).withIndex()) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(if (index % 2 == 0) DARK_GRAY else BLACK)
                setPadding(10, 6, 10, 6)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val infoCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            infoCol.addView(TextView(requireContext()).apply {
                text = app.appName
                setTextColor(GREEN)
                textSize = 12f
                setTypeface(Typeface.MONOSPACE)
            })

            infoCol.addView(TextView(requireContext()).apply {
                text = app.packageName
                setTextColor(Color.parseColor("#888888"))
                textSize = 10f
                setTypeface(Typeface.MONOSPACE)
            })

            row.addView(infoCol)

            val cloneBtn = Button(requireContext()).apply {
                text = "CLONE"
                setTextColor(BLACK)
                setBackgroundColor(GREEN)
                textSize = 10f
                setTypeface(Typeface.MONOSPACE)
                setPadding(6, 2, 6, 2)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { cloneAppByName(app.packageName, app.appName) }
            }
            row.addView(cloneBtn)

            appsContainer.addView(row)
        }

        if (filtered.size > 50) {
            appsContainer.addView(TextView(requireContext()).apply {
                text = "... and ${filtered.size - 50} more (use search to filter)"
                setTextColor(DARK_GREEN)
                textSize = 11f
                setTypeface(Typeface.MONOSPACE)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun cloneApp() {
        val packageName = cloneTargetEdit.text.toString().trim()
        if (packageName.isBlank()) {
            Toast.makeText(requireContext(), "Enter a package name", Toast.LENGTH_SHORT).show()
            return
        }
        cloneAppByName(packageName, packageName)
    }

    private fun cloneAppByName(packageName: String, appName: String) {
        try {
            // Use UserManager to install app in a different user profile
            // This requires system-level permissions on most devices

            val userManager = requireContext().getSystemService(Context.USER_SERVICE) as android.os.UserManager

            // Check if we can create users
            val canCreateUsers = try {
                // Try to check via reflection since these methods are hidden
                val method = userManager.javaClass.getMethod("canAddMoreUsers", Int::class.java)
                method.invoke(userManager, 1) as Boolean
            } catch (_: Exception) {
                false
            }

            if (!canCreateUsers) {
                // Try alternative approach: install existing package for another user
                try {
                    val pm = requireContext().packageManager
                    // Try to install for guest/secondary user
                    val installMethod = pm.javaClass.getMethod(
                        "installExistingPackageAsUser",
                        String::class.java,
                        Int::class.java
                    )
                    val result = installMethod.invoke(pm, packageName, 10) as Int
                    if (result == 1) {
                        statusText.text = "[+] Clone created for $appName"
                        Toast.makeText(requireContext(), "App cloned successfully!", Toast.LENGTH_SHORT).show()
                        loadClonedApps()
                    } else {
                        statusText.text = "[!] Clone failed (result: $result)"
                        Toast.makeText(requireContext(), "Clone failed - may need root/system permissions", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    statusText.text = "[!] Clone failed: ${e.message}"
                    Toast.makeText(requireContext(), "Requires system permissions: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                statusText.text = "[>] Creating user profile and installing clone..."
                createUserAndClone(packageName, appName)
            }
        } catch (e: Exception) {
            statusText.text = "[!] Error: ${e.message}"
            Toast.makeText(requireContext(), "Clone error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createUserAndClone(packageName: String, appName: String) {
        try {
            val userManager = requireContext().getSystemService(Context.USER_SERVICE) as android.os.UserManager

            // Create a new user profile for cloning
            val createUserMethod = userManager.javaClass.getMethod(
                "createUser",
                String::class.java,
                Int::class.java
            )
            // FLAG_PROFILE = 0x00000040
            val userHandle = createUserMethod.invoke(userManager, "Clone_$appName", 0x40)

            if (userHandle != null) {
                // Install app for the new user
                val userIdField = userHandle.javaClass.getDeclaredField("mHandle")
                userIdField.isAccessible = true
                val userId = userIdField.getInt(userHandle)

                val pm = requireContext().packageManager
                val installMethod = pm.javaClass.getMethod(
                    "installExistingPackageAsUser",
                    String::class.java,
                    Int::class.java
                )
                installMethod.invoke(pm, packageName, userId)

                statusText.text = "[+] Clone created in profile for $appName"
                Toast.makeText(requireContext(), "Clone created in separate profile!", Toast.LENGTH_SHORT).show()
                loadClonedApps()
            } else {
                statusText.text = "[!] Failed to create user profile"
            }
        } catch (e: Exception) {
            statusText.text = "[!] Clone error: ${e.message}\n    This feature typically requires root or system privileges"
            Toast.makeText(requireContext(), "Requires elevated permissions", Toast.LENGTH_LONG).show()
        }
    }

    private fun listUserProfiles() {
        try {
            val userManager = requireContext().getSystemService(Context.USER_SERVICE) as android.os.UserManager

            val sb = StringBuilder()
            sb.appendLine("=== USER PROFILES ===")

            // Get user profiles
            try {
                val profilesMethod = userManager.javaClass.getMethod("getUserProfiles")
                val profiles = profilesMethod.invoke(userManager) as? List<*>

                if (profiles != null) {
                    sb.appendLine("Found ${profiles.size} profiles:\n")
                    for ((index, profile) in profiles.withIndex()) {
                        try {
                            val idField = profile?.javaClass?.getDeclaredField("mHandle")
                            idField?.isAccessible = true
                            val id = idField?.getInt(profile) ?: 0

                            val nameMethod = userManager.javaClass.getMethod("getUserName", Int::class.java)
                            val name = try { nameMethod.invoke(userManager, id) as? String ?: "Unknown" } catch (_: Exception) { "User $id" }

                            sb.appendLine("  [$index] ID: $id | Name: $name")
                        } catch (_: Exception) {
                            sb.appendLine("  [$index] ${profile?.toString() ?: "Unknown"}")
                        }
                    }
                } else {
                    sb.appendLine("No additional profiles found")
                }
            } catch (e: Exception) {
                sb.appendLine("Cannot list profiles: ${e.message}")
                sb.appendLine("This requires MANAGE_USERS permission")
            }

            // Also check if user is a managed profile
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val isManaged = userManager.isManagedProfile
                    sb.appendLine("\nIs Managed Profile: $isManaged")
                }
            } catch (_: Exception) { }

            // Check system users
            try {
                val usersMethod = userManager.javaClass.getMethod("getUsers")
                val users = usersMethod.invoke(userManager) as? List<*>
                if (users != null) {
                    sb.appendLine("\nSystem Users: ${users.size}")
                    for (user in users) {
                        sb.appendLine("  - $user")
                    }
                }
            } catch (_: Exception) {
                sb.appendLine("\nCannot enumerate system users (needs permission)")
            }

            showInfoDialog("User Profiles", sb.toString())
        } catch (e: Exception) {
            statusText.text = "[!] Error: ${e.message}"
        }
    }

    private fun createCloneProfile() {
        try {
            val userManager = requireContext().getSystemService(Context.USER_SERVICE) as android.os.UserManager

            val name = "HackerClone_${System.currentTimeMillis() % 10000}"

            // Try to create a secondary user / profile
            val createUserMethod = userManager.javaClass.getMethod(
                "createUser",
                String::class.java,
                Int::class.java
            )

            val result = createUserMethod.invoke(userManager, name, 0)
            if (result != null) {
                statusText.text = "[+] Created profile: $name"
                Toast.makeText(requireContext(), "Profile created!", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = "[!] Failed to create profile - requires MANAGE_USERS permission"
                Toast.makeText(requireContext(), "Requires system permissions", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            statusText.text = "[!] Create profile error: ${e.message}"
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteCloneProfile() {
        try {
            val userManager = requireContext().getSystemService(Context.USER_SERVICE) as android.os.UserManager

            // Try to get and remove a secondary user
            val usersMethod = userManager.javaClass.getMethod("getUsers")
            val users = usersMethod.invoke(userManager) as? List<*>

            if (users != null && users.size > 1) {
                // Try to remove the last user (not owner)
                val userToRemove = users.last()
                val idField = userToRemove?.javaClass?.getDeclaredField("id")
                idField?.isAccessible = true
                val userId = idField?.getInt(userToRemove) ?: -1

                if (userId > 0) {
                    val removeMethod = userManager.javaClass.getMethod("removeUser", Int::class.java)
                    val removed = removeMethod.invoke(userManager, userId) as? Boolean ?: false
                    if (removed) {
                        statusText.text = "[+] Removed profile: User $userId"
                        Toast.makeText(requireContext(), "Profile removed", Toast.LENGTH_SHORT).show()
                    } else {
                        statusText.text = "[!] Failed to remove profile"
                    }
                }
            } else {
                statusText.text = "[!] No additional profiles to remove"
            }
        } catch (e: Exception) {
            statusText.text = "[!] Delete error: ${e.message}"
            Toast.makeText(requireContext(), "Requires system permissions", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadClonedApps() {
        clonedContainer.removeAllViews()
        try {
            // Check for apps installed in other user profiles
            val userManager = requireContext().getSystemService(Context.USER_SERVICE) as android.os.UserManager
            val pm = requireContext().packageManager

            // Try to get packages for other users
            val profilesMethod = userManager.javaClass.getMethod("getUserProfiles")
            val profiles = profilesMethod.invoke(userManager) as? List<*>

            if (profiles != null && profiles.size > 1) {
                for (profile in profiles) {
                    try {
                        val idField = profile?.javaClass?.getDeclaredField("mHandle")
                        idField?.isAccessible = true
                        val userId = idField?.getInt(profile) ?: 0

                        if (userId != 0) {
                            val row = LinearLayout(requireContext()).apply {
                                orientation = LinearLayout.VERTICAL
                                setBackgroundColor(DARK_GRAY)
                                setPadding(10, 6, 10, 6)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                            }

                            row.addView(TextView(requireContext()).apply {
                                text = "Profile $userId"
                                setTextColor(Color.parseColor("#FFFF00"))
                                textSize = 12f
                                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                            })

                            row.addView(TextView(requireContext()).apply {
                                text = "User ID: $userId"
                                setTextColor(Color.parseColor("#888888"))
                                textSize = 10f
                                setTypeface(Typeface.MONOSPACE)
                            })

                            val deleteBtn = Button(requireContext()).apply {
                                text = "DELETE CLONE"
                                setTextColor(Color.RED)
                                setBackgroundColor(MED_GRAY)
                                textSize = 10f
                                setTypeface(Typeface.MONOSPACE)
                                setPadding(4, 2, 4, 2)
                                setOnClickListener {
                                    try {
                                        val removeMethod = userManager.javaClass.getMethod("removeUser", Int::class.java)
                                        removeMethod.invoke(userManager, userId)
                                        statusText.text = "[+] Deleted clone profile $userId"
                                        loadClonedApps()
                                    } catch (e: Exception) {
                                        statusText.text = "[!] Delete error: ${e.message}"
                                    }
                                }
                            }
                            row.addView(deleteBtn)

                            clonedContainer.addView(row)
                        }
                    } catch (_: Exception) { }
                }
            }

            if (clonedContainer.childCount == 0) {
                clonedContainer.addView(TextView(requireContext()).apply {
                    text = "[~] No cloned apps found"
                    setTextColor(DARK_GREEN)
                    textSize = 12f
                    setTypeface(Typeface.MONOSPACE)
                    setPadding(0, 8, 0, 0)
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }
        } catch (e: Exception) {
            clonedContainer.addView(TextView(requireContext()).apply {
                text = "[!] Error loading clones: ${e.message}"
                setTextColor(Color.parseColor("#FF4444"))
                textSize = 11f
                setTypeface(Typeface.MONOSPACE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun showInfoDialog(title: String, message: String) {
        val scroll = ScrollView(requireContext()).apply { setBackgroundColor(BLACK) }
        scroll.addView(TextView(requireContext()).apply {
            text = message
            setTextColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(24, 24, 24, 24)
        })

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun makeTitle(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(GREEN)
            textSize = 14f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun makeLabel(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, 8, 0, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun makeSectionHeader(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = "▸ $text"
            setTextColor(Color.parseColor("#FFFF00"))
            textSize = 13f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, 12, 0, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun makeButton(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(BLACK)
            setBackgroundColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 4 }
            setOnClickListener { onClick() }
        }
    }

    private fun makeHalfButton(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(BLACK)
            setBackgroundColor(GREEN)
            textSize = 11f
            setTypeface(Typeface.MONOSPACE)
            setPadding(8, 6, 8, 6)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginEnd = 4 }
            setOnClickListener { onClick() }
        }
    }
}
