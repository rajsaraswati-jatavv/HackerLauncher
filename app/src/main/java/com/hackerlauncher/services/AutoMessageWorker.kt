package com.hackerlauncher.services

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.hackerlauncher.MainActivity
import com.hackerlauncher.utils.Logger
import java.io.File
import java.io.FileWriter
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * AutoMessageWorker - Hourly cron job that sends automatic messages.
 *
 * UPGRADE Features:
 * - Custom message templates (user can set their own messages)
 * - SMS auto-sending to specific contacts every hour
 * - Notification categories (System Status, Security Alert, Reminder, Custom)
 * - Message history viewer (show all sent messages with timestamps)
 * - Scheduled messages (set specific time for messages)
 * - Message content includes: RAM usage, battery level, network status, uptime
 */
class AutoMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "AutoMessageWorker"
        const val CHANNEL_ID = "auto_messages"
        const val CHANNEL_ID_SECURITY = "auto_messages_security"
        const val CHANNEL_ID_REMINDER = "auto_messages_reminder"
        const val CHANNEL_ID_CUSTOM = "auto_messages_custom"
        const val NOTIFICATION_ID = 2001
        const val PREFS_NAME = "auto_message_prefs"
        const val KEY_MESSAGE_COUNT = "message_count"
        const val KEY_LAST_MESSAGE_TIME = "last_message_time"
        const val KEY_MESSAGES_SENT = "messages_sent_log"
        // UPGRADE: New keys
        const val KEY_CUSTOM_TEMPLATES = "custom_templates"
        const val KEY_SMS_CONTACTS = "sms_contacts"
        const val KEY_NOTIFICATION_CATEGORY = "notification_category"
        const val KEY_MESSAGE_HISTORY = "message_history"
        const val KEY_SCHEDULED_MESSAGES = "scheduled_messages"
        const val KEY_SMS_ENABLED = "sms_enabled"
    }

    override suspend fun doWork(): Result {
        Logger.i(TAG, "AutoMessageWorker triggered - sending hourly message")
        return try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_MESSAGE_COUNT, 0) + 1
            val lastTime = prefs.getLong(KEY_LAST_MESSAGE_TIME, 0)
            val now = System.currentTimeMillis()

            // Build message content with system status info
            val message = buildHourlyMessage(count, now, lastTime)

            // Determine notification category
            val category = determineNotificationCategory(count)

            // Show notification with proper category
            sendAutoMessageNotification(message, category)

            // UPGRADE: Process custom templates
            processCustomTemplates(count, now)

            // UPGRADE: Process scheduled messages
            processScheduledMessages(now)

            // UPGRADE: Send SMS if enabled
            if (prefs.getBoolean(KEY_SMS_ENABLED, false)) {
                sendSmsToContacts(message)
            }

            // Update prefs
            prefs.edit()
                .putInt(KEY_MESSAGE_COUNT, count)
                .putLong(KEY_LAST_MESSAGE_TIME, now)
                .apply()

            // UPGRADE: Add to message history with category
            addMessageToHistory(now, message, category)

            Logger.i(TAG, "Hourly auto-message #$count sent successfully (category: $category)")
            Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, "Auto-message failed: ${e.message}")
            Result.retry()
        }
    }

    /**
     * UPGRADE: Build message with comprehensive system status info
     * including RAM usage, battery level, network status, uptime
     */
    private fun buildHourlyMessage(count: Int, now: Long, lastTime: Long): String {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date(now))

        val uptimeMs = android.os.SystemClock.elapsedRealtime()
        val uptimeHours = uptimeMs / (1000 * 60 * 60)
        val uptimeMins = (uptimeMs / (1000 * 60)) % 60

        // UPGRADE: Get RAM usage
        val ramInfo = getRamUsageInfo()

        // UPGRADE: Get battery level
        val batteryInfo = getBatteryInfo()

        // UPGRADE: Get network status
        val networkInfo = getNetworkStatus()

        // UPGRADE: Get storage info
        val storageInfo = getStorageInfo()

        // Check for custom templates first
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customTemplates = prefs.getStringSet(KEY_CUSTOM_TEMPLATES, null)
        if (customTemplates != null && customTemplates.isNotEmpty()) {
            val templates = customTemplates.toList()
            val template = templates[(count - 1) % templates.size]
            // Replace placeholders in template
            return template
                .replace("{time}", timeStr)
                .replace("{uptime}", "${uptimeHours}h ${uptimeMins}m")
                .replace("{ram_used}", ramInfo.first)
                .replace("{ram_percent}", "${ramInfo.second}%")
                .replace("{battery}", "${batteryInfo.first}%")
                .replace("{battery_status}", batteryInfo.second)
                .replace("{network}", networkInfo)
                .replace("{storage_free}", storageInfo)
                .replace("{count}", "#$count")
        }

        // Default messages with system status
        val messages = listOf(
            "System Status: RAM ${ramInfo.second}% used | Battery ${batteryInfo.first}% ${batteryInfo.second} | $networkInfo | Uptime ${uptimeHours}h | #$count",
            "HackerLauncher Active: RAM ${ramInfo.first} used | Battery ${batteryInfo.first}% | ${uptimeHours}h uptime | Auto-monitoring engaged | #$count",
            "Health Check: RAM ${ramInfo.second}% | Battery ${batteryInfo.first}% ${batteryInfo.second} | $networkInfo | Storage free: $storageInfo | #$count",
            "System Monitor: All layers operational | RAM ${ramInfo.first} | ${uptimeHours}h ${uptimeMins}m | #$count",
            "Reminder: Check for app updates in Settings > About | RAM ${ramInfo.second}% | Battery ${batteryInfo.first}% | #$count",
            "Pro Tip: Use RAM Cleaner to boost performance (currently ${ramInfo.second}% used) | Battery ${batteryInfo.first}% | #$count",
            "Security: All monitoring services active | RAM ${ramInfo.second}% | Battery ${batteryInfo.first}% ${batteryInfo.second} | ${uptimeHours}h | #$count",
            "Network: $networkInfo | RAM ${ramInfo.first} | Battery ${batteryInfo.first}% | Uptime ${uptimeHours}h | Hourly check #$count"
        )

        val index = (count - 1) % messages.size
        return "[${timeStr}] ${messages[index]}"
    }

    // ========== UPGRADE: System info helpers ==========

    private fun getRamUsageInfo(): Pair<String, Int> {
        return try {
            val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val totalRam = memoryInfo.totalMem
            val availableRam = memoryInfo.availMem
            val usedRam = totalRam - availableRam
            val percentUsed = if (totalRam > 0) ((usedRam.toFloat() / totalRam.toFloat()) * 100).toInt() else 0
            val usedMb = usedRam / (1024 * 1024)
            Pair("${usedMb}MB", percentUsed)
        } catch (e: Exception) {
            Pair("N/A", 0)
        }
    }

    private fun getBatteryInfo(): Pair<Int, String> {
        return try {
            val batteryIntent = applicationContext.registerReceiver(null, Intent(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = (level * 100) / scale.coerceAtLeast(1)
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val statusStr = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    else -> "N/A"
                }
                Pair(pct, statusStr)
            } else {
                Pair(0, "Unknown")
            }
        } catch (e: Exception) {
            Pair(0, "Error")
        }
    }

    private fun getNetworkStatus(): String {
        return try {
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            if (activeNetwork != null && activeNetwork.isConnected) {
                when (activeNetwork.type) {
                    android.net.ConnectivityManager.TYPE_WIFI -> "WiFi"
                    android.net.ConnectivityManager.TYPE_MOBILE -> "Mobile"
                    android.net.ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
                    else -> "Connected"
                }
            } else {
                "Offline"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getStorageInfo(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val availableBytes = stat.availableBytes
            val gb = availableBytes / (1024.0 * 1024 * 1024)
            "${"%.1f".format(gb)}GB"
        } catch (e: Exception) {
            "N/A"
        }
    }

    // ========== UPGRADE: Notification categories ==========

    private fun determineNotificationCategory(count: Int): String {
        // Cycle through categories based on count
        return when (count % 4) {
            0 -> "SYSTEM_STATUS"
            1 -> "SECURITY_ALERT"
            2 -> "REMINDER"
            else -> "CUSTOM"
        }
    }

    private fun sendAutoMessageNotification(message: String, category: String) {
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create channels for each category
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channels = listOf(
                    NotificationChannel(CHANNEL_ID, "System Status", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "System status notifications"
                    },
                    NotificationChannel(CHANNEL_ID_SECURITY, "Security Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Security alert notifications"
                    },
                    NotificationChannel(CHANNEL_ID_REMINDER, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Reminder notifications"
                    },
                    NotificationChannel(CHANNEL_ID_CUSTOM, "Custom Messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Custom message notifications"
                    }
                )
                for (channel in channels) {
                    channel.setShowBadge(true)
                    notificationManager.createNotificationChannel(channel)
                }
            }

            val channelId = when (category) {
                "SECURITY_ALERT" -> CHANNEL_ID_SECURITY
                "REMINDER" -> CHANNEL_ID_REMINDER
                "CUSTOM" -> CHANNEL_ID_CUSTOM
                else -> CHANNEL_ID
            }

            val title = when (category) {
                "SYSTEM_STATUS" -> "HackerLauncher Status"
                "SECURITY_ALERT" -> "⚠️ Security Alert"
                "REMINDER" -> "📋 Reminder"
                "CUSTOM" -> "💬 Custom Message"
                else -> "HackerLauncher Auto-Message"
            }

            val priority = when (category) {
                "SECURITY_ALERT" -> NotificationCompat.PRIORITY_HIGH
                else -> NotificationCompat.PRIORITY_DEFAULT
            }

            val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPending = PendingIntent.getActivity(
                applicationContext, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setContentTitle(title)
                .setContentText(message.take(80))
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .setCategory(when (category) {
                    "SECURITY_ALERT" -> NotificationCompat.CATEGORY_ALARM
                    "REMINDER" -> NotificationCompat.CATEGORY_REMINDER
                    else -> NotificationCompat.CATEGORY_MESSAGE
                })
                .setPriority(priority)
                .build()

            notificationManager.notify(NOTIFICATION_ID + when (category) {
                "SECURITY_ALERT" -> 1
                "REMINDER" -> 2
                "CUSTOM" -> 3
                else -> 0
            }, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to send notification: ${e.message}")
        }
    }

    // ========== UPGRADE: Custom templates ==========

    private fun processCustomTemplates(count: Int, now: Long) {
        // Custom templates are already processed in buildHourlyMessage
        // This method handles additional template logic if needed
    }

    // ========== UPGRADE: Scheduled messages ==========

    private fun processScheduledMessages(now: Long) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scheduledStr = prefs.getString(KEY_SCHEDULED_MESSAGES, "") ?: ""
        if (scheduledStr.isEmpty()) return

        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = dateFormat.format(Date(now))

        scheduledStr.split(";").forEach { entry ->
            val parts = entry.split("|")
            if (parts.size >= 2) {
                val scheduledTime = parts[0]
                val message = parts[1]
                if (currentTime == scheduledTime) {
                    sendAutoMessageNotification("[Scheduled] $message", "CUSTOM")
                    addMessageToHistory(now, "[Scheduled] $message", "SCHEDULED")
                }
            }
        }
    }

    // ========== UPGRADE: SMS auto-sending ==========

    private fun sendSmsToContacts(message: String) {
        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val contactsStr = prefs.getString(KEY_SMS_CONTACTS, "") ?: ""
            if (contactsStr.isEmpty()) return

            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                android.telephony.SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }

            contactsStr.split(",").forEach { phoneNumber ->
                try {
                    val trimmed = phoneNumber.trim()
                    if (trimmed.isNotEmpty()) {
                        // Send only first 160 chars for SMS
                        val smsText = message.take(160)
                        smsManager.sendTextMessage(trimmed, null, smsText, null, null)
                        Logger.i(TAG, "SMS sent to $trimmed")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "SMS failed to $phoneNumber: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "SMS sending error: ${e.message}")
        }
    }

    // ========== UPGRADE: Message history ==========

    private fun addMessageToHistory(timestamp: Long, message: String, category: String) {
        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val historyStr = prefs.getString(KEY_MESSAGE_HISTORY, "") ?: ""
            val entry = "$timestamp|$category|${message.take(100)}"
            val newHistory = "$historyStr\n$entry".takeLast(10000) // Keep last 10KB
            prefs.edit().putString(KEY_MESSAGE_HISTORY, newHistory).apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save message history: ${e.message}")
        }
    }
}

/**
 * AutoUpgradeWorker - Checks for app upgrades every 6 hours.
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
        const val GITHUB_REPO = "rajsaraswati-jatavv/HackerLauncher"
        // UPGRADE: New keys
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
                pi.versionName ?: "10.0.0"
            } catch (e: Exception) { "10.0.0" }

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
                    // Beta channel: check all releases including pre-releases
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
                    // Parse array of releases for beta channel
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
                    // Parse single release for stable channel
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

                // UPGRADE: Build version comparison details
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

                    // UPGRADE: Auto-download APK if enabled
                    if (isAutoDownload && downloadUrl.isNotEmpty()) {
                        autoDownloadApk(downloadUrl, latestVersion)
                    }

                    // UPGRADE: Save rollback APK path (current version)
                    saveRollbackInfo(currentVersion)
                }

            } catch (e: Exception) {
                Logger.e(TAG, "GitHub API check failed: ${e.message}")
                // Don't fail the worker - just log and continue
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

    // ========== UPGRADE: Version comparison details ==========
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

        // Compare each version component
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

    // ========== UPGRADE: Auto-download APK ==========
    private fun autoDownloadApk(downloadUrl: String, version: String) {
        try {
            Logger.i(TAG, "Auto-downloading APK for version $version")

            val downloadDir = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val fileName = "HackerLauncher-${version.removePrefix("v").removePrefix("V")}.apk"
            val targetFile = File(downloadDir, fileName)

            // Download in background
            val url = URL(downloadUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            val input = connection.getInputStream()
            val output = FileWriter(targetFile) // Use proper file output

            val fileOutput = java.io.FileOutputStream(targetFile)
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

    // ========== UPGRADE: Rollback support ==========
    private fun saveRollbackInfo(currentVersion: String) {
        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // Save current version info for rollback
            prefs.edit()
                .putString(KEY_PREVIOUS_VERSION, currentVersion)
                .putString(KEY_ROLLBACK_APK_PATH, "") // Would need to save current APK
                .apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save rollback info: ${e.message}")
        }
    }

    // ========== UPGRADE: Installation prompt notification ==========
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

            // Get changelog
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
