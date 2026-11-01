package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CrossfadeEnvelopeTest {
    @Test fun endpointsAndMidpoint() {
        val start = CrossfadeEnvelope.gains(0, 4000)
        assertEquals(1f, start.first, 0.0001f)
        assertEquals(0f, start.second, 0.0001f)
        val middle = CrossfadeEnvelope.gains(2000, 4000)
        assertTrue(abs(middle.first * middle.first + middle.second * middle.second - 1f) < 0.0001f)
        val end = CrossfadeEnvelope.gains(4000, 4000)
        assertEquals(0f, end.first, 0.0001f)
        assertEquals(1f, end.second, 0.0001f)
    }
    @Test fun boundsAndZero() {
        assertEquals(0f to 1f, CrossfadeEnvelope.gains(100, 0))
        assertEquals(1f to 0f, CrossfadeEnvelope.gains(-100, 1000))
        assertEquals(0f to 1f, CrossfadeEnvelope.gains(2000, 1000))
        assertFalse(CrossfadeEnvelope.validDuration(-1))
        assertFalse(CrossfadeEnvelope.validDuration(12001))
        assertTrue(CrossfadeEnvelope.validDuration(12000))
    }
    @Test fun eligibilityExcludesUnknownShortAndUnseekable() {
        assertFalse(CrossfadeEnvelope.eligible(-1, 0, 4000, true, true, true))
        assertFalse(CrossfadeEnvelope.eligible(3000, 0, 4000, true, true, true))
        assertFalse(CrossfadeEnvelope.eligible(10000, 8000, 4000, false, true, true))
        assertFalse(CrossfadeEnvelope.eligible(10000, 8000, 4000, true, true, false))
        assertFalse(CrossfadeEnvelope.eligible(10000, 8000, 4000, true, false, true))
        assertTrue(CrossfadeEnvelope.eligible(10000, 7000, 4000, true, true, true))
        assertFalse(CrossfadeEnvelope.eligible(10000, 3000, 4000, true, true, true))
    }
}
