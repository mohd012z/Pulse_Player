package com.pulseplayer.inspector

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

sealed interface SourceType {
    data class YouTubeWatch(val videoId: String, val timestampSeconds: Long?) : SourceType
    data class YouTubeShorts(val videoId: String) : SourceType
    data class YouTubeLive(val videoId: String) : SourceType
    data class YouTubePlaylist(val playlistId: String) : SourceType
    data class DirectMedia(val url: String, val extension: String) : SourceType
    data class Unsupported(val reason: String) : SourceType
}
data class InspectionResult(val originalUrl: String, val normalizedUrl: String, val sourceType: SourceType)

/** Classification only. Never interpret a YouTube page as a downloadable media file. */
object UrlInspector {
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    private val playlistId = Regex("[A-Za-z0-9_-]{10,128}")
    private val mediaExtensions = setOf("mp4", "webm", "m3u8", "mpd", "m4a", "opus", "mp3", "aac")
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "www.youtube-nocookie.com", "youtube-nocookie.com")
    fun inspect(rawUrl: String?): InspectionResult {
        val original = rawUrl.orEmpty().trim()
        fun reject(reason: String) = InspectionResult(original, original, SourceType.Unsupported(reason))
        if (original.isBlank()) return reject("URL is blank")
        if (original.length > 8192) return reject("URL too long")
        val uri = try { URI(original) } catch (_: Exception) { return reject("Malformed URL") }
        if (!uri.scheme.equals("https", true)) return reject("HTTPS required")
        if (uri.rawUserInfo != null) return reject("URL credentials are not permitted")
        val host = uri.host?.lowercase(Locale.ROOT) ?: return reject("Missing host")
        if (uri.port != -1 && uri.port != 443) return reject("Unsupported port")
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val query = parseQuery(uri.rawQuery)
        fun watch(id: String): InspectionResult {
            val seconds = timestamp(query["t"] ?: query["start"])
            val normalized = "https://www.youtube.com/watch?v=$id" + (seconds?.takeIf { it > 0 }?.let { "&t=$it" } ?: "")
            return InspectionResult(original, normalized, SourceType.YouTubeWatch(id, seconds))
        }
        if (host == "youtu.be") {
            val id = segments.singleOrNull() ?: return reject("Invalid short link path")
            return if (videoId.matches(id)) watch(id) else reject("Invalid video ID")
        }
        if (host in youtubeHosts) {
            return when (segments.firstOrNull()) {
                "shorts", "live", "embed" -> {
                    if (segments.size != 2 || !videoId.matches(segments[1])) reject("Invalid video ID or path")
                    else {
                        val id = segments[1]
                        when (segments[0]) {
                            "shorts" -> InspectionResult(original, "https://www.youtube.com/shorts/$id", SourceType.YouTubeShorts(id))
                            "live" -> InspectionResult(original, "https://www.youtube.com/watch?v=$id", SourceType.YouTubeLive(id))
                            else -> watch(id)
                        }
                    }
                }
                "watch" -> {
                    val id = query["v"]
                    if (segments.size != 1 || id == null || !videoId.matches(id)) reject("Invalid or missing video ID") else watch(id)
                }
                "playlist" -> {
                    val id = query["list"]
                    if (segments.size != 1 || id == null || !playlistId.matches(id)) reject("Invalid playlist ID")
                    else InspectionResult(original, "https://www.youtube.com/playlist?list=$id", SourceType.YouTubePlaylist(id))
                }
                else -> reject("Unsupported YouTube path")
            }
        }
        val extension = segments.lastOrNull()?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        if (extension !in mediaExtensions) return reject("Unsupported source or file extension")
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host == "127.0.0.1" || host == "::1" || host.startsWith("127.") ||
            host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.") ||
            host.startsWith("172.") && host.substringAfter("172.").substringBefore('.').toIntOrNull() in 16..31
        ) return reject("Local or private destination is not allowed")
        // Preserve raw signed query bytes exactly. Network-layer DNS and redirect checks remain mandatory.
        val normalized = original.substringBefore('#')
        return InspectionResult(original, normalized, SourceType.DirectMedia(normalized, extension))
    }
    private fun parseQuery(raw: String?): Map<String, String> = raw.orEmpty().split('&').mapNotNull {
        if (!it.contains('=')) null else {
            val (key, value) = it.split('=', limit = 2)
            try { URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8") }
            catch (_: IllegalArgumentException) { null }
        }
    }.toMap()
    private fun timestamp(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.lowercase(Locale.ROOT)
        if (s.matches(Regex("\\d+"))) return s.toLongOrNull()
        val parts = Regex("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matchEntire(s) ?: return null
        if (parts.value.isEmpty()) return null
        val h = parts.groupValues[1].ifEmpty { "0" }.toLongOrNull() ?: return null
        val m = parts.groupValues[2].ifEmpty { "0" }.toLongOrNull() ?: return null
        val sec = parts.groupValues[3].ifEmpty { "0" }.toLongOrNull() ?: return null
        return try { Math.addExact(Math.addExact(Math.multiplyExact(h, 3600), Math.multiplyExact(m, 60)), sec) }
        catch (_: ArithmeticException) { null }
    }
}
