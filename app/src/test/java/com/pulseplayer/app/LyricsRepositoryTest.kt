package com.pulseplayer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRepositoryTest {
    @Test fun multiTimestampAndOffset() {
        val lines = LyricsRepository.parse("[offset:+100]\n[00:01.50][00:02.500]Hello\n[00:03.00]Next")
        assertEquals(listOf(1600L, 2600L, 3100L), lines.map { it.millis })
        assertEquals(1, LyricsRepository.at(lines, 2800L))
        assertEquals(-1, LyricsRepository.at(lines, 0L))
    }
    @Test fun metadataIgnoredAndSorted() {
        val lines = LyricsRepository.parse("[ar:An artist]\n[00:10.00]Ten\n[00:05.00]Five")
        assertEquals(listOf("Five", "Ten"), lines.map { it.text })
        assertTrue(lines.size == 2)
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedRejected() {
        LyricsRepository.parse("X".repeat(524_289))
    }
}