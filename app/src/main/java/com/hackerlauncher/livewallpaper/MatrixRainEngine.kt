package com.hackerlauncher.livewallpaper

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color

class MatrixRainEngine {
    private var width = 0
    private var height = 0
    private val chars = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ@#$%^&*(){}[]|;:<>,.?/~`"
    private val columns = mutableListOf<Column>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        color = Color.GREEN
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        color = Color.WHITE
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private data class Column(
        var x: Float,
        var y: Float,
        var speed: Float,
        val length: Int,
        val trail: MutableList<Char>
    )

    fun init(w: Int, h: Int, prefs: SharedPreferences) {
        width = w
        height = h
        columns.clear()

        val densityLevel = prefs.getInt(HackerWallpaperService.KEY_DENSITY, 1)
        val colSpacing = when (densityLevel) {
            0 -> 20f; 1 -> 14f; else -> 10f
        }
        val colCount = (w / colSpacing).toInt()

        for (i in 0 until colCount) {
            val length = (8..30).random()
            val trail = mutableListOf<Char>()
            repeat(length) { trail.add(chars.random()) }
            columns.add(Column(
                x = i * colSpacing,
                y = (-Math.random() * h).toFloat(),
                speed = (2f + Math.random() * 6).toFloat(),
                length = length,
                trail = trail
            ))
        }
    }

    fun draw(canvas: Canvas) {
        for (col in columns) {
            for ((idx, char) in col.trail.withIndex()) {
                val alpha = when {
                    idx == col.trail.lastIndex -> 255
                    idx > col.trail.size - 5 -> 200
                    else -> (60 + idx * 5).coerceAtMost(180)
                }
                if (idx == col.trail.lastIndex) {
                    headPaint.alpha = 255
                    canvas.drawText(char.toString(), col.x, col.y + idx * 16f, headPaint)
                } else {
                    paint.alpha = alpha
                    canvas.drawText(char.toString(), col.x, col.y + idx * 16f, paint)
                }
            }
            col.y += col.speed
            // Randomly change a character
            if (Math.random() < 0.05) {
                val randIdx = (0 until col.trail.size).random()
                col.trail[randIdx] = chars.random()
            }
            // Shift trail
            col.trail.removeAt(0)
            col.trail.add(chars.random())

            if (col.y > height + col.length * 16) {
                col.y = -(col.length * 16).toFloat()
                col.speed = (2f + Math.random() * 6).toFloat()
            }
        }
    }
}
