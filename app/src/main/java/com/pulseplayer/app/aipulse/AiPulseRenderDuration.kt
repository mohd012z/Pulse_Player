package com.pulseplayer.app.aipulse

import kotlin.math.ceil

/**
 * Maps an edited timeline to a render duration so the final bars are audible.
 * This deliberately rounds up to whole seconds for PCM WAV export.
 */
object AiPulseRenderDuration {
    const val MAX_SECONDS = 180
    fun secondsFor(plan: AiPulseBrain.Plan): Int {
        val valid = AiPulseArrangementEditor.rebuild(plan)
        require(valid.bpm in 60..180)
        val seconds = ceil(valid.totalBars * 4.0 * 60.0 / valid.bpm).toInt()
        require(seconds in 2..MAX_SECONDS) { "Arrangement exceeds supported export duration" }
        return seconds
    }
    fun compatible(plan: AiPulseBrain.Plan?, genre: String, bpm: Int): Boolean =
        plan == null || (plan.genre == genre && plan.bpm == bpm)
}
