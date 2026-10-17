package com.pulseplayer.app.aipulse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.max

/**
 * Readable, scroll-free bar timeline for the editable arrangement.
 * Drawing is a lightweight overview; editing remains in the accessible dialog.
 */
class AiPulseTimelineView(context: Context) : View(context) {
    var plan: AiPulseBrain.Plan? = null
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sectionColors = mapOf(
        AiPulseBrain.Section.INTRO to 0xFF5477A5.toInt(),
        AiPulseBrain.Section.VERSE to 0xFF238B8D.toInt(),
        AiPulseBrain.Section.CHORUS to 0xFFC27A2E.toInt(),
        AiPulseBrain.Section.BRIDGE to 0xFF7F65B8.toInt(),
        AiPulseBrain.Section.OUTRO to 0xFF687988.toInt()
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF171D2B.toInt())
        val current = plan
        brush.color = Color.WHITE
        brush.textSize = 13f * density
        if (current == null) {
            canvas.drawText("Generate or load an arrangement to view its timeline",
                12f * density, 28f * density, brush)
            return
        }
        val pad = 10f * density
        val usableWidth = max(1f, width - 2f * pad)
        val y = 32f * density
        val heightPx = 72f * density
        brush.color = 0xFFE1E8F2.toInt()
        canvas.drawText("${current.totalBars} bars · ${current.bpm} BPM", pad, 20f * density, brush)
        for (part in current.segments) {
            val left = pad + usableWidth * part.startBar / current.totalBars
            val right = pad + usableWidth * (part.startBar + part.bars) / current.totalBars
            brush.color = sectionColors[part.section] ?: Color.GRAY
            canvas.drawRoundRect(left + 1f, y, right - 1f, y + heightPx, 5f * density, 5f * density, brush)
            val strength = (part.energy * heightPx * 0.45).toFloat()
            brush.color = 0x88FFFFFF.toInt()
            canvas.drawRect(left + 3f, y + heightPx - strength - 4f,
                max(left + 3f, right - 3f), y + heightPx - 4f, brush)
            if (right - left > 43f * density) {
                brush.color = Color.WHITE
                brush.textSize = 11f * density
                canvas.drawText(part.section.name.take(6), left + 4f * density,
                    y + 18f * density, brush)
                canvas.drawText("${part.startBar + 1}–${part.startBar + part.bars}",
                    left + 4f * density, y + 35f * density, brush)
            }
        }
    }
}
