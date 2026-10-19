package com.pulseplayer.app

import android.net.Uri

/** Accept only explicit YouTube video or playlist identifiers; never arbitrary WebView URLs. */
object YouTubeLinkParser {
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val listPattern = Regex("^[A-Za-z0-9_-]{10,64}$")

    fun embedUrl(input: String): String? {
        val uri = runCatching { Uri.parse(input.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val video = when (host) {
            "youtu.be", "www.youtu.be" -> uri.pathSegments.firstOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> when (uri.pathSegments.firstOrNull()) {
                "watch" -> uri.getQueryParameter("v")
                "shorts", "live", "embed" -> uri.pathSegments.getOrNull(1)
                else -> null
            }
            else -> return null
        }
        val list = uri.getQueryParameter("list")?.takeIf { listPattern.matches(it) }
        return when {
            video != null && idPattern.matches(video) -> "https://www.youtube.com/embed/$video?playsinline=1&rel=0"
            list != null && host != "youtu.be" && host != "www.youtu.be" -> "https://www.youtube.com/embed/videoseries?list=$list&playsinline=1"
            else -> null
        }
    }
}
