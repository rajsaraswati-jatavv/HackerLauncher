package com.hackerlauncher.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.hackerlauncher.MainActivity
import com.hackerlauncher.R
import com.hackerlauncher.utils.Logger

class HackerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "hacker_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.hackerlauncher.START_SERVICE"
        const val ACTION_STOP = "com.hackerlauncher.STOP_SERVICE"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val logger = Logger

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Logger.log("HackerForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        acquireWakeLock()
        BackgroundTaskManager.init(this)

        Logger.log("HackerForegroundService started with START_STICKY")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        Logger.log("HackerForegroundService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hacker Launcher Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps HackerLauncher running in background"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, HackerForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HackerLauncher")
            .setContentText("Core service running")
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_stop, "Stop", stopPending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HackerLauncher::CoreWakeLock"
            ).apply {
                acquire(4 * 60 * 60 * 1000L) // 4 hours max
            }
        } catch (e: Exception) {
            Logger.log("WakeLock acquire failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Logger.log("WakeLock release failed: ${e.message}")
        }
    }
}
