package com.hackerlauncher.launcher

import com.hackerlauncher.utils.PreferencesManager

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detailed App Information Activity.
 * Shows icon, name, package, version, dates, size, permissions, and action buttons.
 */
class AppInfoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefsManager: PreferencesManager
    private var packageNameStr: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        prefsManager = PreferencesManager(this)
        packageNameStr = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

        if (packageNameStr.isEmpty()) {
            Toast.makeText(this, "> error: no_package_specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(rootLayout)

        // Loading indicator
        val progressBar = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        rootLayout.addView(progressBar)

        setContentView(scrollView)
        loadAppInfo(rootLayout, progressBar)
    }

    private fun loadAppInfo(container: LinearLayout, progressBar: ProgressBar) {
        scope.launch {
            val info = withContext(Dispatchers.IO) {
                try {
                    val pm = packageManager
                    val appInfo = pm.getApplicationInfo(packageNameStr, PackageManager.GET_META_DATA)
                    val packageInfo = pm.getPackageInfo(
                        packageNameStr,
                        PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES
                    )

                    val appName = appInfo.loadLabel(pm).toString()
                    val icon = appInfo.loadIcon(pm)
                    val versionName = packageInfo.versionName ?: "unknown"
                    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toString()
                    }

                    val installDate = try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        sdf.format(Date(packageInfo.firstInstallTime))
                    } catch (e: Exception) {
                        "unknown"
                    }

                    val updateDate = try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        sdf.format(Date(packageInfo.lastUpdateTime))
                    } catch (e: Exception) {
                        "unknown"
                    }

                    val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()

                    // APK size estimation
                    var apkSize = 0L
                    try {
                        val apkFile = File(appInfo.sourceDir)
                        if (apkFile.exists()) {
                            apkSize = apkFile.length()
                        }
                    } catch (e: Exception) {
                        Log.w("AppInfo", "Cannot get APK size", e)
                    }

                    // Data size estimation
                    var dataSize = 0L
                    try {
                        val dataDir = File(appInfo.dataDir)
                        dataSize = getDirectorySize(dataDir)
                    } catch (e: Exception) {
                        Log.w("AppInfo", "Cannot get data size", e)
                    }

                    // Try StorageStatsManager on API 26+
                    var totalSize = apkSize + dataSize
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val storageStatsManager = getSystemService(Context.STORAGE_STATS_SERVICE) as StorageManager
                            val storageStats = storageStatsManager.queryStatsForUid(
                                StorageManager.UUID_DEFAULT,
                                appInfo.uid
                            )
                            totalSize = storageStats.appBytes + storageStats.dataBytes + storageStats.cacheBytes
                        }
                    } catch (e: Exception) {
                        // Fall back to file estimation
                        Log.w("AppInfo", "StorageStatsManager failed", e)
                    }

                    AppFullInfo(
                        appName = appName,
                        packageName = packageNameStr,
                        icon = icon,
                        versionName = versionName,
                        versionCode = versionCode,
                        installDate = installDate,
                        updateDate = updateDate,
                        apkSize = apkSize,
                        dataSize = dataSize,
                        totalSize = totalSize,
                        permissions = permissions,
                        sourceDir = appInfo.sourceDir
                    )
                } catch (e: Exception) {
                    null
                }
            }

            progressBar.visibility = View.GONE

            if (info == null) {
                val errorTv = TextView(this@AppInfoActivity).apply {
                    text = "> error: app_not_found"
                    setTextColor(Color.RED)
                    typeface = Typeface.MONOSPACE
                    textSize = 16f
                }
                container.addView(errorTv)
                return@launch
            }

            renderAppInfo(container, info)
        }
    }

    private fun getDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                size += if (file.isDirectory) getDirectorySize(file) else file.length()
            }
        }
        return size
    }

    private fun renderAppInfo(container: LinearLayout, info: AppFullInfo) {
        // Icon + Name
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val iconView = ImageView(this).apply {
            setImageDrawable(info.icon)
            layoutParams = LinearLayout.LayoutParams(128, 128).apply { marginEnd = 24 }
        }
        val nameView = TextView(this).apply {
            text = info.appName
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 24f
            setShadowLayer(8f, 0f, 0f, Color.parseColor("#3300FF00"))
        }
        headerLayout.addView(iconView)
        headerLayout.addView(nameView)
        container.addView(headerLayout)

        // Separator
        container.addView(createSeparator())

        // Details
        container.addView(createInfoRow("package:", info.packageName))
        container.addView(createInfoRow("version:", "${info.versionName} (${info.versionCode})"))
        container.addView(createInfoRow("installed:", info.installDate))
        container.addView(createInfoRow("updated:", info.updateDate))
        container.addView(createInfoRow("apk_size:", formatSize(info.apkSize)))
        container.addView(createInfoRow("data_size:", formatSize(info.dataSize)))
        container.addView(createInfoRow("total_size:", formatSize(info.totalSize)))

        // Permissions
        container.addView(createSeparator())
        container.addView(createSectionTitle("> permissions [${info.permissions.size}]"))
        if (info.permissions.isEmpty()) {
            container.addView(createInfoRow("", "no_permissions_requested"))
        } else {
            for (perm in info.permissions) {
                container.addView(createPermissionRow(perm))
            }
        }

        // Action buttons
        container.addView(createSeparator())
        container.addView(createSectionTitle("> actions"))

        container.addView(createActionButton("LAUNCH") { launchApp() })
        container.addView(createActionButton("UNINSTALL") { uninstallApp() })
        container.addView(createActionButton("FORCE_STOP") { forceStopApp() })
        container.addView(createActionButton("CLEAR_CACHE") { clearCache() })
        container.addView(createActionButton("EXPORT_APK") { exportApk(info.sourceDir, info.appName) })
        container.addView(createActionButton("SHARE") { shareApp(info.packageName, info.appName) })
        container.addView(createActionButton("APP_SETTINGS") { openAppSettings() })
    }

    private fun createInfoRow(label: String, value: String): TextView {
        return TextView(this).apply {
            text = if (label.isNotEmpty()) "$label $value" else value
            setTextColor(Color.parseColor("#00CC00"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setPadding(0, 4, 0, 4)
        }
    }

    private fun createPermissionRow(perm: String): TextView {
        return TextView(this).apply {
            text = "  • $perm"
            setTextColor(Color.parseColor("#008800"))
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(0, 2, 0, 2)
        }
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 16f
            setPadding(0, 16, 0, 8)
            setShadowLayer(6f, 0f, 0f, Color.parseColor("#2200FF00"))
        }
    }

    private fun createSeparator(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#003300"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply { topMargin = 8; bottomMargin = 8 }
        }
    }

    private fun createActionButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = "> $label"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setBackgroundColor(Color.parseColor("#0A1A0A"))
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 4 }
            setOnClickListener { onClick() }
        }
    }

    private fun launchApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageNameStr)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "> error: cannot_launch", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uninstallApp() {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.fromParts("package", packageNameStr)
        }
        startActivity(intent)
    }

    private fun forceStopApp() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageNameStr)
            Toast.makeText(this, "> force_stopped: $packageNameStr", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "> error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearCache() {
        try {
            // Try to clear cache via storage manager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val storageManager = getSystemService(Context.STORAGE_STATS_SERVICE) as StorageManager
                // Best-effort: direct user to settings
            }
            // Fallback: open app storage settings
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageNameStr)
            }
            startActivity(intent)
            Toast.makeText(this, "> open_settings_to_clear_cache", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "> error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportApk(sourceDir: String, appName: String) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val sourceFile = File(sourceDir)
                    if (!sourceFile.exists()) return@withContext false

                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val destDir = File(downloadsDir, "HackerLauncher")
                    if (!destDir.exists()) destDir.mkdirs()

                    val safeName = appName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val destFile = File(destDir, "${safeName}.apk")

                    FileInputStream(sourceFile).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                } catch (e: Exception) {
                    Log.e("AppInfo", "Export APK failed", e)
                    false
                }
            }

            if (success) {
                Toast.makeText(
                    this@AppInfoActivity,
                    "> apk_exported_to_Downloads/HackerLauncher",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@AppInfoActivity,
                    "> error: export_failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun shareApp(packageName: String, appName: String) {
        val shareText = "Check out $appName\nhttps://play.google.com/store/apps/details?id=$packageName"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "> share_app"))
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageNameStr)
        }
        startActivity(intent)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    data class AppFullInfo(
        val appName: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable,
        val versionName: String,
        val versionCode: String,
        val installDate: String,
        val updateDate: String,
        val apkSize: Long,
        val dataSize: Long,
        val totalSize: Long,
        val permissions: List<String>,
        val sourceDir: String
    )
}
