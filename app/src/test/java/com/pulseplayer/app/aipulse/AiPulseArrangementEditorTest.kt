package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPulseArrangementEditorTest {
    @Test fun reorderPreservesContiguousBarRanges() {
        val original = AiPulseBrain.plan(AiPulseBrain.Input("Pop Rock", "bright", 110, bars = 16))
        val edited = AiPulseArrangementEditor.move(original, 1, 2)
        assertEquals(16, edited.totalBars)
        assertEquals(original.segments[1].section, edited.segments[2].section)
        var nextBar = 0
        edited.segments.forEach {
            assertEquals(nextBar, it.startBar)
            nextBar += it.bars
        }
        assertEquals(16, nextBar)
    }

    @Test fun lengthEditReindexesAndEnforcesLimits() {
        val original = AiPulseBrain.plan(AiPulseBrain.Input("Ballad", "sad", 90, bars = 12))
        val edited = AiPulseArrangementEditor.update(original, 1, bars = 8)
        assertEquals(16, edited.totalBars)
        assertTrue(runCatching { AiPulseArrangementEditor.update(edited, 1, bars = 3) }.isFailure)
        assertTrue(runCatching { AiPulseArrangementEditor.move(original, 0, -1) }.isFailure)
    }
}
