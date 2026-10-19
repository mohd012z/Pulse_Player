package com.pulseplayer.inspector

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.util.Locale

data class ProgressiveInspectionResult(
    val url: String,
    val containerMimeType: String?,
    val contentLengthBytes: Long?,
    val supportsByteRanges: Boolean,
    val error: String? = null
)

/**
 * HEAD-only metadata probe. Never reads a response body, and does not follow redirects.
 * A subsequent network/download layer must independently enforce DNS/IP and redirect policy.
 */
object ProgressiveMediaInspector {
    fun inspect(url: String): ProgressiveInspectionResult {
        val classification = UrlInspector.inspect(url)
        if (classification.sourceType !is SourceType.DirectMedia) {
            return ProgressiveInspectionResult("[redacted]", null, null, false, "Direct media HTTPS URL required")
        }
        val uri = try { URI(classification.normalizedUrl) }
            catch (_: Exception) { return ProgressiveInspectionResult("[redacted]", null, null, false, "Malformed URL") }
        val host = uri.host ?: return ProgressiveInspectionResult("[redacted]", null, null, false, "Missing host")
        // Avoid hostnames resolving to private networks; DNS rebinding remains a transport-level concern.
        val addresses = try { InetAddress.getAllByName(host) }
            catch (_: Exception) { return ProgressiveInspectionResult("[redacted]", null, null, false, "DNS lookup failed") }
        if (addresses.isEmpty() || addresses.any { it.isAnyLocalAddress || it.isLoopbackAddress ||
                    it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress }) {
            return ProgressiveInspectionResult("[redacted]", null, null, false, "Non-public destination rejected")
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(classification.normalizedUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.useCaches = false
            val status = connection.responseCode
            if (status in 300..399) {
                ProgressiveInspectionResult("[redacted]", null, null, false, "Redirect requires separate validation")
            } else if (status !in 200..299) {
                ProgressiveInspectionResult("[redacted]", null, null, false, "HTTP $status")
            } else {
                val mime = connection.getHeaderField("Content-Type")?.substringBefore(';')?.trim()
                val length = connection.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 }
                val ranges = connection.getHeaderField("Accept-Ranges")?.trim()?.lowercase(Locale.ROOT) == "bytes"
                ProgressiveInspectionResult("[redacted]", mime, length, ranges)
            }
        } catch (_: IOException) {
            ProgressiveInspectionResult("[redacted]", null, null, false, "HEAD inspection unavailable")
        } finally {
            connection?.disconnect()
        }
    }
}
