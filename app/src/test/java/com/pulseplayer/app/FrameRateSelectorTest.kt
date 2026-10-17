package com.pulseplayer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameRateSelectorTest {
    private val modes = listOf(
        FrameRateSelector.Mode(1, 1080, 2400, 60f),
        FrameRateSelector.Mode(2, 1080, 2400, 120f),
        FrameRateSelector.Mode(3, 1080, 2400, 48f),
        FrameRateSelector.Mode(4, 1440, 3200, 50f)
    )

    @Test fun selects48HzFor24FpsContent() {
        assertEquals(3, FrameRateSelector.choose(modes, 1080, 2400, 24f)?.id)
    }
    @Test fun selects60HzFor30FpsContent() {
        assertEquals(1, FrameRateSelector.choose(modes, 1080, 2400, 30f)?.id)
    }
    @Test fun noModeWhenFpsNotCompatible() {
        assertNull(FrameRateSelector.choose(modes, 1080, 2400, 25f))
    }
    @Test fun neverChangesResolution() {
        assertNull(FrameRateSelector.choose(modes, 720, 1280, 24f))
    }
    @Test fun rejectsInvalidFramerates() {
        assertNull(FrameRateSelector.choose(modes, 1080, 2400, Float.NaN))
    }
}
