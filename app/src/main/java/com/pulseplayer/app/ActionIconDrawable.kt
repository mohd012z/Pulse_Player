package com.pulseplayer.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Lightweight resolution-independent action icons; no emoji font dependency. */
class ActionIconDrawable(private val kind: String, private val sizePx: Int) : Drawable() {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx
    override fun draw(canvas: Canvas) {
        val b = bounds
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / 24f, b.height() / 24f)
        when (kind) {
            "radio" -> {
                canvas.drawRoundRect(3f, 8f, 21f, 20f, 2f, 2f, pen)
                canvas.drawLine(5f, 8f, 18f, 3f, pen)
                canvas.drawCircle(16.5f, 14f, 2.5f, pen)
                canvas.drawLine(6f, 13f, 11f, 13f, pen)
                canvas.drawLine(6f, 16f, 10f, 16f, pen)
            }
            "scan" -> {
                canvas.drawArc(3f, 5f, 21f, 23f, 210f, 120f, false, pen)
                canvas.drawArc(7f, 9f, 17f, 19f, 210f, 120f, false, pen)
                canvas.drawCircle(12f, 16f, 1.6f, pen)
            }
            "fx" -> {
                for (x in listOf(6f, 12f, 18f)) canvas.drawLine(x, 4f, x, 20f, pen)
                canvas.drawCircle(6f, 9f, 2.4f, pen)
                canvas.drawCircle(12f, 15f, 2.4f, pen)
                canvas.drawCircle(18f, 10f, 2.4f, pen)
            }
            "settings" -> {
                canvas.drawCircle(12f, 12f, 7.5f, pen)
                canvas.drawCircle(12f, 12f, 3f, pen)
                for (i in 0..7) {
                    val a = i * Math.PI / 4
                    val dx = kotlin.math.cos(a).toFloat()
                    val dy = kotlin.math.sin(a).toFloat()
                    canvas.drawLine(12f + 8f * dx, 12f + 8f * dy, 12f + 10f * dx, 12f + 10f * dy, pen)
                }
            }
            "wave" -> {
                for (i in 0..20) {
                    val x = 2f + i
                    val y = 12f - kotlin.math.sin(i * 0.65f) * 4f
                    canvas.drawCircle(x, y, 0.8f, pen)
                }
            }
            "captions" -> {
                canvas.drawRoundRect(2f, 5f, 22f, 19f, 3f, 3f, pen)
                canvas.drawLine(6f, 12f, 11f, 12f, pen)
                canvas.drawLine(13f, 12f, 18f, 12f, pen)
                canvas.drawLine(6f, 15f, 18f, 15f, pen)
            }
            "library" -> {
                canvas.drawRoundRect(3f, 5f, 20f, 20f, 2f, 2f, pen)
                canvas.drawLine(8f, 9f, 16f, 9f, pen)
                canvas.drawLine(8f, 13f, 16f, 13f, pen)
                canvas.drawLine(8f, 17f, 14f, 17f, pen)
            }
            "device" -> {
                canvas.drawRoundRect(5f, 3f, 19f, 21f, 2f, 2f, pen)
                canvas.drawLine(10f, 17f, 14f, 17f, pen)
            }
            else -> {
                canvas.drawCircle(12f, 12f, 8f, pen)
                canvas.drawLine(9f, 8f, 9f, 16f, pen)
                canvas.drawLine(13f, 6f, 13f, 18f, pen)
                canvas.drawLine(17f, 10f, 17f, 14f, pen)
            }
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { pen.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { pen.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
