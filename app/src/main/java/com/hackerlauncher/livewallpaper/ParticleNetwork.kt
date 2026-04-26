package com.hackerlauncher.livewallpaper

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import kotlin.math.sqrt
import kotlin.random.Random

class ParticleNetwork {
    private var width = 0
    private var height = 0
    private var lineColor = Color.GREEN
    private var particleColor = Color.GREEN
    private val particles = mutableListOf<Particle>()
    private val connectionDistance = 120f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val radius: Float
    )

    fun init(w: Int, h: Int, prefs: SharedPreferences) {
        width = w
        height = h
        val colorStr = prefs.getString(HackerWallpaperService.KEY_COLOR, "#00FF00")
        lineColor = Color.parseColor(colorStr)
        particleColor = Color.parseColor(colorStr)
        particles.clear()

        val densityLevel = prefs.getInt(HackerWallpaperService.KEY_DENSITY, 1)
        val count = when (densityLevel) {
            0 -> 30; 1 -> 60; else -> 100
        }
        repeat(count) {
            particles.add(Particle(
                x = Random.nextFloat() * w,
                y = Random.nextFloat() * h,
                vx = (Random.nextFloat() - 0.5f) * 2f,
                vy = (Random.nextFloat() - 0.5f) * 2f,
                radius = Random.nextFloat() * 2f + 1f
            ))
        }
    }

    fun draw(canvas: Canvas) {
        // Update positions
        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            if (p.x < 0 || p.x > width) p.vx *= -1
            if (p.y < 0 || p.y > height) p.vy *= -1
            p.x = p.x.coerceIn(0f, width.toFloat())
            p.y = p.y.coerceIn(0f, height.toFloat())
        }

        // Draw connections
        linePaint.color = lineColor
        linePaint.strokeWidth = 0.5f
        for (i in particles.indices) {
            for (j in i + 1 until particles.size) {
                val dx = particles[i].x - particles[j].x
                val dy = particles[i].y - particles[j].y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < connectionDistance) {
                    val alpha = ((1f - dist / connectionDistance) * 80).toInt()
                    linePaint.alpha = alpha
                    canvas.drawLine(
                        particles[i].x, particles[i].y,
                        particles[j].x, particles[j].y,
                        linePaint
                    )
                }
            }
        }

        // Draw particles
        paint.color = particleColor
        for (p in particles) {
            paint.alpha = 180
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }

        // Draw center node glow
        if (particles.isNotEmpty()) {
            val center = particles[particles.size / 2]
            paint.alpha = 40
            canvas.drawCircle(center.x, center.y, 20f, paint)
            paint.alpha = 20
            canvas.drawCircle(center.x, center.y, 40f, paint)
        }
    }
}
