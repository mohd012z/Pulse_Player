package com.pulseplayer.app.aipulse

import org.junit.Assert.*
import org.junit.Test

class AiPulseGenerationRouterTest {
    private val procedural = AiPulseGenerationRouter.Backend(
        "procedural", AiPulseGenerationRouter.Kind.PROCEDURAL_OFFLINE,
        setOf(AiPulseGenerationRouter.Task.INSTRUMENTAL), true, true, false
    )
    private val remote = AiPulseGenerationRouter.Backend(
        "remote", AiPulseGenerationRouter.Kind.NEURAL_REMOTE,
        setOf(AiPulseGenerationRouter.Task.INSTRUMENTAL), true, true, true
    )
    private val local = AiPulseGenerationRouter.Backend(
        "local", AiPulseGenerationRouter.Kind.NEURAL_LOCAL,
        setOf(AiPulseGenerationRouter.Task.INSTRUMENTAL), true, true, false, 4096
    )

    @Test fun localNeuralPreferredWhenEligible() {
        val result = AiPulseGenerationRouter.choose(
            AiPulseGenerationRouter.Request("warm piano", AiPulseGenerationRouter.Task.INSTRUMENTAL),
            AiPulseGenerationRouter.Device(8192, true), listOf(remote, procedural, local)
        )
        assertEquals("local", result.backend?.id)
    }

    @Test fun remoteRequiresExplicitConsent() {
        val result = AiPulseGenerationRouter.choose(
            AiPulseGenerationRouter.Request("warm piano", AiPulseGenerationRouter.Task.INSTRUMENTAL),
            AiPulseGenerationRouter.Device(2048, true), listOf(remote)
        )
        assertNull(result.backend)
    }

    @Test fun offlineFallsBackToProcedural() {
        val result = AiPulseGenerationRouter.choose(
            AiPulseGenerationRouter.Request("warm piano", AiPulseGenerationRouter.Task.INSTRUMENTAL, offlineOnly = true),
            AiPulseGenerationRouter.Device(1024, false), listOf(remote, local, procedural)
        )
        assertEquals("procedural", result.backend?.id)
    }

    @Test fun unlicensedEngineRejected() {
        val result = AiPulseGenerationRouter.choose(
            AiPulseGenerationRouter.Request("warm piano", AiPulseGenerationRouter.Task.INSTRUMENTAL),
            AiPulseGenerationRouter.Device(8192, true), listOf(local.copy(licenseApproved = false))
        )
        assertNull(result.backend)
    }

    @Test fun unsupportedSpeechCannotClaimSuccess() {
        val result = AiPulseGenerationRouter.choose(
            AiPulseGenerationRouter.Request("hello", AiPulseGenerationRouter.Task.SPEECH),
            AiPulseGenerationRouter.Device(8192, true), listOf(local, procedural)
        )
        assertNull(result.backend)
    }
}
