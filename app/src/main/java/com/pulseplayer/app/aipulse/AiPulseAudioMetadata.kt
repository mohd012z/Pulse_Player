package com.pulseplayer.app.aipulse

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Read-only inspection of a user-selected local audio URI.
 * Never guesses a decoder from extension alone or mutates the source.
 */
object AiPulseAudioMetadata {
    data class Result(
        val displayName: String,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long?,
        val bitrateKbps: Int?,
        val format: AiPulseLibraryPolicy.Format,
        val quality: AiPulseLibraryPolicy.Quality,
        val sizeBytes: Long?
    )

    fun inspect(context: Context, uri: Uri): Result {
        val resolver = context.contentResolver
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val filename = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "Unknown audio"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun tag(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            val bitrate = tag(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                ?.takeIf { it > 0 }?.div(1000)?.toInt()
            val format = AiPulseLibraryPolicy.format(filename, resolver.getType(uri))
            val track = AiPulseLibraryPolicy.TrackInfo(
                tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                filename, resolver.getType(uri), bitrate, null, null, format
            )
            return Result(filename, AiPulseLibraryPolicy.displayTitle(track),
                AiPulseLibraryPolicy.displayArtist(track), track.album,
                tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                bitrate, format, AiPulseLibraryPolicy.estimatedQuality(track), size)
        } finally {
            retriever.release()
        }
    }
}
