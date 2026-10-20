package com.pulseplayer.app

import android.net.Uri

/** Normalized contract for user-owned media and independently authorized providers. */
object PulsePlaybackContract {
    enum class Format { MP4, HLS, DASH, YOUTUBE, EXTERNAL }
    data class Source(
        val contentId: String,
        val providerId: String,
        val url: String,
        val format: Format,
        val authorized: Boolean,
        val approvedHosts: Set<String> = emptySet()
    )
    sealed class Decision {
        data class Direct(val uri: Uri, val format: Format): Decision()
        data class Embed(val uri: Uri): Decision()
        data class Handoff(val uri: Uri): Decision()
        data class Rejected(val reason: String): Decision()
    }
    fun route(source: Source): Decision {
        if (!source.authorized) return Decision.Rejected("Provider authorization required")
        val uri = runCatching { Uri.parse(source.url) }.getOrNull()
            ?: return Decision.Rejected("Invalid URL")
        val kind = PulseProviderRegistry.resolve(uri, source.approvedHosts)
            ?: return Decision.Rejected("Unapproved URL")
        return when (source.format) {
            Format.MP4, Format.HLS, Format.DASH ->
                if (kind == PulseProviderRegistry.Kind.LOCAL || kind == PulseProviderRegistry.Kind.MEDIA3)
                    Decision.Direct(uri, source.format)
                else Decision.Rejected("Direct playback requires an approved media source")
            Format.YOUTUBE ->
                if (kind == PulseProviderRegistry.Kind.YOUTUBE_EMBED) Decision.Embed(uri)
                else Decision.Rejected("Not a YouTube provider URL")
            Format.EXTERNAL ->
                if (kind == PulseProviderRegistry.Kind.EXTERNAL || kind == PulseProviderRegistry.Kind.YOUTUBE_EMBED)
                    Decision.Handoff(uri)
                else Decision.Rejected("Not an approved external provider")
        }
    }
}
