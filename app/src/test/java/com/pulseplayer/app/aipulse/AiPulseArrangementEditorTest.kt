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
    @Test fun sectionEnergyCanBeAdjustedWithoutMovingBars() {
        val original = AiPulseBrain.plan(AiPulseBrain.Input("Pop Rock", "bright", 110, bars = 16))
        val updated = AiPulseArrangementEditor.update(original, 1, energy = 0.25)
        assertEquals(0.25, updated.segments[1].energy, 0.00001)
        assertEquals(original.segments.map { it.startBar }, updated.segments.map { it.startBar })
        assertEquals(original.totalBars, updated.totalBars)
    }

    @Test fun nonFiniteEnergyIsRejected() {
        val original = AiPulseBrain.plan(AiPulseBrain.Input("Acoustic", "bright", 100, bars = 8))
        assertTrue(runCatching { AiPulseArrangementEditor.update(original, 0, energy = Double.NaN) }.isFailure)
        assertTrue(runCatching { AiPulseArrangementEditor.update(original, 0, energy = Double.POSITIVE_INFINITY) }.isFailure)
        assertTrue(runCatching { AiPulseArrangementEditor.update(original, 0, energy = -0.1) }.isFailure)
    }

    @Test fun insertAndRemoveSectionKeepsTimelineContiguous() {
        val original = AiPulseBrain.plan(AiPulseBrain.Input("Pop Rock", "bright", 120, bars = 8))
        val extended = AiPulseArrangementEditor.insert(original, 0)
        assertEquals(12, extended.totalBars)
        assertEquals(AiPulseBrain.Section.CHORUS, extended.segments[1].section)
        assertEquals(8, extended.segments[2].startBar)
        val restored = AiPulseArrangementEditor.remove(extended, 1)
        assertEquals(original.segments, restored.segments)
    }

    @Test fun cannotRemoveBelowMinimumDurationOrExceedMaximumBars() {
        val short = AiPulseBrain.plan(AiPulseBrain.Input("Ballad", "bright", 90, bars = 8))
        assertTrue(runCatching { AiPulseArrangementEditor.remove(short, 0) }.isFailure)
        val full = AiPulseBrain.plan(AiPulseBrain.Input("EDM", "bright", 125, bars = 128))
        assertTrue(runCatching { AiPulseArrangementEditor.insert(full, 0) }.isFailure)
    }

}
