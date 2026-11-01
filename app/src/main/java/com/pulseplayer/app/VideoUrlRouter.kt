package com.pulseplayer.app

import android.net.Uri

/** Classifies user-supplied URLs. This does not extract streams from third-party webpages. */
internal object VideoUrlRouter {
    enum class Kind { HLS, DASH, VIDEO, YOUTUBE, WEBPAGE, INVALID }
    fun classify(value: String): Kind {
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return Kind.INVALID
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.port !in -1..65535) return Kind.INVALID
        val host = uri.host!!.lowercase().trimEnd('.')
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) ||
            host.contains(':')) return Kind.INVALID
        if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be") return Kind.YOUTUBE
        return when (uri.path.orEmpty().lowercase().substringAfterLast('.')) {
            "m3u8" -> Kind.HLS
            "mpd" -> Kind.DASH
            "mp4", "m4v", "webm", "mkv", "mov" -> Kind.VIDEO
            else -> Kind.WEBPAGE
        }
    }
}
