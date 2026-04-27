package com.hackerlauncher.modules

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.jar.JarFile

data class ApkExtractorAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val apkSize: Long,
    val apkPath: String,
    val isSystemApp: Boolean,
    val targetSdkVersion: Int,
    val minSdkVersion: Int,
    val permissionsCount: Int,
    var isSelected: Boolean = false
)

class ApkExtractorFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val BLACK = Color.parseColor("#000000")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")
    private val MED_GRAY = Color.parseColor("#333333")

    private val allApps = mutableListOf<ApkExtractorAppInfo>()
    private val filteredApps = mutableListOf<ApkExtractorAppInfo>()
    private lateinit var mainLayout: LinearLayout
    private lateinit var searchEdit: EditText
    private lateinit var appCountText: TextView
    private lateinit var appListContainer: LinearLayout
    private lateinit var statusText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
        mainLayout.addView(makeTitle("[>] APK EXTRACTOR"))

        // Search
        searchEdit = EditText(requireContext()).apply {
            hint = "Search apps..."
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
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filterApps() }
            override fun afterTextChanged(s: Editable?) {}
        })
        mainLayout.addView(searchEdit)

        // App count
        appCountText = makeLabel("Apps: 0")
        mainLayout.addView(appCountText)

        // Buttons row
        val btnRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        btnRow.addView(makeButton("EXTRACT SELECTED") { batchExtract() })
        btnRow.addView(makeButton("SELECT ALL") { selectAll() })
        btnRow.addView(makeButton("DESELECT") { deselectAll() })

        mainLayout.addView(btnRow)

        // Status
        statusText = makeLabel("Ready.")
        mainLayout.addView(statusText)

        // App list container
        appListContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(appListContainer)

        scrollView.addView(mainLayout)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadApps()
    }

    private fun loadApps() {
        statusText.text = "[~] Scanning installed apps..."
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { getInstalledApps() }
            allApps.clear()
            allApps.addAll(apps)
            filterApps()
            statusText.text = "[>] Loaded ${allApps.size} apps"
        }
    }

    private fun getInstalledApps(): List<ApkExtractorAppInfo> {
        val pm = requireContext().packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)
        val result = mutableListOf<ApkExtractorAppInfo>()

        for (pkgInfo in packages) {
            try {
                val appInfo = pkgInfo.applicationInfo
                val appName = pm.getApplicationLabel(appInfo).toString()
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val apkFile = File(appInfo.sourceDir)
                val apkSize = if (apkFile.exists()) apkFile.length() else 0L
                val versionName = pkgInfo.versionName ?: "N/A"
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
                val targetSdk = appInfo.targetSdkVersion
                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 0
                val permCount = pkgInfo.requestedPermissions?.size ?: 0

                result.add(ApkExtractorAppInfo(
                    packageName = pkgInfo.packageName,
                    appName = appName,
                    versionName = versionName,
                    versionCode = versionCode,
                    apkSize = apkSize,
                    apkPath = appInfo.sourceDir,
                    isSystemApp = isSystemApp,
                    targetSdkVersion = targetSdk,
                    minSdkVersion = minSdk,
                    permissionsCount = permCount
                ))
            } catch (_: Exception) { }
        }
        return result.sortedBy { it.appName.lowercase() }
    }

    private fun filterApps() {
        val query = searchEdit.text.toString().lowercase()
        filteredApps.clear()
        filteredApps.addAll(
            if (query.isBlank()) allApps
            else allApps.filter { it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query) }
        )
        appCountText.text = "Apps: ${filteredApps.size}"
        renderAppList()
    }

    private fun renderAppList() {
        appListContainer.removeAllViews()
        for ((index, app) in filteredApps.withIndex()) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(if (index % 2 == 0) DARK_GRAY else BLACK)
                setPadding(8, 8, 8, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
            }

            // Icon
            val icon = ImageView(requireContext()).apply {
                try {
                    setImageDrawable(requireContext().packageManager.getApplicationIcon(app.packageName))
                } catch (_: Exception) { }
                layoutParams = LinearLayout.LayoutParams(48, 48).apply { marginEnd = 8 }
            }
            row.addView(icon)

            // Info column
            val infoCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            infoCol.addView(TextView(requireContext()).apply {
                text = app.appName
                setTextColor(GREEN)
                textSize = 13f
                setTypeface(Typeface.MONOSPACE)
            })
            infoCol.addView(TextView(requireContext()).apply {
                text = app.packageName
                setTextColor(DARK_GREEN)
                textSize = 11f
                setTypeface(Typeface.MONOSPACE)
            })
            infoCol.addView(TextView(requireContext()).apply {
                text = "v${app.versionName} | ${formatSize(app.apkSize)} | SDK:${app.minSdkVersion}-${app.targetSdkVersion} | Perms:${app.permissionsCount}"
                setTextColor(Color.parseColor("#888888"))
                textSize = 10f
                setTypeface(Typeface.MONOSPACE)
            })
            infoCol.addView(TextView(requireContext()).apply {
                text = if (app.isSystemApp) "[SYSTEM]" else "[USER]"
                setTextColor(if (app.isSystemApp) Color.parseColor("#FF8800") else GREEN)
                textSize = 10f
                setTypeface(Typeface.MONOSPACE)
            })

            row.addView(infoCol)

            // Checkbox
            val checkBox = CheckBox(requireContext()).apply {
                isChecked = app.isSelected
                setTextColor(GREEN)
                setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN))
                setOnCheckedChangeListener { _, checked ->
                    app.isSelected = checked
                }
            }
            row.addView(checkBox)

            // Extract button
            val extractBtn = Button(requireContext()).apply {
                text = "EXT"
                setTextColor(BLACK)
                setBackgroundColor(GREEN)
                textSize = 10f
                setTypeface(Typeface.MONOSPACE)
                setPadding(4, 2, 4, 2)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { extractApk(app) }
            }
            row.addView(extractBtn)

            // Info button
            val infoBtn = Button(requireContext()).apply {
                text = "INFO"
                setTextColor(GREEN)
                setBackgroundColor(MED_GRAY)
                textSize = 10f
                setTypeface(Typeface.MONOSPACE)
                setPadding(4, 2, 4, 2)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { showApkInfo(app) }
            }
            row.addView(infoBtn)

            // Share button
            val shareBtn = Button(requireContext()).apply {
                text = "SHARE"
                setTextColor(GREEN)
                setBackgroundColor(MED_GRAY)
                textSize = 10f
                setTypeface(Typeface.MONOSPACE)
                setPadding(4, 2, 4, 2)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { shareApk(app) }
            }
            row.addView(shareBtn)

            appListContainer.addView(row)
        }
    }

    private fun extractApk(app: ApkExtractorAppInfo) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val srcFile = File(app.apkPath)
                    if (!srcFile.exists()) return@withContext "Source APK not found"
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val destFile = File(downloadsDir, "${app.appName}_${app.versionName}.apk")
                    FileInputStream(srcFile).use { fis ->
                        FileOutputStream(destFile).use { fos ->
                            val buf = ByteArray(8192)
                            var len: Int
                            while (fis.read(buf).also { len = it } > 0) {
                                fos.write(buf, 0, len)
                            }
                        }
                    }
                    "Extracted to ${destFile.absolutePath}"
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            statusText.text = "[+] $result"
            Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show()
        }
    }

    private fun batchExtract() {
        val selected = filteredApps.filter { it.isSelected }
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), "No apps selected", Toast.LENGTH_SHORT).show()
            return
        }
        statusText.text = "[~] Extracting ${selected.size} APK(s)..."
        lifecycleScope.launch {
            var success = 0
            var fail = 0
            withContext(Dispatchers.IO) {
                for (app in selected) {
                    try {
                        val srcFile = File(app.apkPath)
                        if (!srcFile.exists()) { fail++; continue }
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val destFile = File(downloadsDir, "${app.appName}_${app.versionName}.apk")
                        FileInputStream(srcFile).use { fis ->
                            FileOutputStream(destFile).use { fos ->
                                val buf = ByteArray(8192)
                                var len: Int
                                while (fis.read(buf).also { len = it } > 0) {
                                    fos.write(buf, 0, len)
                                }
                            }
                        }
                        success++
                    } catch (_: Exception) { fail++ }
                }
            }
            statusText.text = "[+] Extracted: $success | Failed: $fail"
            Toast.makeText(requireContext(), "Done: $success extracted, $fail failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareApk(app: ApkExtractorAppInfo) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val srcFile = File(app.apkPath)
                    if (!srcFile.exists()) return@withContext null
                    val shareDir = File(requireContext().cacheDir, "shared_apk")
                    shareDir.mkdirs()
                    val destFile = File(shareDir, "${app.appName}.apk")
                    FileInputStream(srcFile).use { fis ->
                        FileOutputStream(destFile).use { fos ->
                            val buf = ByteArray(8192)
                            var len: Int
                            while (fis.read(buf).also { len = it } > 0) {
                                fos.write(buf, 0, len)
                            }
                        }
                    }
                    destFile
                } catch (_: Exception) { null }
            }

            if (result != null) {
                try {
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        result
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share APK"))
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Failed to prepare APK for sharing", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showApkInfo(app: ApkExtractorAppInfo) {
        val sb = StringBuilder()
        sb.appendLine("=== APK INFO ===")
        sb.appendLine("App: ${app.appName}")
        sb.appendLine("Package: ${app.packageName}")
        sb.appendLine("Version: ${app.versionName} (${app.versionCode})")
        sb.appendLine("Min SDK: ${app.minSdkVersion}")
        sb.appendLine("Target SDK: ${app.targetSdkVersion}")
        sb.appendLine("Size: ${formatSize(app.apkSize)}")
        sb.appendLine("Type: ${if (app.isSystemApp) "System" else "User"}")
        sb.appendLine("APK Path: ${app.apkPath}")
        sb.appendLine("Permissions: ${app.permissionsCount}")

        // Get signing certificate SHA-256
        try {
            val pm = requireContext().packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signatures = pkgInfo.signingInfo?.apkContentsSigners
                if (signatures != null) {
                    for (sig in signatures) {
                        val md = MessageDigest.getInstance("SHA-256")
                        val digest = md.digest(sig.toByteArray())
                        val hex = digest.joinToString(":") { "%02X".format(it) }
                        sb.appendLine("SHA-256: $hex")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val pkgInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES)
                val signatures = pkgInfo.signatures
                if (signatures != null) {
                    for (sig in signatures) {
                        val md = MessageDigest.getInstance("SHA-256")
                        val digest = md.digest(sig.toByteArray())
                        val hex = digest.joinToString(":") { "%02X".format(it) }
                        sb.appendLine("SHA-256: $hex")
                    }
                }
            }
        } catch (_: Exception) {
            sb.appendLine("SHA-256: Unable to retrieve")
        }

        // Try to get permissions list
        try {
            val pm = requireContext().packageManager
            val pkgInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            val perms = pkgInfo.requestedPermissions
            if (perms != null && perms.isNotEmpty()) {
                sb.appendLine("\n--- Permissions ---")
                perms.forEach { sb.appendLine("  * $it") }
            }
        } catch (_: Exception) { }

        showInfoDialog(app.appName, sb.toString())
    }

    private fun selectAll() {
        filteredApps.forEach { it.isSelected = true }
        renderAppList()
        statusText.text = "[+] Selected ${filteredApps.size} apps"
    }

    private fun deselectAll() {
        filteredApps.forEach { it.isSelected = false }
        renderAppList()
        statusText.text = "[>] All deselected"
    }

    private fun showInfoDialog(title: String, message: String) {
        val dialogLayout = ScrollView(requireContext()).apply { setBackgroundColor(BLACK) }
        val textView = TextView(requireContext()).apply {
            text = message
            setTextColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(24, 24, 24, 24)
        }
        dialogLayout.addView(textView)

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogLayout)
            .setPositiveButton("COPY") { _, _ ->
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("APK Info", message))
                Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CLOSE", null)
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

    private fun makeButton(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(BLACK)
            setBackgroundColor(GREEN)
            textSize = 11f
            setTypeface(Typeface.MONOSPACE)
            setPadding(8, 4, 8, 4)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginEnd = 4 }
            setOnClickListener { onClick() }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
            bytes >= 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }
}
