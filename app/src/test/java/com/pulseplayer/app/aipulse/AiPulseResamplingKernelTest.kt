package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class AiPulseResamplingKernelTest {
    @Test fun constantSignalPreservesLevelAtDifferentRates() {
        for (target in listOf(16000, 22050, 44100, 48000)) {
            val value = AiPulseResamplingKernel.interpolate(135.375, 48000, target) { 0.32 }
            assertEquals(0.32, value, 1e-8)
        }
    }

    @Test fun highFrequencyIsReducedWhenDownsampling() {
        val highHz = 18000.0
        val high = AiPulseResamplingKernel.interpolate(100.5, 48000, 16000) { index ->
            sin(2 * PI * highHz * index / 48000.0)
        }
        assertTrue(abs(high) < 0.5)
    }

    @Test fun rejectsNonFiniteInput() {
        assertTrue(runCatching {
            AiPulseResamplingKernel.interpolate(Double.NaN, 44100, 48000) { 0.0 }
        }.isFailure)
    }
}
