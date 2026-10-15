package com.pulseplayer.app

import java.net.URI

/** Pure link validation/normalization for provider-owned playback experiences.
 * Does not resolve, download, transcode, or intercept protected media streams.
 */
object ProviderMediaUrls {
    enum class Provider { YOUTUBE, YOUTUBE_MUSIC, SPOTIFY, JOOX }
    enum class Mode { WEB, YOUTUBE_EMBED, SPOTIFY_EMBED }
    data class Destination(val url: String, val mode: Mode, val message: String)

    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
    private val playlistId = Regex("^[A-Za-z0-9_-]{12,100}$")
    private val spotifyId = Regex("^[A-Za-z0-9]{22}$")
    private val spotifyTypes = setOf("track", "album", "playlist", "artist", "episode", "show")

    fun default(provider: Provider): Destination = when (provider) {
        Provider.YOUTUBE -> Destination("https://www.youtube.com/", Mode.WEB, "Use a YouTube video link for the official in-app player")
        Provider.YOUTUBE_MUSIC -> Destination("https://music.youtube.com/", Mode.WEB, "YouTube Music website; playback depends on Google's browser support")
        Provider.SPOTIFY -> Destination("https://open.spotify.com/", Mode.WEB, "Paste a Spotify track or playlist share link for an official embed")
        Provider.JOOX -> Destination("https://www.joox.com/", Mode.WEB, "JOOX official website; native SDK playback requires JOOX authorization")
    }

    fun resolve(provider: Provider, input: String): Destination? {
        val link = input.trim()
        if (link.isEmpty()) return default(provider)
        if (link.length > 2048 || link.any { Character.isISOControl(it) }) return null
        val uri = try { URI(link) } catch (_: Exception) { return null }
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null || uri.port != -1) return null
        if (uri.fragment != null) return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty()
        val segments = path.split('/').filter { it.isNotEmpty() }
        return when (provider) {
            Provider.YOUTUBE, Provider.YOUTUBE_MUSIC -> {
                val id = when {
                    host == "youtu.be" && segments.size == 1 -> segments[0]
                    host in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com") && segments.size == 1 && segments[0] in setOf("watch") -> query(uri.rawQuery, "v")
                    host in setOf("youtube.com", "www.youtube.com", "m.youtube.com") && segments.size == 2 && segments[0] in setOf("embed", "shorts", "live") -> segments[1]
                    else -> null
                }
                if (id != null && videoId.matches(id)) {
                    Destination("https://www.youtube.com/embed/$id?playsinline=1&rel=0", Mode.YOUTUBE_EMBED, "Official YouTube player, video remains visible")
                } else {
                    val list = query(uri.rawQuery, "list")
                    if (host in setOf("youtube.com", "www.youtube.com", "music.youtube.com") && path in setOf("/playlist", "/watch") && list != null && playlistId.matches(list)) {
                        Destination("https://www.youtube.com/embed/videoseries?list=$list", Mode.YOUTUBE_EMBED, "Official YouTube playlist player")
                    } else null
                }
            }
            Provider.SPOTIFY -> {
                if (host != "open.spotify.com") return null
                val normalized = if (segments.firstOrNull()?.startsWith("intl-") == true) segments.drop(1) else segments
                val normalizedEmbed = if (normalized.firstOrNull() == "embed") normalized.drop(1) else normalized
                if (normalizedEmbed.size != 2) return null
                val kind = normalizedEmbed[0]; val id = normalizedEmbed[1]
                if (kind !in spotifyTypes || !spotifyId.matches(id)) return null
                Destination("https://open.spotify.com/embed/$kind/$id", Mode.SPOTIFY_EMBED, "Official Spotify embed; availability and playback limits vary")
            }
            Provider.JOOX -> {
                if (host !in setOf("www.joox.com", "joox.com") || uri.rawPath.orEmpty().contains("\\")) return null
                Destination(link, Mode.WEB, "JOOX official website; playback depends on JOOX service support")
            }
        }
    }

    private fun query(raw: String?, key: String): String? = raw?.split('&')?.firstNotNullOfOrNull { entry ->
        val parts = entry.split('=', limit = 2)
        if (parts.size == 2 && parts[0] == key) parts[1] else null
    }
}