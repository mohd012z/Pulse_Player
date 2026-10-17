package com.pulseplayer.app.aipulse

/**
 * Smoothly interpolates arrangement energy immediately after a section boundary.
 * A short smoothstep ramp prevents instantaneous gain jumps (not a mastering limiter).
 */
object AiPulseSectionEnvelope {
    fun energyAt(
        plan: AiPulseBrain.Plan,
        beat: Double,
        beatsPerBar: Int = 4,
        transitionBeats: Double = 0.25
    ): Double {
        require(beat.isFinite() && beat >= 0.0)
        require(beatsPerBar > 0 && transitionBeats.isFinite() && transitionBeats > 0.0)
        require(plan.segments.isNotEmpty())
        val bar = (beat / beatsPerBar).toInt()
        val idx = plan.segments.indexOfLast { bar >= it.startBar }.coerceAtLeast(0)
        val current = plan.segments[idx]
        if (idx == 0) return current.energy
        val boundary = current.startBar.toDouble() * beatsPerBar
        val phase = ((beat - boundary) / transitionBeats).coerceIn(0.0, 1.0)
        val smoothstep = phase * phase * (3.0 - 2.0 * phase)
        return plan.segments[idx - 1].energy * (1.0 - smoothstep) +
            current.energy * smoothstep
    }
}
