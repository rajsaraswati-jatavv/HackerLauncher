package com.hackerlauncher.livewallpaper

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Bitmap
import kotlin.random.Random

class GlitchEffect {
    private var width = 0
    private var height = 0
    private var baseColor = Color.GREEN
    private val glitchLines = mutableListOf<GlitchLine>()
    private var frameCount = 0
    private var glitchIntensity = 0.5f

    private data class GlitchLine(
        val y: Int,
        val height: Int,
        val offset: Int,
        val color: Int,
        val lifetime: Int,
        var age: Int = 0
    )

    fun init(w: Int, h: Int, prefs: SharedPreferences) {
        width = w
        height = h
        val colorStr = prefs.getString(HackerWallpaperService.KEY_COLOR, "#00FF00")
        baseColor = Color.parseColor(colorStr)
        glitchIntensity = when (prefs.getInt(HackerWallpaperService.KEY_DENSITY, 1)) {
            0 -> 0.3f; 1 -> 0.5f; else -> 0.8f
        }
        glitchLines.clear()
    }

    fun draw(canvas: Canvas) {
        frameCount++
        val paint = Paint()

        // Background grid
        paint.color = baseColor
        paint.alpha = 20
        for (x in 0 until width step 40) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
        }
        for (y in 0 until height step 40) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
        }

        // Random noise blocks
        if (Random.nextFloat() < glitchIntensity) {
            val blockCount = (1..5).random()
            repeat(blockCount) {
                val bx = Random.nextInt(width)
                val by = Random.nextInt(height)
                val bw = Random.nextInt(50, 200)
                val bh = Random.nextInt(2, 20)
                paint.color = baseColor
                paint.alpha = Random.nextInt(30, 120)
                canvas.drawRect(bx.toFloat(), by.toFloat(), (bx + bw).toFloat(), (by + bh).toFloat(), paint)
            }
        }

        // Horizontal glitch lines
        if (Random.nextFloat() < glitchIntensity * 0.4f) {
            val lineY = Random.nextInt(height)
            val lineHeight = Random.nextInt(1, 8)
            val offset = Random.nextInt(-50, 50)
            glitchLines.add(GlitchLine(lineY, lineHeight, offset, baseColor, Random.nextInt(3, 15)))
        }

        // Draw and update glitch lines
        val iterator = glitchLines.iterator()
        while (iterator.hasNext()) {
            val line = iterator.next()
            paint.color = line.color
            paint.alpha = Random.nextInt(80, 200)
            canvas.drawRect(
                Math.max(0, line.offset).toFloat(),
                line.y.toFloat(),
                (width + Math.min(0, line.offset)).toFloat(),
                (line.y + line.height).toFloat(),
                paint
            )
            line.age++
            if (line.age >= line.lifetime) iterator.remove()
        }

        // Screen tear effect
        if (Random.nextFloat() < glitchIntensity * 0.15f) {
            val tearY = Random.nextInt(height)
            val tearH = Random.nextInt(10, 60)
            try {
                val bitmap = Bitmap.createBitmap(width, tearH, Bitmap.Config.ARGB_8888)
                val tearCanvas = Canvas(bitmap)
                val shift = Random.nextInt(-30, 30)
                tearCanvas.translate(shift.toFloat(), 0f)
                // Draw displaced section
                paint.color = baseColor
                paint.alpha = 40
                tearCanvas.drawRect(0f, 0f, width.toFloat(), tearH.toFloat(), paint)
                canvas.drawBitmap(bitmap, 0f, tearY.toFloat(), null)
                bitmap.recycle()
            } catch (_: Exception) {}
        }

        // Scanline overlay
        paint.color = Color.BLACK
        paint.alpha = 30
        for (y in 0 until height step 2) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
        }

        // Random text fragments
        if (Random.nextFloat() < glitchIntensity * 0.2f) {
            paint.color = baseColor
            paint.alpha = Random.nextInt(40, 100)
            paint.textSize = Random.nextInt(8, 16).toFloat()
            paint.typeface = android.graphics.Typeface.MONOSPACE
            val text = generateGlitchText()
            canvas.drawText(text, Random.nextInt(width).toFloat(), Random.nextInt(height).toFloat(), paint)
        }
    }

    private fun generateGlitchText(): String {
        val fragments = listOf(
            "ERR_0x${Random.nextInt(255).toString(16).uppercase()}",
            "SEGFAULT",
            "0x${Random.nextLong().toString(16).uppercase()}",
            "NULL_PTR",
            "OVERFLOW",
            "BREACH",
            "ACCESS_DENIED",
            "STACK_SMASH",
            "CORE_DUMP",
            "PANIC"
        )
        return fragments.random()
    }
}
