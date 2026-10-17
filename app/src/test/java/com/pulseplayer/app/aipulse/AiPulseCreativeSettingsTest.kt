package com.pulseplayer.app.aipulse

import org.junit.Assert.*
import org.junit.Test

class AiPulseCreativeSettingsTest {
    @Test fun simpleInstrumentalProducesInstrumentalTask() {
        val plan = AiPulseCreativeSettings.plan(
            AiPulseCreativeSettings.Settings(description = "Warm jazz piano", instrumental = true)
        )
        assertEquals(AiPulseGenerationRouter.Task.INSTRUMENTAL, plan.task)
        assertTrue(plan.prompt.contains("Warm jazz piano"))
    }

    @Test fun advancedLyricsAndExclusionsArePreserved() {
        val plan = AiPulseCreativeSettings.plan(
            AiPulseCreativeSettings.Settings(mode = AiPulseCreativeSettings.Mode.ADVANCED,
                description = "Original melody", lyrics = "My own lyrics",
                style = "Acoustic folk", exclusions = "distorted guitars",
                instrumental = false)
        )
        assertEquals(AiPulseGenerationRouter.Task.VOCAL_SONG, plan.task)
        assertTrue(plan.prompt.contains("My own lyrics"))
        assertEquals("distorted guitars", plan.negativePrompt)
    }

    @Test fun speechRequiresSpeechBackend() {
        val plan = AiPulseCreativeSettings.plan(
            AiPulseCreativeSettings.Settings(mode = AiPulseCreativeSettings.Mode.SPEECH,
                description = "A calm narration")
        )
        assertEquals(AiPulseGenerationRouter.Task.SPEECH, plan.task)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unauthorizedReferenceRejected() {
        AiPulseCreativeSettings.plan(AiPulseCreativeSettings.Settings(
            description = "New track", referenceAudioUri = "content://sample", audioInfluence = 70
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun audioInfluenceWithoutReferenceRejected() {
        AiPulseCreativeSettings.plan(AiPulseCreativeSettings.Settings(
            description = "New track", audioInfluence = 50
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSliderRejected() {
        AiPulseCreativeSettings.plan(AiPulseCreativeSettings.Settings(
            description = "New track", weirdness = 101
        ))
    }
}
