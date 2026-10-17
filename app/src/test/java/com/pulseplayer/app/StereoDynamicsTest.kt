package com.pulseplayer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StereoDynamicsTest {
    @Test fun bypassDoesNotChangeSamples() {
        val data = shortArrayOf(200, -300, 30000, -31000)
        StereoDynamics(48000).process(data, compress = false, limit = false)
        assertEquals(listOf<Short>(200, -300, 30000, -31000), data.toList())
    }

    @Test fun limiterKeepsStereoSamplesBelowCeiling() {
        val data = shortArrayOf(Short.MAX_VALUE, (-32768).toShort(), 10000, 5000)
        StereoDynamics(48000).process(data, compress = false, limit = true)
        assertTrue(data.all { abs(it.toInt()) <= 30000 })
        assertTrue(data[0] > 0 && data[1] < 0)
    }

    @Test fun compressorAttenuatesSustainedLoudSignals() {
        val source = ShortArray(48000) { if (it % 2 == 0) 29000 else -29000 }
        val dynamics = StereoDynamics(48000)
        dynamics.process(source, compress = true, limit = true)
        assertTrue(abs(source[source.size - 2].toInt()) < 25000)
        assertTrue(source.all { abs(it.toInt()) <= 30000 })
    }

    @Test fun softSignalsRemainUnchanged() {
        val source = ShortArray(512) { if (it % 2 == 0) 100 else -100 }
        StereoDynamics(44100).process(source, compress = true, limit = false)
        assertTrue(source.all { abs(it.toInt()) == 100 })
    }

    @Test fun invalidInputRejected() {
        var rejected = false
        try { StereoDynamics(48000).process(shortArrayOf(1, 2, 3), compress = true, limit = true) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
