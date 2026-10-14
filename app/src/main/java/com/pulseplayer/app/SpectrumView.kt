package com.pulseplayer.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.util.Log
import android.view.View
import kotlin.math.hypot

/** Foreground-only, opt-in FFT capture from the active playback session. */
class SpectrumView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF75E2C0.toInt() }
    private val levels = FloatArray(32)
    private var effect: Visualizer? = null

    fun start(sessionId: Int): Boolean {
        stop()
        if (sessionId <= 0) return false
        return try {
            val visualizer = Visualizer(sessionId)
            visualizer.captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(128)
            visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) = Unit
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                    if (fft == null || fft.size < 64) return
                    val next = FloatArray(levels.size)
                    for (i in next.indices) {
                        val bin = 1 + (i.toDouble() / next.size * (fft.size / 2 - 2)).toInt()
                        val re = fft[bin * 2].toInt().toDouble()
                        val im = fft[bin * 2 + 1].toInt().toDouble()
                        next[i] = (hypot(re, im) / 110.0).toFloat().coerceIn(0.025f, 1f)
                    }
                    post {
                        next.copyInto(levels)
                        invalidate()
                    }
                }
            }, minOf(12_000, Visualizer.getMaxCaptureRate()), false, true)
            effect = visualizer
            visualizer.enabled = true
            true
        } catch (ex: Exception) {
            Log.w("PulseSpectrum", "FFT visualizer is unavailable", ex)
            stop()
            false
        }
    }

    fun stop() {
        val old = effect
        effect = null
        runCatching { old?.enabled = false }
        runCatching { old?.release() }
        levels.fill(0f)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = width / (levels.size * 1.45f)
        val barWidth = (width - gap * (levels.size - 1)) / levels.size
        levels.forEachIndexed { idx, value ->
            val barHeight = (height * value.coerceAtLeast(0.03f)).coerceAtMost(height.toFloat())
            val left = idx * (barWidth + gap)
            canvas.drawRoundRect(left, height - barHeight, left + barWidth, height.toFloat(), barWidth / 3, barWidth / 3, paint)
        }
    }
}