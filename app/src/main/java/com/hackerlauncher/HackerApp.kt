package com.hackerlauncher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.WorkManager
import com.hackerlauncher.services.HackerForegroundService
import com.hackerlauncher.utils.Logger

class HackerApp : Application() {

    companion object {
        lateinit var instance: HackerApp
            private set
    }

    private val logger = Logger()

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Create notification channels
        createNotificationChannels()

        // Initialize WorkManager
        WorkManager.getInstance(this)

        logger.info("HackerApp v3.0 initialized")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    HackerForegroundService.CHANNEL_ID,
                    "Hacker Launcher Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Core service notifications"
                    setShowBadge(false)
                },
                NotificationChannel(
                    "chat_messages",
                    "Chat Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Chat message notifications"
                },
                NotificationChannel(
                    "alerts",
                    "Security Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Important security alerts"
                },
                NotificationChannel(
                    "network_alerts",
                    "Network Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Network scan and monitoring alerts"
                },
                NotificationChannel(
                    "scan_results",
                    "Scan Results",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background scan results"
                }
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(channels)
        }
    }
}
