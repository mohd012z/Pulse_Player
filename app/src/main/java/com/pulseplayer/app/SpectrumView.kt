package com.pulseplayer.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.view.View
import kotlin.math.hypot
import kotlin.math.max

/** Actual per-session FFT visualization. Does not store PCM/record audio. */
class SpectrumView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(117, 226, 192) }
    private var visualizer: Visualizer? = null
    private val energy = FloatArray(32)
    private var fft = ByteArray(0)
    var enabledByUser: Boolean = false
        private set

    fun bind(audioSessionId: Int): Boolean {
        release()
        if (audioSessionId <= 0) return false
        return runCatching {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange().last()
                fft = ByteArray(captureSize)
                enabled = true
            }
            enabledByUser = true
            true
        }.getOrElse { release(); false }
    }

    fun tick() {
        val v = visualizer ?: return
        if (runCatching { v.getFft(fft) }.getOrDefault(Visualizer.ERROR) != Visualizer.SUCCESS) return
        for (i in energy.indices) {
            val bin = 1 + (i * (fft.size / 2 - 1) / energy.size)
            val real = fft[2 * bin].toInt().toDouble()
            val imaginary = fft[2 * bin + 1].toInt().toDouble()
            val magnitude = (hypot(real, imaginary) / 128.0).toFloat().coerceIn(0f, 1f)
            energy[i] = max(magnitude, energy[i] * 0.75f)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = width.toFloat() / energy.size
        for (i in energy.indices) {
            val barHeight = height * energy[i].coerceAtLeast(0.03f)
            canvas.drawRoundRect(i * barWidth + 1f, height - barHeight, (i + 1) * barWidth - 1f,
                height.toFloat(), 2f, 2f, paint)
        }
    }

    fun release() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        enabledByUser = false
        energy.fill(0f)
        invalidate()
    }
}