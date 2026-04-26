package com.hackerlauncher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.WorkManager
import com.hackerlauncher.services.*
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

        createAllNotificationChannels()
        WorkManager.getInstance(this)

        // Auto-start all always-running services on app create
        startAlwaysRunningServices()

        logger.info("HackerApp v6.0 Ultimate initialized - ALL services running")
    }

    private fun createAllNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel("daemon_service", "Daemon Service", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Always-running daemon service"; setShowBadge(false)
                },
                NotificationChannel("watchdog_service", "Watchdog Service", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Service watchdog monitor"; setShowBadge(false)
                },
                NotificationChannel("keep_alive", "Keep Alive Service", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Multi-layer keep-alive"; setShowBadge(false)
                },
                NotificationChannel("network_monitor", "Network Monitor", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Network state monitoring"; setShowBadge(false)
                },
                NotificationChannel("location_tracker", "Location Tracker", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Location tracking service"; setShowBadge(false)
                },
                NotificationChannel("process_monitor", "Process Monitor", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Process monitoring service"; setShowBadge(false)
                },
                NotificationChannel("system_monitor", "System Monitor", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "System health monitoring"; setShowBadge(false)
                },
                NotificationChannel(HackerForegroundService.CHANNEL_ID, "Hacker Service", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Core service notifications"; setShowBadge(false)
                },
                NotificationChannel("chat_messages", "Chat Messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Chat message notifications"
                },
                NotificationChannel("alerts", "Security Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Important security alerts"
                },
                NotificationChannel("network_alerts", "Network Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Network scan and monitoring alerts"
                },
                NotificationChannel("scan_results", "Scan Results", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Background scan results"
                },
                NotificationChannel("app_lock", "App Lock", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "App lock notifications"; setShowBadge(false)
                },
                NotificationChannel("weather_updates", "Weather Updates", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Weather update notifications"
                },
                NotificationChannel("todo_reminders", "Todo Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Task due date reminders"
                },
                NotificationChannel("intruder_alerts", "Intruder Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Wrong PIN attempt alerts with photo"
                },
                NotificationChannel("screen_recorder", "Screen Recorder", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Screen recording in progress"; setShowBadge(false)
                },
                NotificationChannel("audio_recorder", "Audio Recorder", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Audio recording in progress"; setShowBadge(false)
                },
                NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Download progress notifications"
                },
                NotificationChannel("battery_alerts", "Battery Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Battery level alerts"
                },
                NotificationChannel("system_health", "System Health", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "System health reports"
                }
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(channels)
        }
    }

    private fun startAlwaysRunningServices() {
        val services = listOf(
            DaemonService::class.java,
            WatchdogService::class.java,
            KeepAliveService::class.java,
            HackerForegroundService::class.java,
            NetworkMonitorService::class.java,
            ProcessMonitorService::class.java,
            SystemMonitorService::class.java
        )

        for (serviceClass in services) {
            try {
                val intent = android.content.Intent(this, serviceClass).apply { action = "ACTION_START" }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                logger.info("Auto-started: ${serviceClass.simpleName}")
            } catch (e: Exception) {
                logger.error("Auto-start failed ${serviceClass.simpleName}: ${e.message}")
            }
        }
    }
}
