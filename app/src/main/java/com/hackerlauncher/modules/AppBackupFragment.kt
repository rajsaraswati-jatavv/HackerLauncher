package com.hackerlauncher.modules

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * FEATURE 4: AppBackupFragment
 * Backup APKs of installed apps
 * Hacker-themed dark UI with green text (#00FF00) on black background (#0D0D0D)
 */
class AppBackupFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val YELLOW = Color.parseColor("#FFFF00")
    private val CYAN = Color.parseColor("#00FFFF")
    private val RED = Color.parseColor("#FF4444")
    private val BLACK = Color.parseColor("#0D0D0D")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")
    private val MED_GRAY = Color.parseColor("#333333")

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var tvStatus: TextView
    private lateinit var backupProgressBar: ProgressBar
    private lateinit var searchEdit: EditText
    private lateinit var appsContainer: LinearLayout

    private var backupJob: Job? = null

    data class AppBackupInfo(
        val packageName: String,
        val appName: String,
        val apkPath: String,
        val isSystem: Boolean,
        val size: Long
    )

    private val installedApps = mutableListOf<AppBackupInfo>()

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
            text = "[ APP BACKUP ]"
            setTextColor(GREEN)
            textSize = 16f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        })

        // Status
        tvStatus = TextView(ctx).apply {
            text = "[~] Loading apps..."
            setTextColor(YELLOW)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 4)
        }
        rootLayout.addView(tvStatus)

        // Progress bar
        backupProgressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(backupProgressBar)

        // Search
        searchEdit = EditText(ctx).apply {
            hint = "Search apps..."
            setTextColor(GREEN)
            setHintTextColor(DARK_GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(12, 8, 12, 8)
        }
        rootLayout.addView(searchEdit)

        // Buttons
        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeBtn("BACKUP ALL") { backupAll() })
        row1.addView(makeBtn("SEARCH") { searchAndRender() })
        row1.addView(makeBtn("REFRESH") { loadApps() })
        rootLayout.addView(row1)

        val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(makeBtn("VIEW BACKUPS") { viewBackups() })
        row2.addView(makeBtn("RESTORE") { showRestoreInfo() })
        row2.addView(makeBtn("DELETE BACKUPS") { deleteBackups() })
        rootLayout.addView(row2)

        // Apps list
        rootLayout.addView(makeSectionHeader("INSTALLABLE APPS"))

        val appsScroll = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        appsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        appsScroll.addView(appsContainer)
        rootLayout.addView(appsScroll)

        // Output area
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadApps()
    }

    private fun loadApps() {
        tvStatus.text = "[~] Loading apps..."
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { getInstalledApps() }
            installedApps.clear()
            installedApps.addAll(apps)
            renderApps()
            tvStatus.text = "[>] Found ${installedApps.size} apps"
        }
    }

    private fun getInstalledApps(): List<AppBackupInfo> {
        val pm = requireContext().packageManager
        val packages = pm.getInstalledApplications(0)
        val result = mutableListOf<AppBackupInfo>()

        for (appInfo in packages) {
            try {
                val appName = pm.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val apkPath = appInfo.sourceDir
                val apkFile = File(apkPath)
                val size = if (apkFile.exists()) apkFile.length() else 0L
                result.add(AppBackupInfo(
                    packageName = appInfo.packageName,
                    appName = appName,
                    apkPath = apkPath,
                    isSystem = isSystem,
                    size = size
                ))
            } catch (_: Exception) {}
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
                gravity = Gravity.CENTER_VERTICAL
            }

            val infoCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            infoCol.addView(TextView(requireContext()).apply {
                text = "${if (app.isSystem) "[SYS]" else "[USR]"} ${app.appName}"
                setTextColor(if (app.isSystem) Color.parseColor("#888888") else GREEN)
                textSize = 11f
                typeface = Typeface.MONOSPACE
            })

            infoCol.addView(TextView(requireContext()).apply {
                text = "${app.packageName} | ${formatSize(app.size)}"
                setTextColor(Color.parseColor("#666666"))
                textSize = 9f
                typeface = Typeface.MONOSPACE
            })

            row.addView(infoCol)

            val backupBtn = Button(requireContext()).apply {
                text = "BACKUP"
                setTextColor(BLACK)
                setBackgroundColor(GREEN)
                textSize = 9f
                typeface = Typeface.MONOSPACE
                setPadding(4, 2, 4, 2)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { backupApp(app) }
            }
            row.addView(backupBtn)

            appsContainer.addView(row)
        }

        if (filtered.size > 50) {
            appsContainer.addView(TextView(requireContext()).apply {
                text = "... and ${filtered.size - 50} more (use search)"
                setTextColor(DARK_GREEN)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            })
        }
    }

    private fun searchAndRender() {
        renderApps()
    }

    private fun backupApp(app: AppBackupInfo) {
        lifecycleScope.launch {
            appendOutput("[*] Backing up ${app.appName}...\n")
            val result = withContext(Dispatchers.IO) {
                copyApkToBackup(app)
            }
            if (result) {
                appendOutput("[+] Backed up: ${app.appName}\n")
                appendOutput("    → /HackerLauncher/Backups/${app.packageName}.apk\n\n")
                Toast.makeText(requireContext(), "Backup complete: ${app.appName}", Toast.LENGTH_SHORT).show()
            } else {
                appendOutput("[!] Backup failed: ${app.appName}\n\n")
                Toast.makeText(requireContext(), "Backup failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun backupAll() {
        if (backupJob?.isActive == true) return
        backupProgressBar.visibility = View.VISIBLE
        backupProgressBar.progress = 0
        tvStatus.text = "[*] Backing up all user apps..."

        backupJob = lifecycleScope.launch {
            val userApps = installedApps.filter { !it.isSystem }
            var backedUp = 0
            var failed = 0

            for ((index, app) in userApps.withIndex()) {
                val result = withContext(Dispatchers.IO) { copyApkToBackup(app) }
                if (result) backedUp++ else failed++
                withContext(Dispatchers.Main) {
                    backupProgressBar.progress = ((index + 1) * 100 / userApps.size)
                    tvStatus.text = "[*] Backed up: $backedUp / ${userApps.size}"
                }
            }

            appendOutput("═══ BACKUP COMPLETE ═══\n")
            appendOutput("Backed up: $backedUp apps\n")
            appendOutput("Failed: $failed apps\n")
            appendOutput("Location: /HackerLauncher/Backups/\n\n")

            backupProgressBar.visibility = View.GONE
            tvStatus.text = "[+] Backup complete: $backedUp apps backed up"
            Toast.makeText(requireContext(), "Backup complete!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyApkToBackup(app: AppBackupInfo): Boolean {
        return try {
            val backupDir = File(Environment.getExternalStorageDirectory(), "HackerLauncher/Backups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val sourceFile = File(app.apkPath)
            if (!sourceFile.exists()) return false

            val destFile = File(backupDir, "${app.packageName}.apk")
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun viewBackups() {
        val backupDir = File(Environment.getExternalStorageDirectory(), "HackerLauncher/Backups")
        if (!backupDir.exists()) {
            appendOutput("[!] No backups found\n\n")
            return
        }

        val files = backupDir.listFiles()?.filter { it.name.endsWith(".apk") }?.sortedByDescending { it.lastModified() }
        if (files.isNullOrEmpty()) {
            appendOutput("[!] No backup APKs found\n\n")
            return
        }

        appendOutput("═══ BACKUP LIST ═══\n\n")
        for (file in files) {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(file.lastModified()))
            appendOutput("${file.name} | ${formatSize(file.length())} | $date\n")
        }
        appendOutput("\nTotal: ${files.size} backups\n\n")
    }

    private fun showRestoreInfo() {
        appendOutput("═══ RESTORE INFO ═══\n\n")
        appendOutput("To restore a backed-up APK:\n")
        appendOutput("1. Navigate to /HackerLauncher/Backups/\n")
        appendOutput("2. Tap the .apk file\n")
        appendOutput("3. Enable 'Install from unknown sources'\n")
        appendOutput("4. Follow the installation prompt\n\n")
        appendOutput("[*] Note: You may need root access for\n")
        appendOutput("    system app restoration.\n\n")
    }

    private fun deleteBackups() {
        val backupDir = File(Environment.getExternalStorageDirectory(), "HackerLauncher/Backups")
        if (backupDir.exists()) {
            val count = backupDir.listFiles()?.size ?: 0
            backupDir.deleteRecursively()
            appendOutput("[+] Deleted $count backup files\n\n")
            Toast.makeText(requireContext(), "Backups deleted", Toast.LENGTH_SHORT).show()
        } else {
            appendOutput("[!] No backups to delete\n\n")
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1073741824 -> "${"%.1f".format(bytes / 1073741824.0)} GB"
            bytes >= 1048576 -> "${"%.1f".format(bytes / 1048576.0)} MB"
            bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            else -> "$bytes B"
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
        backupJob?.cancel()
    }
}
