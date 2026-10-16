package com.pulseplayer.app.aipulse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Lightweight preview-only visuals; no audio capture or misleading FFT claims. */
class AiPulseVisualView(context: Context) : View(context) {
    enum class Preset { BARS, WAVE, ORBIT, MINIMAL }
    var preset = Preset.BARS
        set(value) { field = value; invalidate() }
    var motionEnabled = true
        set(value) { field = value; invalidate() }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 5f }
    private val colors = intArrayOf(0xFF75E2C0.toInt(), 0xFF66B6FF.toInt(), 0xFFFF78C6.toInt())
    private var frame = 0L
    override fun onAttachedToWindow() { super.onAttachedToWindow(); invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF151C2A.toInt())
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val phase = if (motionEnabled) frame * 0.06 else 0.0
        when (preset) {
            Preset.BARS -> {
                val count = 30
                val gap = w / count
                for (i in 0 until count) {
                    val strength = 0.12 + 0.72 * abs(sin(phase + i * 0.35) * cos(i * 0.17 + phase * 0.3))
                    val bh = (h * strength).toFloat()
                    pen.color = colors[i % colors.size]
                    pen.style = Paint.Style.FILL
                    canvas.drawRoundRect(i * gap + 2f, h - bh, (i + 1) * gap - 2f, h, 5f, 5f, pen)
                }
            }
            Preset.WAVE -> {
                pen.style = Paint.Style.STROKE
                for (line in 0..2) {
                    pen.color = colors[line]
                    var previousX = 0f
                    var previousY = h / 2
                    for (i in 0..160) {
                        val x = w * i / 160f
                        val y = (h / 2 + sin(i * 0.12 + phase + line) * h * (0.12 + line * 0.035)).toFloat()
                        if (i > 0) canvas.drawLine(previousX, previousY, x, y, pen)
                        previousX = x
                        previousY = y
                    }
                }
            }
            Preset.ORBIT -> {
                pen.style = Paint.Style.STROKE
                val radius = min(w, h) * 0.27f
                for (i in 0..3) {
                    pen.color = colors[i % colors.size]
                    val r = radius * (0.65f + i * 0.15f)
                    canvas.drawCircle(w / 2, h / 2, r, pen)
                }
                pen.style = Paint.Style.FILL
                for (i in 0..11) {
                    val angle = phase + i * 2 * PI / 12
                    pen.color = colors[i % colors.size]
                    canvas.drawCircle((w / 2 + radius * cos(angle)).toFloat(),
                        (h / 2 + radius * sin(angle)).toFloat(), 5f, pen)
                }
            }
            Preset.MINIMAL -> {
                pen.color = colors[0]
                pen.style = Paint.Style.STROKE
                canvas.drawCircle(w / 2, h / 2, min(w, h) * 0.2f, pen)
                pen.style = Paint.Style.FILL
                canvas.drawCircle(w / 2, h / 2, 7f, pen)
            }
        }
        if (motionEnabled && isAttachedToWindow && visibility == VISIBLE) {
            frame++
            postInvalidateDelayed(33L)
        }
    }
}
