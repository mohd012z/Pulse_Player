package com.pulseplayer.data.room

/**
 * Pure state-transition policy used before wiring a WorkManager download worker.
 * A completed file cannot be silently overwritten by a duplicate queue request.
 */
object DownloadQueuePolicy {
    fun canTransition(from: DownloadState, to: DownloadState): Boolean = when (from) {
        DownloadState.PENDING -> to == DownloadState.DOWNLOADING || to == DownloadState.FAILED
        DownloadState.DOWNLOADING -> to == DownloadState.COMPLETED || to == DownloadState.FAILED
        DownloadState.FAILED -> to == DownloadState.PENDING
        DownloadState.COMPLETED -> false
    }

    fun safeProgress(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    /** Strip path separators and control characters before creating MediaStore display names. */
    fun safeDisplayName(raw: String, extension: String): String {
        require(extension in setOf("mp4", "webm", "m4a", "mp3", "aac", "opus")) { "Unsupported extension" }
        val base = raw.replace(Regex("[\\\\/\\p{Cntrl}]"), "_")
            .trim().trim('.').take(80).ifBlank { "PulseMedia" }
        return "$base.$extension"
    }
}
