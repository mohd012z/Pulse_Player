package com.pulseplayer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldMusicInputsTest {
    @Test fun identifiersAndQueriesMustBeValid() {
        assertTrue(WorldMusicInputs.validClientId("my_app_123"))
        assertFalse(WorldMusicInputs.validClientId("bad secret!"))
        assertTrue(WorldMusicInputs.validSearch("indie pop"))
        assertFalse(WorldMusicInputs.validSearch("x"))
    }
    @Test fun onlySafeHttpsAudioUrls() {
        assertTrue(WorldMusicInputs.validAudioUrl("https://usercontent.jamendo.com/audio.mp3"))
        assertFalse(WorldMusicInputs.validAudioUrl("http://example.com/file.mp3"))
        assertFalse(WorldMusicInputs.validAudioUrl("https://user@example.com/file.mp3"))
    }
}