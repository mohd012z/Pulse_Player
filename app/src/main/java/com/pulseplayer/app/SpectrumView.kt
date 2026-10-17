package com.pulseplayer.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.util.Log
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

/** Foreground-only, opt-in FFT capture from the active playback session. */
class SpectrumView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF75E2C0.toInt() }
    private val levels = FloatArray(32)
    enum class Mode { BARS, WAVE, ORBIT }
    var mode: Mode = Mode.BARS
        set(value) { field = value; invalidate() }
    private val peaks = FloatArray(32)
    private val colors = intArrayOf(0xFF75E2C0.toInt(), 0xFF66B6FF.toInt(), 0xFFFF78C6.toInt())
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
                        for (j in levels.indices) {
                            levels[j] = if (next[j] > levels[j]) next[j]
                                else levels[j] * 0.77f + next[j] * 0.23f
                            peaks[j] = max(peaks[j] * 0.93f, levels[j])
                        }
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
        peaks.fill(0f)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawColor(0xFF10141F.toInt())
        val n = levels.size
        when (mode) {
            Mode.BARS -> {
                val pitch = w / n
                val gap = max(1f, pitch * 0.14f)
                for (j in 0 until n) {
                    val x = j * pitch + gap
                    val right = max(x + 1f, (j + 1) * pitch - gap)
                    val height = h * levels[j].coerceIn(0f, 1f)
                    paint.style = Paint.Style.FILL
                    paint.color = colors[j % colors.size]
                    canvas.drawRoundRect(x, h - height, right, h, gap, gap, paint)
                    paint.color = Color.WHITE
                    val peakY = h - h * peaks[j].coerceIn(0f, 1f)
                    canvas.drawRect(x, peakY, right, peakY + 2f, paint)
                }
            }
            Mode.WAVE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(2f, w / 190f)
                for (layer in 0..2) {
                    paint.color = colors[layer]
                    var lastX = 0f
                    var lastY = h / 2f
                    for (j in 0..(n * 4)) {
                        val fraction = j / (n * 4f)
                        val band = min(n - 1, (fraction * n).toInt())
                        val envelope = levels[band] * h * (0.4f - layer * 0.05f)
                        val x = fraction * w
                        val y = h / 2f + sin(fraction * (6 + layer * 2) * PI).toFloat() * envelope
                        if (j > 0) canvas.drawLine(lastX, lastY, x, y, paint)
                        lastX = x
                        lastY = y
                    }
                }
            }
            Mode.ORBIT -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(2f, w / 200f)
                val base = min(w, h) * 0.20f
                for (j in 0 until n) {
                    val theta = 2.0 * PI * j / n
                    val outer = base + levels[j] * min(w, h) * 0.27f
                    paint.color = colors[j % colors.size]
                    canvas.drawLine(w / 2f + cos(theta).toFloat() * base,
                        h / 2f + sin(theta).toFloat() * base,
                        w / 2f + cos(theta).toFloat() * outer,
                        h / 2f + sin(theta).toFloat() * outer, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
    }
}
