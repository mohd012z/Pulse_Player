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
    private val profile get() = DevicePerformanceProfile.selected(context)
    private val layerRadius get() = when (profile) {
        DevicePerformanceProfile.Tier.LITE -> 3
        DevicePerformanceProfile.Tier.BALANCED -> 6
        DevicePerformanceProfile.Tier.HIGH -> 10
    }
    private var lastFrameMs = 0L

    fun start(sessionId: Int): Boolean {
        stop()
        if (sessionId <= 0) return false
        return try {
            val visualizer = Visualizer(sessionId)
            visualizer.captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(256)
            val targetFps = DevicePerformanceProfile.recommendedSpectrumFps(context)
            visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {
                    if (data == null || data.isEmpty()) return
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastFrameMs < 1000L / targetFps) return
                    lastFrameMs = now
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
            }, minOf(targetFps * 1000, Visualizer.getMaxCaptureRate()), true, false)
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
        lastFrameMs = 0L
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
        val pixel = (resources.displayMetrics.density * 1.15f).coerceAtLeast(1f)
        val spacing = pixel * 1.85f
        val stride = when (profile) {
            DevicePerformanceProfile.Tier.LITE -> 3
            DevicePerformanceProfile.Tier.BALANCED -> 2
            DevicePerformanceProfile.Tier.HIGH -> 1
        }
        val columns = (width / (spacing * stride)).toInt().coerceAtLeast(1)
        val layerCount = layerRadius
        val amplitude = height * 0.31f
        line.style = Paint.Style.FILL
        // A layered sine-wave cloud. Actual waveform energy controls its shape and thickness.
        // Multiple phase-shifted ribbons make quiet music visible without fake FFT bars.
        for (column in 0 until columns) {
            val t = column.toFloat() / columns
            val sampleIndex = (column.toLong() * wave.size / columns).toInt().coerceIn(0, wave.lastIndex)
            val signal = wave[sampleIndex].coerceIn(-1f, 1f)
            val energy = kotlin.math.abs(signal)
            val envelope = 0.35f + 0.65f * kotlin.math.sin(Math.PI.toFloat() * t).let { it * it }
            val swing = (0.24f + energy * 0.76f) * envelope
            val phase = t * 19f
            val mainY = center - amplitude * (signal * 0.45f + kotlin.math.sin(phase) * swing * 0.75f)
            val spread = height * (0.09f + energy * 0.17f) * envelope
            for (layer in -layerCount..layerCount) {
                val distance = kotlin.math.abs(layer).toFloat() / layerCount
                val ripple = kotlin.math.sin(phase * 1.35f + layer * 0.41f) * amplitude * 0.13f * swing
                val jitter = kotlin.math.sin(column * 1.71f + layer * 2.37f) * pixel * 0.8f
                val y = mainY + layer * spread / 10f + ripple + jitter
                if (y < pixel || y > height - pixel) continue
                val opacity = (0.93f - distance * 0.75f).coerceIn(0.1f, 1f)
                val hue = (172f + t * 155f + layer * 1.5f) % 360f
                line.color = Color.HSVToColor((opacity * 255f).toInt(),
                    floatArrayOf(hue, 0.74f, 0.98f))
                val size = pixel * (0.55f + opacity * 0.25f)
                canvas.drawRect(column * spacing * stride, y, column * spacing * stride + size, y + size, line)
            }
        }
    }
}
