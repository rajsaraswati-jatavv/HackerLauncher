package com.hackerlauncher.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.Collections

/**
 * NotificationListenerService for HackerLauncher.
 * Captures all notifications, stores them in memory (max 500),
 * provides badge counts per package, notification history,
 * and broadcasts updates to the UI.
 */
class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
        private const val MAX_NOTIFICATIONS = 500
        const val ACTION_NOTIFICATION_UPDATE = "com.hackerlauncher.launcher.NOTIFICATION_UPDATE"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_ACTION = "extra_action"
        const val ACTION_POSTED = "posted"
        const val ACTION_REMOVED = "removed"

        // In-memory notification store
        private val notificationsList =
            Collections.synchronizedList(mutableListOf<NotificationEntry>())

        /**
         * Get all stored notifications.
         */
        fun getNotifications(): List<NotificationEntry> {
            return synchronized(notificationsList) {
                notificationsList.toList()
            }
        }

        /**
         * Get notifications for a specific package.
         */
        fun getNotificationsForPackage(packageName: String): List<NotificationEntry> {
            return synchronized(notificationsList) {
                notificationsList.filter { it.packageName == packageName }
            }
        }

        /**
         * Get badge count for a specific package.
         */
        fun getBadgeCount(packageName: String): Int {
            return synchronized(notificationsList) {
                notificationsList.count { it.packageName == packageName }
            }
        }

        /**
         * Get total notification count.
         */
        fun getTotalCount(): Int {
            return synchronized(notificationsList) {
                notificationsList.size
            }
        }

        /**
         * Get set of packages with active notifications.
         */
        fun getActivePackages(): Set<String> {
            return synchronized(notificationsList) {
                notificationsList.map { it.packageName }.toSet()
            }
        }

        /**
         * Get notification history (most recent first).
         */
        fun getHistory(limit: Int = 50): List<NotificationEntry> {
            return synchronized(notificationsList) {
                notificationsList.takeLast(limit).reversed()
            }
        }

        /**
         * Clear all stored notifications.
         */
        fun clearAll() {
            synchronized(notificationsList) {
                notificationsList.clear()
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Handle any custom broadcast intents if needed
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "> notification_listener_created")
        val filter = IntentFilter(ACTION_NOTIFICATION_UPDATE)
        registerReceiver(receiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.w(TAG, "> receiver_already_unregistered")
        }
        Log.d(TAG, "> notification_listener_destroyed")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "> notification_listener_connected")

        // Load current active notifications
        try {
            val activeSbn = activeNotifications
            if (activeSbn != null) {
                synchronized(notificationsList) {
                    notificationsList.clear()
                    for (sbn in activeSbn) {
                        val entry = parseNotification(sbn)
                        if (entry != null && notificationsList.size < MAX_NOTIFICATIONS) {
                            notificationsList.add(entry)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "> error_loading_active_notifications: ${e.message}")
        }

        broadcastUpdate("", ACTION_POSTED)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "> notification_listener_disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        Log.d(TAG, "> notification_posted: ${sbn.packageName}")

        val entry = parseNotification(sbn) ?: return

        synchronized(notificationsList) {
            // Remove duplicate if exists
            notificationsList.removeAll {
                it.key == entry.key
            }

            // Add new entry
            if (notificationsList.size >= MAX_NOTIFICATIONS) {
                notificationsList.removeAt(0)
            }
            notificationsList.add(entry)
        }

        broadcastUpdate(sbn.packageName, ACTION_POSTED)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        Log.d(TAG, "> notification_removed: ${sbn.packageName}")

        val key = sbn.key
        synchronized(notificationsList) {
            // Mark as removed rather than deleting for history
            val entry = notificationsList.find { it.key == key }
            entry?.isRemoved = true
        }

        broadcastUpdate(sbn.packageName, ACTION_REMOVED)
    }

    /**
     * Dismiss a notification by its key.
     */
    fun dismissNotification(key: String) {
        try {
            val sbn = synchronized(notificationsList) {
                notificationsList.find { it.key == key }
            }

            if (sbn != null) {
                // Find the StatusBarNotification to cancel
                val active = activeNotifications
                active?.let { notifications ->
                    for (notification in notifications) {
                        if (notification.key == key) {
                            cancelNotification(key)
                            Log.d(TAG, "> notification_dismissed: $key")
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "> error_dismissing_notification: ${e.message}")
        }
    }

    /**
     * Dismiss all notifications for a package.
     */
    fun dismissAllForPackage(packageName: String) {
        try {
            val active = activeNotifications
            active?.let { notifications ->
                for (sbn in notifications) {
                    if (sbn.packageName == packageName) {
                        cancelNotification(sbn.key)
                    }
                }
            }
            synchronized(notificationsList) {
                notificationsList.removeAll { it.packageName == packageName }
            }
            broadcastUpdate(packageName, ACTION_REMOVED)
            Log.d(TAG, "> all_dismissed_for: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "> error_dismissing_all: ${e.message}")
        }
    }

    /**
     * Dismiss all notifications.
     */
    fun dismissAll() {
        try {
            cancelAllNotifications()
            synchronized(notificationsList) {
                notificationsList.clear()
            }
            broadcastUpdate("", ACTION_REMOVED)
            Log.d(TAG, "> all_notifications_dismissed")
        } catch (e: Exception) {
            Log.e(TAG, "> error_dismissing_all: ${e.message}")
        }
    }

    private fun parseNotification(sbn: StatusBarNotification): NotificationEntry? {
        return try {
            val notification = sbn.notification
            val extras = notification.extras

            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

            NotificationEntry(
                key = sbn.key,
                packageName = sbn.packageName,
                id = sbn.id,
                tag = sbn.tag ?: "",
                postTime = sbn.postTime,
                title = title,
                text = if (bigText.isNotEmpty()) bigText else text,
                subText = subText,
                isOngoing = sbn.isOngoing,
                isClearable = sbn.isClearable,
                category = notification.category ?: "",
                priority = notification.priority,
                isRemoved = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "> error_parsing_notification: ${e.message}")
            null
        }
    }

    private fun broadcastUpdate(packageName: String, action: String) {
        val intent = Intent(ACTION_NOTIFICATION_UPDATE).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_ACTION, action)
            setPackage("com.hackerlauncher.launcher")
        }
        sendBroadcast(intent)
    }

    /**
     * Data class representing a stored notification entry.
     */
    data class NotificationEntry(
        val key: String,
        val packageName: String,
        val id: Int,
        val tag: String,
        val postTime: Long,
        val title: String,
        val text: String,
        val subText: String,
        val isOngoing: Boolean,
        val isClearable: Boolean,
        val category: String,
        val priority: Int,
        var isRemoved: Boolean = false
    ) {
        /**
         * Get formatted post time.
         */
        fun getFormattedTime(): String {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(postTime))
        }

        /**
         * Get formatted post date.
         */
        fun getFormattedDate(): String {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(postTime))
        }

        /**
         * Get relative time string (e.g., "2m ago").
         */
        fun getRelativeTime(): String {
            val now = System.currentTimeMillis()
            val diff = now - postTime
            return when {
                diff < 60_000 -> "${diff / 1000}s_ago"
                diff < 3_600_000 -> "${diff / 60_000}m_ago"
                diff < 86_400_000 -> "${diff / 3_600_000}h_ago"
                else -> "${diff / 86_400_000}d_ago"
            }
        }
    }
}
