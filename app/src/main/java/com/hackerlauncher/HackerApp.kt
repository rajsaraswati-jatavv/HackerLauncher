package com.hackerlauncher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager
import com.hackerlauncher.services.*
import com.hackerlauncher.utils.Logger

class HackerApp : Application(), Configuration.Provider {

    companion object {
        lateinit var instance: HackerApp
            private set
    }

    private val logger = Logger

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            createAllNotificationChannels()
        } catch (e: Exception) {
            Logger.error("Failed to create notification channels: ${e.message}")
        }

        try {
            WorkManager.initialize(this, workManagerConfiguration)
        } catch (e: Exception) {
            Logger.error("WorkManager already initialized: ${e.message}")
        }

        // Stagger service starts - DO NOT start all at once (causes crash)
        startServicesStaggered()

        Logger.info("HackerApp v10.0 CRASH-FIX initialized")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

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
                },
                NotificationChannel("auto_messages", "Auto Messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Automatic hourly message notifications"
                }
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(channels)
        }
    }

    /**
     * STAGGERED SERVICE START - CRITICAL FIX
     * Starting all services at once causes ForegroundServiceDidNotStartInTimeException
     * Now we start them with delays to give each service time to call startForeground()
     */
    private fun startServicesStaggered() {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        // Start only the core service immediately
        tryStartService(HackerForegroundService::class.java, "ACTION_START")

        // Stagger the rest with increasing delays
        mainHandler.postDelayed({
            tryStartService(DaemonService::class.java, "ACTION_START")
        }, 2000L)

        mainHandler.postDelayed({
            tryStartService(WatchdogService::class.java, "ACTION_START")
        }, 4000L)

        mainHandler.postDelayed({
            tryStartService(KeepAliveService::class.java, "ACTION_START")
        }, 6000L)

        mainHandler.postDelayed({
            tryStartService(NetworkMonitorService::class.java, "ACTION_START")
        }, 8000L)

        mainHandler.postDelayed({
            tryStartService(ProcessMonitorService::class.java, "ACTION_START")
        }, 10000L)

        mainHandler.postDelayed({
            tryStartService(SystemMonitorService::class.java, "ACTION_START")
        }, 12000L)
    }

    private fun tryStartService(serviceClass: Class<*>, action: String) {
        try {
            val intent = android.content.Intent(this, serviceClass).apply { this.action = action }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Logger.info("Started service: ${serviceClass.simpleName}")
        } catch (e: Exception) {
            Logger.error("Failed to start ${serviceClass.simpleName}: ${e.message}")
        }
    }
}
