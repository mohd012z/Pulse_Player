package com.pulseplayer.app

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Linked-channel, attenuation-only PCM16 dynamics for Pulse Player media sessions.
 *
 * A gentle 2:1 compressor is followed by a sample-peak ceiling when requested.
 * No automatic makeup gain: it cannot improve source fidelity or undo clipping.
 * Not a true-peak limiter and does not control downstream Android EQ/output gain.
 *
 * This class deliberately has no Android dependencies to permit deterministic JVM tests.
 * Instances belong to a single audio stream and are never shared between sessions.
 */
internal class StereoDynamics(private val sampleRate: Int) {
    init { require(sampleRate in 8000..384000) }

    private val threshold = 10.0.pow(-18.0 / 20.0).toFloat()
    private val ceiling = 30000f / 32768f
    private val ratioExponent = 0.5 // 2:1 ratio above threshold
    private val blockFrames = 64
    private val attack = exp(-blockFrames / (0.008 * sampleRate)).toFloat()
    private val release = exp(-blockFrames / (0.170 * sampleRate)).toFloat()
    private var gain = 1f

    fun reset() { gain = 1f }

    fun process(pcm: ShortArray, sampleCount: Int = pcm.size,
                compress: Boolean, limit: Boolean) {
        require(sampleCount in 0..pcm.size && sampleCount % 2 == 0)
        if (!compress && !limit) { gain = 1f; return }
        if (!compress) gain = 1f
        var offset = 0
        while (offset < sampleCount) {
            val end = min(sampleCount, offset + blockFrames * 2)
            var peak = 0f
            if (compress) {
                for (i in offset until end) peak = max(peak, abs(pcm[i].toInt()).toFloat() / 32768f)
                val target = if (peak <= threshold) 1f
                    else (threshold / peak).toDouble().pow(ratioExponent).toFloat()
                val coefficient = if (target < gain) attack else release
                gain = (coefficient * gain + (1 - coefficient) * target).coerceIn(0f, 1f)
            }
            var i = offset
            while (i < end) {
                val left = pcm[i].toInt() * gain
                val right = pcm[i + 1].toInt() * gain
                val peakAfter = max(abs(left), abs(right))
                val limitingGain = if (limit && peakAfter > ceiling * 32768f)
                    ceiling * 32768f / peakAfter else 1f
                pcm[i] = (left * limitingGain).roundToInt().coerceIn(-32768, 32767).toShort()
                pcm[i + 1] = (right * limitingGain).roundToInt().coerceIn(-32768, 32767).toShort()
                i += 2
            }
            offset = end
        }
    }
}
