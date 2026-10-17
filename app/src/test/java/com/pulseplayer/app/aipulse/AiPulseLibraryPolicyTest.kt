package com.pulseplayer.app.aipulse

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AiPulseLibraryPolicyTest {
    @Test fun supportsCommonFormatsAndMimeFallback() {
        val examples = mapOf("song.MP3" to AiPulseLibraryPolicy.Format.MP3,
            "song.wave" to AiPulseLibraryPolicy.Format.WAV,
            "song.flac" to AiPulseLibraryPolicy.Format.FLAC,
            "song.aac" to AiPulseLibraryPolicy.Format.AAC,
            "song.m4a" to AiPulseLibraryPolicy.Format.M4A,
            "song.ogg" to AiPulseLibraryPolicy.Format.OGG,
            "song.opus" to AiPulseLibraryPolicy.Format.OPUS,
            "song.aiff" to AiPulseLibraryPolicy.Format.AIFF)
        examples.forEach { (name, expected) -> assertEquals(expected, AiPulseLibraryPolicy.format(name)) }
        assertEquals(AiPulseLibraryPolicy.Format.MP3, AiPulseLibraryPolicy.format("untitled", "audio/mpeg"))
        assertEquals(AiPulseLibraryPolicy.Format.UNKNOWN, AiPulseLibraryPolicy.format("song.bin"))
    }

    @Test fun unknownMetadataIsNotFabricated() {
        val t = AiPulseLibraryPolicy.TrackInfo(null, " ", null, "demo.mp3", null, null, null, null, AiPulseLibraryPolicy.Format.MP3)
        assertEquals("demo", AiPulseLibraryPolicy.displayTitle(t))
        assertEquals("Unknown artist", AiPulseLibraryPolicy.displayArtist(t))
        assertEquals(AiPulseLibraryPolicy.Quality.UNKNOWN, AiPulseLibraryPolicy.estimatedQuality(t))
    }

    @Test fun qualityIsConservative() {
        fun track(format: AiPulseLibraryPolicy.Format, kbps: Int?) =
            AiPulseLibraryPolicy.TrackInfo(null, null, null, "x", null, kbps, null, null, format)
        assertEquals(AiPulseLibraryPolicy.Quality.LOW, AiPulseLibraryPolicy.estimatedQuality(track(AiPulseLibraryPolicy.Format.MP3, 96)))
        assertEquals(AiPulseLibraryPolicy.Quality.HIGH, AiPulseLibraryPolicy.estimatedQuality(track(AiPulseLibraryPolicy.Format.MP3, 320)))
        assertEquals(AiPulseLibraryPolicy.Quality.LOSSLESS, AiPulseLibraryPolicy.estimatedQuality(track(AiPulseLibraryPolicy.Format.FLAC, null)))
    }

    @Test fun externalDeletionRequiresConsent() {
        assertEquals(AiPulseLibraryPolicy.Removal.UNLINK_ONLY, AiPulseLibraryPolicy.removal(false, false))
        assertEquals(AiPulseLibraryPolicy.Removal.REQUIRE_SYSTEM_CONSENT, AiPulseLibraryPolicy.removal(false, true))
        assertEquals(AiPulseLibraryPolicy.Removal.MOVE_OWNED_TO_TRASH, AiPulseLibraryPolicy.removal(true, true))
    }

    @Test fun ownedPathCannotEscapeDirectory() {
        val root = File(System.getProperty("java.io.tmpdir"), "pulse-owned")
        assertTrue(AiPulseLibraryPolicy.isAppOwnedFile(File(root, "song.wav"), root))
        assertFalse(AiPulseLibraryPolicy.isAppOwnedFile(File(root, "../external.wav"), root))
        assertFalse(AiPulseLibraryPolicy.isAppOwnedFile(root, root))
    }
}
