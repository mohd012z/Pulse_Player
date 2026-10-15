package com.pulseplayer.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
    private val path = Path()
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
        canvas.drawLine(0f, center, width.toFloat(), center, baseline)
        val amplitude = height * 0.43f
        path.reset()
        wave.forEachIndexed { index, sample ->
            val x = index * (width - 1f) / (wave.size - 1)
            val y = center - sample.coerceIn(-1f, 1f) * amplitude
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, line)
    }
}
