package com.hackerlauncher.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.hackerlauncher.utils.Logger

class OverlayService : Service() {

    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null
    private val logger = Logger

    companion object {
        @Volatile
        var isRunning = false
            private set

        // FIX: Make overlayText and isVisible @Volatile for thread safety
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
        createOverlay()
        Logger.log("OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        isRunning = false
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
                        }
                    } else if (!isVisible && overlayView != null) {
                        overlayView?.post {
                            overlayView?.visibility = if (isVisible) View.VISIBLE else View.GONE
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
