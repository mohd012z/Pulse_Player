package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

class SrtParserTest {
    @Test fun parsesAndSynchronizes() {
        val cues = SrtParser.parse("1\n00:00:01,000 --> 00:00:02,500\nHello world\n\n2\n00:00:03.000 --> 00:00:04.000\nSecond line")
        assertEquals(2, cues.size)
        assertEquals("Hello world", SrtParser.at(cues, 1200))
        assertEquals("", SrtParser.at(cues, 2600))
        assertEquals("Second line", SrtParser.at(cues, 3000))
    }
    @Test fun rejectsInvalidRangesAndBlankText() {
        val cues = SrtParser.parse("1\n00:00:05,000 --> 00:00:04,000\nBad\n\n2\n00:00:01,000 --> 00:00:02,000")
        assertTrue(cues.isEmpty())
    }
}
