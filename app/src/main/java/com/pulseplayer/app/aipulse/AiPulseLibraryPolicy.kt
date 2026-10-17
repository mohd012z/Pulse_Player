package com.pulseplayer.app.aipulse

import java.io.File
import java.util.Locale

/**
 * Conservative media-library decisions. Never deletes a user-owned source file.
 * Actual SAF/MediaStore deletion must be requested through Android's consent APIs.
 */
object AiPulseLibraryPolicy {
    enum class Format { MP3, WAV, FLAC, AAC, M4A, OGG, OPUS, AIFF, UNKNOWN }
    enum class Quality { LOSSLESS, HIGH, STANDARD, LOW, UNKNOWN }
    enum class Removal { UNLINK_ONLY, MOVE_OWNED_TO_TRASH, REQUIRE_SYSTEM_CONSENT }

    data class TrackInfo(
        val title: String?,
        val artist: String?,
        val album: String?,
        val fileName: String,
        val mimeType: String?,
        val bitrateKbps: Int?,
        val sampleRateHz: Int?,
        val channels: Int?,
        val format: Format
    )

    fun format(fileName: String, mimeType: String? = null): Format {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "mp3" -> Format.MP3
            "wav", "wave" -> Format.WAV
            "flac" -> Format.FLAC
            "aac" -> Format.AAC
            "m4a", "mp4" -> Format.M4A
            "ogg", "oga" -> Format.OGG
            "opus" -> Format.OPUS
            "aif", "aiff" -> Format.AIFF
            else -> when (mimeType?.lowercase(Locale.ROOT)) {
                "audio/mpeg" -> Format.MP3
                "audio/wav", "audio/x-wav", "audio/wave" -> Format.WAV
                "audio/flac", "audio/x-flac" -> Format.FLAC
                "audio/aac" -> Format.AAC
                "audio/mp4", "audio/x-m4a" -> Format.M4A
                "audio/ogg" -> Format.OGG
                "audio/opus" -> Format.OPUS
                "audio/aiff", "audio/x-aiff" -> Format.AIFF
                else -> Format.UNKNOWN
            }
        }
    }

    fun displayTitle(track: TrackInfo): String =
        track.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: track.fileName.substringBeforeLast('.', track.fileName).ifBlank { "Unknown title" }

    fun displayArtist(track: TrackInfo): String =
        track.artist?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown artist"

    /**
     * This is a technical bitrate indicator, not an audio-quality or listening test.
     * Upsampled/transcoded audio cannot be repaired by increasing bitrate.
     */
    fun estimatedQuality(track: TrackInfo): Quality {
        if (track.format == Format.UNKNOWN) return Quality.UNKNOWN
        if (track.format in setOf(Format.WAV, Format.FLAC, Format.AIFF)) return Quality.LOSSLESS
        val kbps = track.bitrateKbps ?: return Quality.UNKNOWN
        if (kbps <= 0) return Quality.UNKNOWN
        return when {
            kbps >= 256 -> Quality.HIGH
            kbps >= 128 -> Quality.STANDARD
            else -> Quality.LOW
        }
    }

    fun removal(appOwned: Boolean, deleteOriginalRequested: Boolean): Removal = when {
        !deleteOriginalRequested -> Removal.UNLINK_ONLY
        appOwned -> Removal.MOVE_OWNED_TO_TRASH
        else -> Removal.REQUIRE_SYSTEM_CONSENT
    }

    /** Only app-private files under the canonical owned directory are eligible. */
    fun isAppOwnedFile(file: File, ownedDirectory: File): Boolean {
        val root = ownedDirectory.canonicalFile.toPath()
        val candidate = file.canonicalFile.toPath()
        return candidate != root && candidate.startsWith(root)
    }
}
