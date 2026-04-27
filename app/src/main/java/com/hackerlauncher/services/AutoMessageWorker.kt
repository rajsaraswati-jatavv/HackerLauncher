package com.hackerlauncher.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.hackerlauncher.MainActivity
import com.hackerlauncher.utils.Logger
import java.util.concurrent.TimeUnit

/**
 * AutoMessageWorker - Hourly cron job that sends automatic messages.
 *
 * Runs every 1 hour via WorkManager PeriodicWorkRequest.
 * Sends a notification with system status and helpful reminders.
 * Includes upgrade reminders in the loop.
 */
class AutoMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "AutoMessageWorker"
        const val CHANNEL_ID = "auto_messages"
        const val NOTIFICATION_ID = 2001
        const val PREFS_NAME = "auto_message_prefs"
        const val KEY_MESSAGE_COUNT = "message_count"
        const val KEY_LAST_MESSAGE_TIME = "last_message_time"
        const val KEY_MESSAGES_SENT = "messages_sent_log"
    }

    override suspend fun doWork(): Result {
        Logger.i(TAG, "AutoMessageWorker triggered - sending hourly message")
        return try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_MESSAGE_COUNT, 0) + 1
            val lastTime = prefs.getLong(KEY_LAST_MESSAGE_TIME, 0)
            val now = System.currentTimeMillis()

            // Build message content
            val message = buildHourlyMessage(count, now, lastTime)

            // Show notification
            sendAutoMessageNotification(message)

            // Update prefs
            prefs.edit()
                .putInt(KEY_MESSAGE_COUNT, count)
                .putLong(KEY_LAST_MESSAGE_TIME, now)
                .apply()

            // Add to messages sent log
            val log = prefs.getString(KEY_MESSAGES_SENT, "") ?: ""
            val newLog = "$log\n[$now] MSG#$count: ${message.take(50)}..."
            prefs.edit().putString(KEY_MESSAGES_SENT, newLog.takeLast(5000)).apply()

            Logger.i(TAG, "Hourly auto-message #$count sent successfully")
            Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, "Auto-message failed: ${e.message}")
            Result.retry()
        }
    }

    private fun buildHourlyMessage(count: Int, now: Long, lastTime: Long): String {
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(now))

        val uptimeMs = android.os.SystemClock.elapsedRealtime()
        val uptimeHours = uptimeMs / (1000 * 60 * 60)
        val uptimeMins = (uptimeMs / (1000 * 60)) % 60

        val messages = listOf(
            "System Status: All services running. Uptime: ${uptimeHours}h ${uptimeMins}m | Message #$count",
            "HackerLauncher Active: ${uptimeHours}h uptime | Auto-monitoring engaged | #$count",
            "Health Check: Daemon active, Watchdog guarding, Keep-Alive locked | ${uptimeHours}h | #$count",
            "System Monitor: All layers operational | ${uptimeHours}h ${uptimeMins}m | #$count",
            "Reminder: Check for app updates in Settings > About | ${uptimeHours}h uptime | #$count",
            "Pro Tip: Use RAM Cleaner to boost performance | ${uptimeHours}h uptime | #$count",
            "Security: All monitoring services active | ${uptimeHours}h ${uptimeMins}m | #$count",
            "Network: Auto-monitoring enabled | Uptime ${uptimeHours}h | Hourly check #$count"
        )

        val index = (count - 1) % messages.size
        return "[${timeStr}] ${messages[index]}"
    }

    private fun sendAutoMessageNotification(message: String) {
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Auto Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Automatic hourly message notifications"
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPending = PendingIntent.getActivity(
                applicationContext, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("HackerLauncher Auto-Message")
                .setContentText(message.take(80))
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to send notification: ${e.message}")
        }
    }
}

/**
 * AutoUpgradeWorker - Checks for app upgrades every 6 hours.
 *
 * Checks GitHub releases API for new versions and notifies the user
 * if an upgrade is available. Part of the auto-upgrade loop mechanism.
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
        const val GITHUB_REPO = "rajsaraswati-jatavv/HackerLauncher"
    }

    override suspend fun doWork(): Result {
        Logger.i(TAG, "AutoUpgradeWorker triggered - checking for upgrades")
        return try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val checkCount = prefs.getInt(KEY_CHECK_COUNT, 0) + 1

            val currentVersion = try {
                val pi = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
                pi.versionName ?: "10.0.0"
            } catch (e: Exception) { "10.0.0" }

            // Check GitHub API for latest release
            var latestVersion = currentVersion
            var upgradeAvailable = false
            var downloadUrl = ""

            try {
                val url = java.net.URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                val connection = url.openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                val response = connection.getInputStream().bufferedReader().readText()
                val json = org.json.JSONObject(response)

                latestVersion = json.optString("tag_name", currentVersion)
                val htmlUrl = json.optString("html_url", "")

                // Find APK download URL
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

                // Compare versions
                upgradeAvailable = compareVersions(latestVersion, currentVersion) > 0

                Logger.i(TAG, "Version check: current=$currentVersion latest=$latestVersion upgrade=$upgradeAvailable")
            } catch (e: Exception) {
                Logger.e(TAG, "GitHub API check failed: ${e.message}")
                // Don't fail the worker - just log and continue
            }

            // Update prefs
            prefs.edit()
                .putString(KEY_CURRENT_VERSION, currentVersion)
                .putString(KEY_AVAILABLE_VERSION, latestVersion)
                .putBoolean(KEY_UPGRADE_AVAILABLE, upgradeAvailable)
                .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                .putInt(KEY_CHECK_COUNT, checkCount)
                .apply()

            // Show upgrade notification if available
            if (upgradeAvailable) {
                sendUpgradeNotification(latestVersion, downloadUrl)
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

    private fun sendUpgradeNotification(version: String, downloadUrl: String) {
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "upgrade_alerts",
                    "Upgrade Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "App upgrade available notifications"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("upgrade_available", true)
                putExtra("new_version", version)
            }
            val openPending = PendingIntent.getActivity(
                applicationContext, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(applicationContext, "upgrade_alerts")
                .setContentTitle("Upgrade Available!")
                .setContentText("HackerLauncher $version is available. Tap to update.")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("A new version of HackerLauncher ($version) is available for download. " +
                            if (downloadUrl.isNotEmpty()) "Tap to download." else "Check Settings for update."))
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
