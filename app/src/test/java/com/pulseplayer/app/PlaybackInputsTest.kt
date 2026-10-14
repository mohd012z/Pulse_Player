package com.pulseplayer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackInputsTest {
    @Test fun recognizesAudioFilesByMimeAndExtension() {
        assertTrue(PlaybackInputs.isAudioFile("song.FLAC", null))
        assertTrue(PlaybackInputs.isAudioFile("audio.bin", "audio/aac"))
        assertFalse(PlaybackInputs.isAudioFile("image.png", "image/png"))
    }

    @Test fun rejectsRadioInsecureOrNonStreamUrls() {
        assertTrue(PlaybackInputs.isValidRadioStream("https://radio.example.net/live.mp3"))
        assertFalse(PlaybackInputs.isValidRadioStream("http://radio.example.net/live.mp3"))
        assertFalse(PlaybackInputs.isValidRadioStream("https://name:secret@radio.example.net/live"))
        assertFalse(PlaybackInputs.isValidRadioStream("https://radio.example.net/list.pls"))
    }
}