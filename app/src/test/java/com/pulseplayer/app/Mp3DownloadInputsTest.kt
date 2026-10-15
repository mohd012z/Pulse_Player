package com.pulseplayer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class Mp3DownloadInputsTest {
    @Test fun onlyNumericTrackIdentifiers() {
        assertTrue(Mp3DownloadInputs.validTrackId("1848357"))
        assertFalse(Mp3DownloadInputs.validTrackId("../1848357"))
        assertFalse(Mp3DownloadInputs.validTrackId(""))
        assertFalse(Mp3DownloadInputs.validTrackId("1".repeat(19)))
    }
    @Test fun actualProviderQualityOptions() {
        assertEquals("mp31", Mp3DownloadInputs.Quality.STANDARD.apiValue)
        assertEquals("mp32", Mp3DownloadInputs.Quality.HIGH.apiValue)
    }
    @Test fun safeMp3Name() {
        val path = Mp3DownloadInputs.filename("S/LASH", "..../Test?Song.mp3", "4", Mp3DownloadInputs.Quality.HIGH)
        assertTrue(path.endsWith("4 - VBR.mp3"))
        assertFalse(path.contains("/"))
        assertFalse(path.contains("?"))
    }
}