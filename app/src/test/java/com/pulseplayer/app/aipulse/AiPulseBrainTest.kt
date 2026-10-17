package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPulseBrainTest {
    @Test fun generatesBoundedPlan() {
        val plan = AiPulseBrain.plan(AiPulseBrain.Input("EDM", "bright", 128, bars = 32))
        assertEquals(32, plan.totalBars)
        assertTrue(plan.segments.isNotEmpty())
        assertEquals(0, plan.segments.first().startBar)
        assertTrue(plan.segments.all { it.energy in 0.0..1.0 && it.chordRoots.size == 4 })
        assertEquals(32, plan.segments.last().startBar + plan.segments.last().bars)
    }

    @Test fun moodChangesHarmony() {
        val bright = AiPulseBrain.plan(AiPulseBrain.Input("Ballad", "bright", 90))
        val sad = AiPulseBrain.plan(AiPulseBrain.Input("Ballad", "sad", 90))
        assertTrue(bright.segments.first().chordRoots != sad.segments.first().chordRoots)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExcessiveBars() {
        AiPulseBrain.plan(AiPulseBrain.Input("Pop Rock", "bright", 120, bars = 1000))
    }
}
