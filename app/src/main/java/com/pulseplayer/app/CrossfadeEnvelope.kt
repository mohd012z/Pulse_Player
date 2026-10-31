package com.pulseplayer.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Equal-power gains for two independent audio streams. */
object CrossfadeEnvelope {
    const val MAX_DURATION_MS = 12_000L
    fun validDuration(durationMs: Long): Boolean = durationMs in 0L..MAX_DURATION_MS
    fun gains(elapsedMs: Long, durationMs: Long): Pair<Float, Float> {
        require(validDuration(durationMs)) { "Duration outside 0..12000 ms" }
        if (durationMs == 0L) return 0f to 1f
        val fraction = (elapsedMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
        if (fraction <= 0.0) return 1f to 0f
        if (fraction >= 1.0) return 0f to 1f
        return cos(fraction * PI / 2).toFloat() to sin(fraction * PI / 2).toFloat()
    }
    fun eligible(
        totalMs: Long, positionMs: Long, fadeMs: Long,
        hasNext: Boolean, playing: Boolean, seekable: Boolean
    ): Boolean = validDuration(fadeMs) && fadeMs > 0 && totalMs > fadeMs &&
        positionMs in 0 until totalMs && totalMs - positionMs <= fadeMs &&
        hasNext && playing && seekable
}
