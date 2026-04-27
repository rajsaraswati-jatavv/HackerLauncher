package com.hackerlauncher.services

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 * AutoMessageWorker - PeriodicWorkRequest that runs every 1 hour.
 *
 * FEATURE 1: WorkManager Hourly Auto-Messaging
 * - Proper PeriodicWorkRequest running every 1 hour
 * - Shows a notification with a hacker tip/message each time
 * - Scheduled in HackerApp.kt onCreate() using WorkManager.getInstance(context).enqueueUniquePeriodicWork()
 * - Uses ExistingPeriodicWorkPolicy.KEEP so it doesn't reschedule if already scheduled
 *
 * UPGRADE Features:
 * - Custom message templates (user can set their own messages)
 * - SMS auto-sending to specific contacts every hour
 * - Notification categories (System Status, Security Alert, Reminder, Custom)
 * - Message history viewer (show all sent messages with timestamps)
 * - Scheduled messages (set specific time for messages)
 * - Message content includes: RAM usage, battery level, network status, uptime
 * - Hacker tips rotation with 50+ tips
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
        const val KEY_CUSTOM_TEMPLATES = "custom_templates"
        const val KEY_SMS_CONTACTS = "sms_contacts"
        const val KEY_NOTIFICATION_CATEGORY = "notification_category"
        const val KEY_MESSAGE_HISTORY = "message_history"
        const val KEY_SCHEDULED_MESSAGES = "scheduled_messages"
        const val KEY_SMS_ENABLED = "sms_enabled"

        /** 50+ Hacker tips for hourly notifications */
        val HACKER_TIPS = listOf(
            "Use VPN on public WiFi to prevent MITM attacks.",
            "Always verify SSL certificates before entering credentials.",
            "Enable 2FA on all accounts - it blocks 99.9% of automated attacks.",
            "Use a password manager to generate unique 16+ char passwords.",
            "Keep your device updated - patches fix critical CVEs.",
            "Disable Bluetooth when not in use to prevent BlueBorne attacks.",
            "Use Tor Browser for anonymous web browsing.",
            "Check app permissions regularly - many apps over-request.",
            "Encrypt your storage - Android encryption is hardware-backed.",
            "Use SSH keys instead of passwords for server access.",
            "Monitor network connections with: netstat -tulnp",
            "Scan open ports with: nmap -sV target_ip",
            "Check DNS leaks at dnsleaktest.com after connecting VPN.",
            "Use fail2ban to prevent brute-force SSH attacks.",
            "Regularly backup your data using the 3-2-1 rule.",
            "Disable USB debugging when not needed.",
            "Use Wireshark to analyze network traffic for anomalies.",
            "Enable Android's built-in malware scanner in Google Play Protect.",
            "Use signal or Element for encrypted messaging.",
            "Never reuse passwords across different services.",
            "Check for keyloggers: monitor running processes regularly.",
            "Use uBlock Origin to block malicious ads and trackers.",
            "Verify APK signatures before sideloading apps.",
            "Use Kubernetes network policies to restrict pod communication.",
            "Enable auditd for system call monitoring on Linux servers.",
            "Use strace to debug process behavior: strace -p PID",
            "Check for rootkits with rkhunter and chkrootkit.",
            "Use iptables to configure firewall rules: iptables -L -n -v",
            "Scan web apps with OWASP ZAP for vulnerabilities.",
            "Use burp suite for API security testing.",
            "Enable SELinux in enforcing mode for maximum protection.",
            "Use GPG to encrypt sensitive emails and files.",
            "Monitor logins with: last -a and lastb for failed attempts.",
            "Use DNSSEC to prevent DNS spoofing attacks.",
            "Configure SPF, DKIM, and DMARC for email security.",
            "Use HSTS headers to enforce HTTPS on web servers.",
            "Implement CSP headers to prevent XSS attacks.",
            "Use container scanning tools like Trivy for Docker images.",
            "Enable Android's Find My Device for remote wipe capability.",
            "Review app data usage - high background data may indicate spyware.",
            "Use VirtualBox snapshots before testing potentially dangerous software.",
            "Regularly rotate API keys and access tokens.",
            "Use certificate pinning in mobile apps to prevent MITM.",
            "Check WiFi security: avoid WEP, use WPA3 if available.",
            "Monitor battery drain - sudden spikes may indicate malware.",
            "Use proxychains for routing traffic through multiple proxies.",
            "Enable remote wipe for lost/stolen devices.",
            "Use hashcat or john the ripper for password auditing.",
            "Check for evil twin APs using WiFi scanning tools.",
            "Use airgeddon to secure your wireless networks."
        )
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

            // Show notification with proper category and hacker tip
            val tip = HACKER_TIPS[(count - 1) % HACKER_TIPS.size]
            val fullMessage = "$message\n\n💡 Hacker Tip: $tip"
            sendAutoMessageNotification(fullMessage, category)

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
     * Build message with comprehensive system status info
     * including RAM usage, battery level, network status, uptime
     */
    private fun buildHourlyMessage(count: Int, now: Long, lastTime: Long): String {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date(now))

        val uptimeMs = android.os.SystemClock.elapsedRealtime()
        val uptimeHours = uptimeMs / (1000 * 60 * 60)
        val uptimeMins = (uptimeMs / (1000 * 60)) % 60

        // Get RAM usage
        val ramInfo = getRamUsageInfo()

        // Get battery level
        val batteryInfo = getBatteryInfo()

        // Get network status
        val networkInfo = getNetworkStatus()

        // Get storage info
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

    // ========== System info helpers ==========

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
            val batteryIntent = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
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

    // ========== Notification categories ==========

    private fun determineNotificationCategory(count: Int): String {
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

    // ========== Custom templates ==========

    private fun processCustomTemplates(count: Int, now: Long) {
        // Custom templates are already processed in buildHourlyMessage
    }

    // ========== Scheduled messages ==========

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

    // ========== SMS auto-sending ==========

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

    // ========== Message history ==========

    private fun addMessageToHistory(timestamp: Long, message: String, category: String) {
        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val historyStr = prefs.getString(KEY_MESSAGE_HISTORY, "") ?: ""
            val entry = "$timestamp|$category|${message.take(100)}"
            val newHistory = "$historyStr\n$entry".takeLast(10000)
            prefs.edit().putString(KEY_MESSAGE_HISTORY, newHistory).apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save message history: ${e.message}")
        }
    }
}
