package com.pulseplayer.app

/** Strict, pure download controls for tracks authorized by their independent artists. */
object Mp3DownloadInputs {
    enum class Quality(val apiValue: String, val label: String, val suffix: String) {
        STANDARD("mp31", "MP3 standard · 96 kbps", "96kbps"),
        HIGH("mp32", "MP3 high · variable bitrate", "VBR")
    }

    fun validTrackId(id: String): Boolean = id.length in 1..18 && id.all(Char::isDigit)

    fun filename(artist: String, title: String, trackId: String, quality: Quality): String {
        require(validTrackId(trackId)) { "Invalid Jamendo track identifier" }
        fun clean(v: String): String {
            val filtered = v.replace(Regex("[^\\p{L}\\p{N} ._-]"), " ")
                .replace(Regex("\\s+"), " ").trim(' ', '.', '-')
            return filtered.take(48).trimEnd(' ', '.', '-').ifBlank { "Unknown" }
        }
        return "${clean(artist)} - ${clean(title)} - $trackId - ${quality.suffix}.mp3"
    }
}