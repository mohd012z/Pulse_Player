package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Test

class AiPulseSourceGuardTest {
    private val url = "https://example.org/audio"
    @Test fun unknownLicenseRequiresReview() {
        val s = AiPulseSourceGuard.Source("catalog", url, AiPulseSourceGuard.License.UNKNOWN)
        assertEquals(AiPulseSourceGuard.Verdict.REVIEW,
            AiPulseSourceGuard.decide(s, AiPulseSourceGuard.Action.SAMPLE).verdict)
    }
    @Test fun proprietaryWithoutPermissionDenied() {
        val s = AiPulseSourceGuard.Source("catalog", url, AiPulseSourceGuard.License.PROPRIETARY)
        assertEquals(AiPulseSourceGuard.Verdict.DENY,
            AiPulseSourceGuard.decide(s, AiPulseSourceGuard.Action.DISTRIBUTE).verdict)
    }
    @Test fun noncommercialCommercialDenied() {
        val s = AiPulseSourceGuard.Source("catalog", url, AiPulseSourceGuard.License.CC_BY_NC, "Artist")
        assertEquals(AiPulseSourceGuard.Verdict.DENY,
            AiPulseSourceGuard.decide(s, AiPulseSourceGuard.Action.SAMPLE, commercial = true).verdict)
    }
    @Test fun trainingRequiresExplicitAuthorization() {
        val s = AiPulseSourceGuard.Source("catalog", url, AiPulseSourceGuard.License.CC0)
        assertEquals(AiPulseSourceGuard.Verdict.DENY,
            AiPulseSourceGuard.decide(s, AiPulseSourceGuard.Action.TRAIN).verdict)
    }
}
