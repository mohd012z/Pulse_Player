package com.pulseplayer.app.aipulse

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AiPulseDiscoveryGatewayTest {
    @Test fun offlineLookupDoesNotContactNetwork() {
        AiPulseDiscoveryGateway.clear()
        assertTrue(AiPulseDiscoveryGateway.search("test artist", online = false).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankSearch() {
        AiPulseDiscoveryGateway.search(" ", online = false)
    }

    @Test fun offlineSearchRemainsDeterministic() {
        AiPulseDiscoveryGateway.clear()
        assertEquals(
            AiPulseDiscoveryGateway.search("original demo", online = false),
            AiPulseDiscoveryGateway.search("original demo", online = false)
        )
    }
}
