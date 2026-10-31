package com.pulseplayer.app

import java.net.URI
import java.util.Locale

/**
 * Prevents forwarding WebView cookies to a different HTTPS origin.
 * A port is part of an origin even though HTTP cookies are not port-scoped.
 */
internal object DownloadCookieOriginPolicy {
    private data class HttpsOrigin(val host: String, val port: Int)

    fun canForwardCookies(pageUrl: String, downloadUrl: String): Boolean {
        val pageOrigin = parseHttpsOrigin(pageUrl) ?: return false
        val downloadOrigin = parseHttpsOrigin(downloadUrl) ?: return false
        return pageOrigin == downloadOrigin
    }

    private fun parseHttpsOrigin(value: String): HttpsOrigin? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme?.equals("https", ignoreCase = true) != true || uri.rawUserInfo != null) {
            return null
        }
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        val port = when (uri.port) {
            -1, 443 -> 443
            in 1..65535 -> uri.port
            else -> return null
        }
        return HttpsOrigin(host, port)
    }
}
