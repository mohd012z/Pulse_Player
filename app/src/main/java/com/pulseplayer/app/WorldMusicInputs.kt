package com.pulseplayer.app

import java.net.URI

/** Pure request and audio URL validation for authorized catalog streams. */
object WorldMusicInputs {
    fun validClientId(value: String): Boolean = value.length in 4..128 &&
        value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    fun validSearch(value: String): Boolean = value.trim().length in 2..100 &&
        value.none { it.isISOControl() }

    fun validAudioUrl(value: String): Boolean {
        if (value.length !in 12..2048 || value.any { it.isWhitespace() || it.isISOControl() }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }
}