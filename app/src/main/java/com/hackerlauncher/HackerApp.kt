package com.hackerlauncher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.WorkManager
import com.hackerlauncher.services.HackerForegroundService
import com.hackerlauncher.utils.Logger

class HackerApp : Application() {

    private val logger = Logger()

    override fun onCreate() {
        super.onCreate()

        // Create notification channels
        createNotificationChannels()

        // Initialize WorkManager
        WorkManager.getInstance(this)

        logger.info("HackerApp initialized")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                HackerForegroundService.CHANNEL_ID,
                "Hacker Launcher Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Core service notifications"
                setShowBadge(false)
            }

            val chatChannel = NotificationChannel(
                "chat_messages",
                "Chat Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Chat message notifications"
            }

            val alertChannel = NotificationChannel(
                "alerts",
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important security alerts"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(serviceChannel, chatChannel, alertChannel))
        }
    }
}
