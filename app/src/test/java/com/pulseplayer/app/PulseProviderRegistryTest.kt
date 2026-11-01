package com.pulseplayer.app

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

class PulseProviderRegistryTest {
    @Test fun registryHasNoUnapprovedDirectStreamHosts() {
        assertTrue(PulseProviderRegistry.providers.none { it.kind == PulseProviderRegistry.Kind.MEDIA3 })
        assertFalse(PulseProviderRegistry.mayAutoplay(PulseProviderRegistry.Kind.YOUTUBE_EMBED))
        assertFalse(PulseProviderRegistry.mayAutoplay(PulseProviderRegistry.Kind.EXTERNAL))
    }
}
