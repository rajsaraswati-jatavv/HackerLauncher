package com.hackerlauncher.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.hackerlauncher.MainActivity
import com.hackerlauncher.utils.Logger
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * AutoUpgradeWorker - PeriodicWorkRequest that runs every 6 hours.
 *
 * FEATURE 2: Auto-Upgrade Loop Mechanism
 * - Checks GitHub API for latest release
 * - Compares version with current version
 * - If new version available, downloads APK and prompts install
 * - Scheduled as PeriodicWorkRequest every 6 hours
 * - GitHub repo: https://github.com/T3RMUXK1NG/HackerLauncher
 * - Scheduled in HackerApp.kt using WorkManager.getInstance(context).enqueueUniquePeriodicWork()
 * - Uses ExistingPeriodicWorkPolicy.KEEP so it doesn't reschedule if already scheduled
 *
 * UPGRADE Features:
 * - Auto-download APK when upgrade available
 * - Changelog viewer (show release notes from GitHub)
 * - Rollback support (keep last version APK)
 * - Beta channel option (check pre-releases too)
 * - Installation prompt with new features summary
 * - Version comparison details
 */
class AutoUpgradeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "AutoUpgradeWorker"
        const val PREFS_NAME = "auto_upgrade_prefs"
        const val KEY_CURRENT_VERSION = "current_version"
        const val KEY_LAST_CHECK_TIME = "last_check_time"
        const val KEY_AVAILABLE_VERSION = "available_version"
        const val KEY_UPGRADE_AVAILABLE = "upgrade_available"
        const val KEY_CHECK_COUNT = "check_count"
        const val GITHUB_REPO = "T3RMUXK1NG/HackerLauncher"
        const val KEY_AUTO_DOWNLOAD = "auto_download"
        const val KEY_CHANGELOG = "changelog"
        const val KEY_ROLLBACK_APK_PATH = "rollback_apk_path"
        const val KEY_BETA_CHANNEL = "beta_channel"
        const val KEY_PREVIOUS_VERSION = "previous_version"
        const val KEY_DOWNLOAD_PATH = "download_path"
        const val KEY_VERSION_COMPARISON = "version_comparison"
        const val CHANNEL_ID_UPGRADE = "upgrade_alerts"
        const val CHANNEL_ID_BETA = "beta_alerts"
    }

    override suspend fun doWork(): Result {
        Logger.i(TAG, "AutoUpgradeWorker triggered - checking for upgrades")
        return try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val checkCount = prefs.getInt(KEY_CHECK_COUNT, 0) + 1
            val isBetaChannel = prefs.getBoolean(KEY_BETA_CHANNEL, false)
            val isAutoDownload = prefs.getBoolean(KEY_AUTO_DOWNLOAD, false)

            val currentVersion = try {
                val pi = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
                pi.versionName ?: "6.0.0"
            } catch (e: Exception) { "6.0.0" }

            // Save previous version for rollback
            val previousVersion = prefs.getString(KEY_CURRENT_VERSION, currentVersion) ?: currentVersion
            prefs.edit().putString(KEY_PREVIOUS_VERSION, previousVersion).apply()

            // Check GitHub API for latest release
            var latestVersion = currentVersion
            var upgradeAvailable = false
            var downloadUrl = ""
            var changelog = ""
            var isPrerelease = false

            try {
                val apiUrl = if (isBetaChannel) {
                    "https://api.github.com/repos/$GITHUB_REPO/releases"
                } else {
                    "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
                }

                val url = URL(apiUrl)
                val connection = url.openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                val response = connection.getInputStream().bufferedReader().readText()

                if (isBetaChannel) {
                    val releases = org.json.JSONArray(response)
                    if (releases.length() > 0) {
                        val latestRelease = releases.getJSONObject(0)
                        latestVersion = latestRelease.optString("tag_name", currentVersion)
                        changelog = latestRelease.optString("body", "No changelog available")
                        isPrerelease = latestRelease.optBoolean("prerelease", false)

                        val assets = latestRelease.optJSONArray("assets")
                        if (assets != null && assets.length() > 0) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk")) {
                                    downloadUrl = asset.optString("browser_download_url", "")
                                    break
                                }
                            }
                        }
                    }
                } else {
                    val json = org.json.JSONObject(response)
                    latestVersion = json.optString("tag_name", currentVersion)
                    changelog = json.optString("body", "No changelog available")

                    val assets = json.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }
                }

                // Compare versions
                upgradeAvailable = compareVersions(latestVersion, currentVersion) > 0

                // Build version comparison details
                val comparisonDetails = buildVersionComparison(currentVersion, latestVersion, changelog, isPrerelease)

                Logger.i(TAG, "Version check: current=$currentVersion latest=$latestVersion upgrade=$upgradeAvailable beta=$isBetaChannel")

                // Update prefs
                prefs.edit()
                    .putString(KEY_CURRENT_VERSION, currentVersion)
                    .putString(KEY_AVAILABLE_VERSION, latestVersion)
                    .putBoolean(KEY_UPGRADE_AVAILABLE, upgradeAvailable)
                    .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                    .putInt(KEY_CHECK_COUNT, checkCount)
                    .putString(KEY_CHANGELOG, changelog.take(5000))
                    .putString(KEY_VERSION_COMPARISON, comparisonDetails)
                    .putBoolean(KEY_BETA_CHANNEL, isBetaChannel)
                    .apply()

                // Show upgrade notification if available
                if (upgradeAvailable) {
                    sendUpgradeNotification(latestVersion, downloadUrl, changelog, isPrerelease)

                    // Auto-download APK if enabled
                    if (isAutoDownload && downloadUrl.isNotEmpty()) {
                        autoDownloadApk(downloadUrl, latestVersion)
                    }

                    // Save rollback info (current version)
                    saveRollbackInfo(currentVersion)
                }

            } catch (e: Exception) {
                Logger.e(TAG, "GitHub API check failed: ${e.message}")
            }

            Logger.i(TAG, "Auto-upgrade check #$checkCount complete. Upgrade: $upgradeAvailable")
            Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, "Auto-upgrade check failed: ${e.message}")
            Result.retry()
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val stripPrefix: (String) -> String = { v -> v.removePrefix("v").removePrefix("V") }
        val parts1 = stripPrefix(v1).split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = stripPrefix(v2).split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    private fun buildVersionComparison(
        currentVersion: String,
        latestVersion: String,
        changelog: String,
        isPrerelease: Boolean
    ): String {
        val stripPrefix: (String) -> String = { v -> v.removePrefix("v").removePrefix("V") }
        val current = stripPrefix(currentVersion).split(".").map { it.toIntOrNull() ?: 0 }
        val latest = stripPrefix(latestVersion).split(".").map { it.toIntOrNull() ?: 0 }

        val sb = StringBuilder()
        sb.appendLine("═══ VERSION COMPARISON ═══")
        sb.appendLine("Current: v$currentVersion")
        sb.appendLine("Available: v$latestVersion")
        if (isPrerelease) sb.appendLine("⚠ PRE-RELEASE (Beta)")
        sb.appendLine()

        val labels = listOf("Major", "Minor", "Patch")
        for (i in 0 until maxOf(current.size, latest.size)) {
            val curr = current.getOrElse(i) { 0 }
            val new = latest.getOrElse(i) { 0 }
            val label = labels.getOrElse(i) { "Build" }
            val status = when {
                new > curr -> "⬆ UPGRADING"
                new < curr -> "⬇ DOWNGRADING"
                else -> "= Same"
            }
            sb.appendLine("$label: $curr → $new $status")
        }

        sb.appendLine()
        sb.appendLine("── Changelog ──")
        sb.append(changelog.take(2000))

        return sb.toString()
    }

    private fun autoDownloadApk(downloadUrl: String, version: String) {
        try {
            Logger.i(TAG, "Auto-downloading APK for version $version")

            val downloadDir = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val fileName = "HackerLauncher-${version.removePrefix("v").removePrefix("V")}.apk"
            val targetFile = File(downloadDir, fileName)

            val url = URL(downloadUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            val input = connection.getInputStream()
            val fileOutput = FileOutputStream(targetFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                fileOutput.write(buffer, 0, bytesRead)
            }
            fileOutput.close()
            input.close()

            // Save download path
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_DOWNLOAD_PATH, targetFile.absolutePath).apply()

            Logger.i(TAG, "APK downloaded to ${targetFile.absolutePath}")

            // Send notification for installation prompt
            sendInstallPromptNotification(version, targetFile.absolutePath)

        } catch (e: Exception) {
            Logger.e(TAG, "Auto-download failed: ${e.message}")
        }
    }

    private fun saveRollbackInfo(currentVersion: String) {
        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_PREVIOUS_VERSION, currentVersion)
                .putString(KEY_ROLLBACK_APK_PATH, "")
                .apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save rollback info: ${e.message}")
        }
    }

    private fun sendInstallPromptNotification(version: String, apkPath: String) {
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_UPGRADE,
                    "Installation Prompts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "App installation prompt notifications"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("upgrade_available", true)
                putExtra("new_version", version)
                putExtra("apk_path", apkPath)
            }
            val openPending = PendingIntent.getActivity(
                applicationContext, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val changelog = prefs.getString(KEY_CHANGELOG, "See release notes for details.") ?: ""

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_UPGRADE)
                .setContentTitle("📥 Ready to Install v$version")
                .setContentText("APK downloaded. Tap to install HackerLauncher $version.")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("HackerLauncher $version is ready to install.\n\nWhat's new:\n${changelog.take(500)}\n\nTap to install."))
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(3002, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to send install prompt: ${e.message}")
        }
    }

    private fun sendUpgradeNotification(version: String, downloadUrl: String, changelog: String, isPrerelease: Boolean) {
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_UPGRADE,
                    "Upgrade Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "App upgrade available notifications"
                }
                notificationManager.createNotificationChannel(channel)

                if (isPrerelease) {
                    val betaChannel = NotificationChannel(
                        CHANNEL_ID_BETA,
                        "Beta Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Beta/pre-release upgrade notifications"
                    }
                    notificationManager.createNotificationChannel(betaChannel)
                }
            }

            val channelId = if (isPrerelease) CHANNEL_ID_BETA else CHANNEL_ID_UPGRADE

            val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("upgrade_available", true)
                putExtra("new_version", version)
            }
            val openPending = PendingIntent.getActivity(
                applicationContext, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (isPrerelease) {
                "🧪 Beta Update Available!"
            } else {
                "Upgrade Available!"
            }

            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setContentTitle(title)
                .setContentText("HackerLauncher $version is available. Tap to update.")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText(buildString {
                        append("A new version of HackerLauncher ($version) is available for download.\n\n")
                        if (isPrerelease) append("⚠ This is a BETA/PRE-RELEASE version.\n\n")
                        append("What's new:\n${changelog.take(500)}\n\n")
                        append(if (downloadUrl.isNotEmpty()) "Tap to download." else "Check Settings for update.")
                    }))
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(3001, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to send upgrade notification: ${e.message}")
        }
    }
}
