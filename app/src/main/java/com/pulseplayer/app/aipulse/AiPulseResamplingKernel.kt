package com.pulseplayer.app.aipulse

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Finite windowed-sinc resampling kernel. Restricts frequencies to the lower
 * destination Nyquist rate when downsampling. This is a practical quality
 * improvement, not an audiophile-grade polyphase resampler.
 */
object AiPulseResamplingKernel {
    const val RADIUS = 12

    fun interpolate(sourcePosition: Double, inputRate: Int, outputRate: Int,
                    sample: (Long) -> Double): Double {
        require(sourcePosition.isFinite() && sourcePosition >= 0.0)
        require(inputRate in 8000..192000 && outputRate in 8000..192000)
        val cutoff = min(1.0, outputRate.toDouble() / inputRate)
        val center = sourcePosition.toLong()
        var weighted = 0.0
        var weights = 0.0
        for (offset in -RADIUS + 1..RADIUS) {
            val index = center + offset
            val distance = sourcePosition - index
            val radius = abs(distance) / RADIUS
            if (radius >= 1.0) continue
            val x = PI * cutoff * distance
            val sinc = if (abs(x) < 1e-10) 1.0 else sin(x) / x
            val window = 0.5 + 0.5 * cos(PI * radius)
            val weight = cutoff * sinc * window
            weighted += sample(index) * weight
            weights += weight
        }
        return if (abs(weights) < 1e-10) 0.0 else weighted / weights
    }
}
