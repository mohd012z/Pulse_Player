package com.pulseplayer.app

import java.net.URI
import java.util.Locale

/** Pure validation logic shared by Android components; testable without an Android SDK. */
object PlaybackInputs {
    private val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "aiff", "amr")

    fun isAudioFile(name: String, mimeType: String?): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in audioExtensions || mimeType?.startsWith("audio/", ignoreCase = true) == true
    }

    /** Restrict radio bookmarks to HTTPS streams; deny credentials and ambiguous URLs. */
    fun isValidRadioStream(value: String): Boolean {
        if (value.length !in 12..2048 || value.any { it.isWhitespace() || it.isISOControl() }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null &&
            uri.port in -1..65535 && uri.port != 0 &&
            !uri.path.orEmpty().endsWith(".pls", ignoreCase = true) &&
            !uri.path.orEmpty().endsWith(".m3u", ignoreCase = true)
    }
}