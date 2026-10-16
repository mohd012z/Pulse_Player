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
    private data class Drop(var x: Float, var y: Float, val speed: Float, val energy: Float)
    private data class Ripple(val x: Float, var age: Float, val energy: Float)
    private val drops = ArrayList<Drop>()
    private val ripples = ArrayList<Ripple>()
    private val liquid = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastWaterNs = 0L

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
    private var receivedNonSilentAudio = false
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = resources.displayMetrics.scaledDensity * 10f
    }

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
                    val audible = next.any { kotlin.math.abs(it) > 0.025f }
                    post {
                        if (effect !== v) return@post
                        if (audible) receivedNonSilentAudio = true
                        for (i in next.indices) wave[i] = wave[i] * 0.2f + next[i] * 0.8f
                        updateWater()
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
        drops.clear()
        ripples.clear()
        lastWaterNs = 0L
        wave.fill(0f)
        lastFrameMs = 0L
        receivedNonSilentAudio = false
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun dpFloat(value: Float): Float = value * resources.displayMetrics.density

    private fun updateWater() {
        if (width == 0 || height == 0) return
        val now = System.nanoTime()
        val dt = if (lastWaterNs == 0L) 0.033f else ((now - lastWaterNs) / 1e9f).coerceIn(0.005f, 0.06f)
        lastWaterNs = now
        val surface = height * 0.79f
        val iter = drops.iterator()
        while (iter.hasNext()) {
            val d = iter.next()
            d.y += d.speed * dt
            if (d.y >= surface) {
                if (ripples.size < 32) ripples.add(Ripple(d.x, 0f, d.energy))
                iter.remove()
            }
        }
        val ri = ripples.iterator()
        while (ri.hasNext()) {
            val ripple = ri.next()
            ripple.age += dt
            if (ripple.age > 0.8f) ri.remove()
        }
        val maxDrops = when (profile) {
            DevicePerformanceProfile.Tier.LITE -> 12
            DevicePerformanceProfile.Tier.BALANCED -> 24
            DevicePerformanceProfile.Tier.HIGH -> 40
        }
        for (i in 0 until wave.size step 23) {
            if (drops.size >= maxDrops) break
            val energy = kotlin.math.abs(wave[i])
            val x = (i + 0.5f) / wave.size * width
            if (energy > 0.14f && drops.none { kotlin.math.abs(it.x - x) < dpFloat(8f) }) {
                drops.add(Drop(x, surface * (0.2f + 0.25f * (1f - energy)),
                    dpFloat(55f + energy * 120f), energy))
            }
        }
    }

    private fun drawWater(canvas: Canvas) {
        val surface = height * 0.79f
        liquid.style = Paint.Style.STROKE
        liquid.strokeWidth = dpFloat(0.6f)
        liquid.color = 0x664DE0CC
        canvas.drawLine(0f, surface, width.toFloat(), surface, liquid)
        for (d in drops) {
            val hue = 180f + 130f * d.x / width
            liquid.color = Color.HSVToColor(floatArrayOf(hue, 0.8f, 1f))
            liquid.alpha = 155
            canvas.drawLine(d.x, d.y - dpFloat(5f), d.x, d.y, liquid)
            liquid.style = Paint.Style.FILL
            canvas.drawCircle(d.x, d.y, dpFloat(0.7f + d.energy), liquid)
            liquid.style = Paint.Style.STROKE
        }
        for (r in ripples) {
            val hue = 180f + 130f * r.x / width
            liquid.color = Color.HSVToColor(floatArrayOf(hue, 0.8f, 1f))
            liquid.alpha = ((1f - r.age / 0.8f) * 190).toInt().coerceIn(0, 255)
            val radius = dpFloat(2f + r.age * (20f + 12f * r.energy))
            canvas.drawOval(r.x - radius, surface - radius * 0.17f,
                r.x + radius, surface + radius * 0.17f, liquid)
            if (r.age < 0.15f) {
                liquid.style = Paint.Style.FILL
                canvas.drawCircle(r.x, surface - dpFloat(3f * r.energy) * (1f - r.age / 0.15f),
                    dpFloat(0.8f), liquid)
                liquid.style = Paint.Style.STROKE
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (!receivedNonSilentAudio) {
            canvas.drawText("Waiting for audio capture · route may block visualizer", dpFloat(5f), height / 2f, hintPaint)
            return
        }
        val center = height * 0.36f
        val pixel = (resources.displayMetrics.density * 1.15f).coerceAtLeast(1f)
        val spacing = pixel * 1.85f
        val stride = when (profile) {
            DevicePerformanceProfile.Tier.LITE -> 3
            DevicePerformanceProfile.Tier.BALANCED -> 2
            DevicePerformanceProfile.Tier.HIGH -> 1
        }
        val columns = (width / (spacing * stride)).toInt().coerceAtLeast(1)
        val layerCount = layerRadius
        val amplitude = height * 0.19f
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
            val spread = height * (0.04f + energy * 0.08f) * envelope
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
        drawWater(canvas)
    }
}
