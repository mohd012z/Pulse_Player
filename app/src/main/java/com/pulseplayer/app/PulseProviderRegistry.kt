package com.pulseplayer.app

import android.net.Uri
import java.util.Locale

/** Public provider routing only. No scraping, private tokens, or DRM bypass. */
object PulseProviderRegistry {
    enum class Kind { LOCAL, MEDIA3, YOUTUBE_EMBED, EXTERNAL }
    data class Provider(
        val id: String,
        val kind: Kind,
        val hosts: Set<String> = emptySet(),
        val supportsDirectPlayback: Boolean = false,
        val attribution: String = ""
    )
    val providers = listOf(
        Provider("local", Kind.LOCAL, supportsDirectPlayback = true),
        Provider("youtube", Kind.YOUTUBE_EMBED, setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtube-nocookie.com"), attribution = "YouTube"),
        Provider("archive", Kind.EXTERNAL, setOf("archive.org", "www.archive.org"), attribution = "Internet Archive"),
        Provider("wikimedia", Kind.EXTERNAL, setOf("commons.wikimedia.org"), attribution = "Wikimedia Commons"),
        Provider("tmdb", Kind.EXTERNAL, setOf("www.themoviedb.org", "themoviedb.org"), attribution = "TMDB")
    )

    fun resolve(uri: Uri, approvedMediaHosts: Set<String> = emptySet()): Kind? {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme == "content") return Kind.LOCAL
        if (scheme != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
        if (host.isEmpty() || host == "localhost" || host.endsWith(".localhost")) return null
        providers.firstOrNull { host in it.hosts }?.let { return it.kind }
        // Media playback must be explicitly approved, not inferred from .mp4/.m3u8 suffix.
        return if (host in approvedMediaHosts.map { it.lowercase(Locale.ROOT) }) Kind.MEDIA3 else null
    }

    fun mayAutoplay(kind: Kind): Boolean = kind == Kind.LOCAL || kind == Kind.MEDIA3
}
