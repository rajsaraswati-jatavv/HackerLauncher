package com.hackerlauncher.livewallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder

class HackerWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "HackerWallpaper"
        const val PREFS_NAME = "hacker_wallpaper_prefs"
        const val KEY_MODE = "wallpaper_mode"
        const val KEY_COLOR = "wallpaper_color"
        const val KEY_SPEED = "wallpaper_speed"
        const val KEY_DENSITY = "wallpaper_density"

        const val MODE_MATRIX_RAIN = 0
        const val MODE_GLITCH = 1
        const val MODE_CRT_SCANLINES = 2
        const val MODE_PARTICLE_NETWORK = 3
        const val MODE_HEX_FALL = 4
    }

    override fun onCreateEngine(): Engine {
        return HackerEngine()
    }

    inner class HackerEngine : Engine() {
        private var drawingThread: DrawingThread? = null
        private var isVisible = true
        private var screenOn = true
        private val prefs: SharedPreferences by lazy {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        stopDrawing()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        screenOn = true
                        if (isVisible) startDrawing()
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            // FIX: Use RECEIVER_NOT_EXPORTED for screen on/off (system broadcasts)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
            Log.d(TAG, "HackerEngine created")
        }

        override fun onDestroy() {
            super.onDestroy()
            stopDrawing()
            try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible && screenOn) startDrawing() else stopDrawing()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawingThread?.updateDimensions(width, height)
        }

        private fun startDrawing() {
            if (drawingThread != null && drawingThread!!.isRunning) return
            drawingThread = DrawingThread(surfaceHolder, prefs).also { it.start() }
        }

        private fun stopDrawing() {
            drawingThread?.requestStop()
            drawingThread = null
        }
    }

    private class DrawingThread(
        private val surfaceHolder: SurfaceHolder,
        private val prefs: SharedPreferences
    ) : Thread("HackerWallpaper-Draw") {

        @Volatile
        var isRunning = false
            private set

        private var width = 0
        private var height = 0
        private var mode = MODE_MATRIX_RAIN
        private var fps = 30
        private val frameInterval: Long get() = 1000L / fps

        private val matrixRain = MatrixRainEngine()
        private val glitchEffect = GlitchEffect()
        private val particleNetwork = ParticleNetwork()

        fun updateDimensions(w: Int, h: Int) {
            width = w
            height = h
            matrixRain.init(w, h, prefs)
            glitchEffect.init(w, h, prefs)
            particleNetwork.init(w, h, prefs)
        }

        fun requestStop() {
            isRunning = false
        }

        override fun run() {
            isRunning = true
            while (isRunning) {
                val startTime = System.currentTimeMillis()
                mode = prefs.getInt(KEY_MODE, MODE_MATRIX_RAIN)
                fps = when (prefs.getInt(KEY_SPEED, 1)) {
                    0 -> 24; 1 -> 30; 2 -> 45; else -> 60
                }

                var canvas: Canvas? = null
                try {
                    canvas = surfaceHolder.lockCanvas()
                    if (canvas != null && width > 0 && height > 0) {
                        synchronized(surfaceHolder) {
                            drawFrame(canvas)
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    canvas?.let { surfaceHolder.unlockCanvasAndPost(it) }
                }

                val elapsed = System.currentTimeMillis() - startTime
                val sleepTime = frameInterval - elapsed
                if (sleepTime > 0) {
                    try { sleep(sleepTime) } catch (_: InterruptedException) {}
                }
            }
        }

        private fun drawFrame(canvas: Canvas) {
            canvas.drawColor(0xFF000000.toInt()) // Black background
            when (mode) {
                MODE_MATRIX_RAIN -> matrixRain.draw(canvas)
                MODE_GLITCH -> glitchEffect.draw(canvas)
                MODE_CRT_SCANLINES -> {
                    matrixRain.draw(canvas)
                    drawScanlines(canvas)
                }
                MODE_PARTICLE_NETWORK -> particleNetwork.draw(canvas)
                MODE_HEX_FALL -> drawHexFall(canvas)
            }
        }

        private fun drawScanlines(canvas: Canvas) {
            val paint = Paint().apply {
                color = 0x18000000
                strokeWidth = 1f
            }
            var y = 0f
            while (y < height) {
                canvas.drawLine(0f, y, width.toFloat(), y, paint)
                y += 3f
            }
            // CRT vignette
            val vignette = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            val cx = width / 2f
            val cy = height / 2f
            val radius = Math.max(cx, cy) * 1.2f
            val shader = android.graphics.RadialGradient(
                cx, cy, radius,
                0x00000000, 0x80000000,
                android.graphics.Shader.TileMode.CLAMP
            )
            vignette.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignette)
        }

        private val hexChars = "0123456789ABCDEF"
        private val hexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            color = 0xFF00FF00.toInt()
            alpha = 200
        }
        private val hexColumns = mutableListOf<HexColumn>()

        private data class HexColumn(var x: Float, var y: Float, val speed: Float, val chars: List<String>)

        private fun drawHexFall(canvas: Canvas) {
            if (hexColumns.isEmpty()) {
                val density = prefs.getInt(KEY_DENSITY, 2)
                val colCount = when (density) { 0 -> 12; 1 -> 20; else -> 35 }
                for (i in 0 until colCount) {
                    val x = (width.toFloat() / colCount) * i + 5f
                    hexColumns.add(HexColumn(x, (-Math.random() * height).toFloat(), (2f + Math.random() * 4).toFloat(),
                        (1..20).map { "0x${hexChars.random()}${hexChars.random()}" }))
                }
            }
            val colorStr = prefs.getString(KEY_COLOR, "#00FF00")
            hexPaint.color = android.graphics.Color.parseColor(colorStr)
            hexPaint.alpha = 200
            for (col in hexColumns) {
                for ((idx, char) in col.chars.withIndex()) {
                    val alpha = if (idx == col.chars.lastIndex) 255 else (100 + idx * 8).coerceAtMost(220)
                    hexPaint.alpha = alpha
                    canvas.drawText(char, col.x, col.y + idx * 18f, hexPaint)
                }
                col.y += col.speed
                if (col.y > height + col.chars.size * 18) {
                    col.y = -col.chars.size * 18f
                }
            }
        }
    }
}
