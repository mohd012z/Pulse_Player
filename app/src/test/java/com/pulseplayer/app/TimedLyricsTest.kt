package com.pulseplayer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedLyricsTest {
    @Test fun parsesMultipleStampsAndOffset() {
        val lines = TimedLyrics.parse("[offset:-100]\n[00:01.25][00:02.0]Line one\n[00:03.50]Line two")
        assertEquals(3, lines.size)
        assertEquals(1150L, lines[0].atMs)
        assertEquals(1900L, lines[1].atMs)
        assertEquals(3400L, lines[2].atMs)
        assertEquals("Line one", lines[1].text)
    }

    @Test fun selectsMostRecentLyric() {
        val lines = TimedLyrics.parse("[00:01.00]a\n[00:04.00]b")
        assertEquals(-1, TimedLyrics.activeIndex(lines, 999))
        assertEquals(0, TimedLyrics.activeIndex(lines, 1000))
        assertEquals(0, TimedLyrics.activeIndex(lines, 3999))
        assertEquals(1, TimedLyrics.activeIndex(lines, 4000))
        assertTrue(TimedLyrics.parse("[ar:Artist]").isEmpty())
    }
}