package com.hackerlauncher.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.hackerlauncher.utils.Logger

class OverlayService : Service() {

    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null
    private val logger = Logger

    companion object {
        const val CHANNEL_ID = "overlay_service"
        const val NOTIFICATION_ID = 1010

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var overlayText: String = "HackerLauncher Active"

        @Volatile
        var isVisible = false

        fun show(text: String = "HackerLauncher Active") {
            overlayText = text
            isVisible = true
        }

        fun hide() {
            isVisible = false
        }

        fun updateText(text: String) {
            overlayText = text
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Overlay active"))
        createOverlay()
        Logger.log("OverlayService created with foreground notification")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure foreground notification
        try {
            startForeground(NOTIFICATION_ID, buildNotification("Overlay active"))
        } catch (_: Exception) {}
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        isRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Overlay display service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HackerLauncher Overlay")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createOverlay() {
        if (overlayView != null) return

        overlayView = TextView(this).apply {
            text = overlayText
            setTextColor(0xFF00FF00.toInt())
            setTextSize(10f)
            setBackgroundColor(0x80000000.toInt())
            setPadding(8, 4, 8, 4)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 10
            y = 10
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Logger.log("Overlay add failed: ${e.message}")
        }

        // Update thread
        Thread {
            while (isRunning) {
                try {
                    Thread.sleep(1000)
                    if (isVisible && overlayView != null) {
                        overlayView?.post {
                            overlayView?.text = overlayText
                            overlayView?.visibility = View.VISIBLE
                        }
                    } else if (!isVisible && overlayView != null) {
                        overlayView?.post {
                            overlayView?.visibility = View.GONE
                        }
                    }
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.start()
    }

    private fun removeOverlay() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Logger.log("Overlay remove failed: ${e.message}")
        }
        overlayView = null
    }
}
