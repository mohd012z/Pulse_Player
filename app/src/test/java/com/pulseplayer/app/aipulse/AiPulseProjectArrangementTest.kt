package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPulseProjectArrangementTest {
    @Test fun projectAcceptsEditedArrangementAndMixer() {
        val base = AiPulseBrain.plan(AiPulseBrain.Input("Pop Rock", "bright", 110, 16))
        val edited = AiPulseArrangementEditor.move(base, 1, 2)
        val project = AiPulseProject(
            title = "Song", idea = "Story", genre = "Pop Rock",
            language = "English", mood = "bright", instruments = emptyList(),
            lyrics = "", bpm = 110,
            mixer = AiPulseMixPreset(lead = 85),
            arrangement = edited
        )
        assertEquals(16, project.arrangement?.totalBars)
        assertEquals(85, project.mixer.lead)
        assertEquals(base.segments[1].section, project.arrangement?.segments?.get(2)?.section)
    }

    @Test fun arrangementNormalizationRejectsGapsAndBadLengths() {
        val plan = AiPulseBrain.plan(AiPulseBrain.Input("R&B", "sad", 92, 12))
        assertTrue(runCatching {
            AiPulseArrangementEditor.rebuild(plan.copy(
                segments = plan.segments.mapIndexed { index, segment ->
                    if (index == 1) segment.copy(bars = 3) else segment
                }
            ))
        }.isFailure)
    }
}
