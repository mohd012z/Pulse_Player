package com.pulseplayer.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.util.Log
import android.view.View

/** Pixel-sharp, centered AC-style waveform from the active audio session. */
class SpectrumView(context: Context) : View(context) {
    private val wave = FloatArray(256)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF75E2C0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.5f
        strokeJoin = Paint.Join.ROUND
    }
    private val baseline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4475E2C0
        strokeWidth = resources.displayMetrics.density
    }
    private val pixelSize = resources.displayMetrics.density * 1.25f
    private var effect: Visualizer? = null

    fun start(sessionId: Int): Boolean {
        stop()
        if (sessionId <= 0) return false
        return try {
            val visualizer = Visualizer(sessionId)
            visualizer.captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(256)
            visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {
                    if (data == null || data.isEmpty()) return
                    val next = FloatArray(wave.size)
                    for (i in next.indices) {
                        val index = (i.toLong() * data.size / next.size).toInt().coerceIn(0, data.lastIndex)
                        next[i] = ((data[index].toInt() and 0xFF) - 128) / 128f
                    }
                    post {
                        if (effect !== v) return@post
                        for (i in next.indices) wave[i] = wave[i] * 0.2f + next[i] * 0.8f
                        invalidate()
                    }
                }
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) = Unit
            }, minOf(12_000, Visualizer.getMaxCaptureRate()), true, false)
            effect = visualizer
            visualizer.enabled = true
            true
        } catch (ex: Exception) {
            Log.w("PulseSpectrum", "Waveform capture unavailable", ex)
            stop()
            false
        }
    }

    fun stop() {
        val previous = effect
        effect = null
        runCatching { previous?.enabled = false }
        runCatching { previous?.release() }
        wave.fill(0f)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val center = height / 2f
        val pixel = pixelSize.coerceAtLeast(1f)
        val spacing = pixel * 1.9f
        val columns = (width / spacing).toInt().coerceAtLeast(1)
        val amplitude = height * 0.39f
        line.style = Paint.Style.FILL
        // A soft cloud of independent fine pixels around the captured waveform.
        // Deterministic variation avoids random flicker between frames.
        for (column in 0 until columns) {
            val sampleIndex = (column.toLong() * wave.size / columns).toInt().coerceIn(0, wave.lastIndex)
            val sample = wave[sampleIndex].coerceIn(-1f, 1f)
            val x = column * spacing
            val waveY = center - sample * amplitude
            val spread = (height * (0.035f + 0.14f * kotlin.math.abs(sample))).coerceAtLeast(pixel * 2f)
            for (layer in -4..4) {
                val distance = kotlin.math.abs(layer) / 4f
                val offset = layer * spread / 4f
                val jitter = kotlin.math.sin(column * 1.71f + layer * 2.37f) * pixel * 0.55f
                val y = waveY + offset + jitter
                if (y < 0f || y >= height) continue
                val brightness = (1f - distance * 0.68f).coerceIn(0f, 1f)
                val hue = (178f + 140f * column / columns + layer * 2f) % 360f
                line.color = Color.HSVToColor((brightness * 255).toInt(), floatArrayOf(hue, 0.72f, 0.98f))
                val size = pixel * (0.45f + brightness * 0.25f)
                canvas.drawRect(x, y, x + size, y + size, line)
            }
        }
    }
}
