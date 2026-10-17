package com.pulseplayer.app.aipulse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPulseAssetAdmissionTest {
    private fun source(license: AiPulseSourceGuard.License) =
        AiPulseSourceGuard.Source("test", "https://example.org/source", license)

    @Test fun rejectsWithoutRightsConfirmation() {
        val request = AiPulseAssetAdmission.Request(source(AiPulseSourceGuard.License.CC0),
            AiPulseAssetAdmission.IntendedUse.REMIX, false, false)
        assertFalse(AiPulseAssetAdmission.assess(request).accepted)
    }

    @Test fun acceptsConfirmedCc0Remix() {
        val request = AiPulseAssetAdmission.Request(source(AiPulseSourceGuard.License.CC0),
            AiPulseAssetAdmission.IntendedUse.REMIX, false, true)
        assertTrue(AiPulseAssetAdmission.assess(request).accepted)
    }

    @Test fun rejectsUnknownLicenseRemix() {
        val request = AiPulseAssetAdmission.Request(source(AiPulseSourceGuard.License.UNKNOWN),
            AiPulseAssetAdmission.IntendedUse.REMIX, false, true)
        assertFalse(AiPulseAssetAdmission.assess(request).accepted)
    }

    @Test fun rejectsUnapprovedTraining() {
        val request = AiPulseAssetAdmission.Request(source(AiPulseSourceGuard.License.CC0),
            AiPulseAssetAdmission.IntendedUse.TRAIN_MODEL, false, true)
        assertFalse(AiPulseAssetAdmission.assess(request).accepted)
    }
}
