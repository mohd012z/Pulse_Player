package com.pulseplayer.inspector

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Network-independent parsing primitives for Phase 2. No downloads are initiated here. */
object BoundedManifestInspector {
    const val MAX_BYTES = 512 * 1024
    data class Variant(val bandwidth: Long?, val resolution: String?, val codecs: String?, val uri: String)
    data class HlsInfo(val variants: List<Variant>, val isMediaPlaylist: Boolean)

    /** Reads at most MAX_BYTES+1 bytes, then rejects oversized responses. */
    fun readManifest(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = MAX_BYTES + 1 - output.size()
            if (remaining <= 0) throw IOException("Manifest exceeds 512 KiB limit")
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            if (output.size() > MAX_BYTES) throw IOException("Manifest exceeds 512 KiB limit")
        }
        return output.toString(Charsets.UTF_8.name())
    }

    fun parseHls(text: String): HlsInfo {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        require(lines.firstOrNull() == "#EXTM3U") { "Not an HLS manifest" }
        val variants = mutableListOf<Variant>()
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue
            val uri = lines.drop(i + 1).firstOrNull { !it.startsWith("#") }
                ?: throw IllegalArgumentException("Missing variant URI")
            val attrs = parseAttributes(line.substringAfter(':'))
            variants.add(Variant(attrs["BANDWIDTH"]?.toLongOrNull(), attrs["RESOLUTION"], attrs["CODECS"], uri))
            if (variants.size > 1024) throw IllegalArgumentException("Too many variants")
        }
        return HlsInfo(variants, lines.any { it.startsWith("#EXTINF:") })
    }

    /** Comma-separated HLS attributes with quoted CODECS values. */
    private fun parseAttributes(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var pos = 0
        while (pos < text.length) {
            val equal = text.indexOf('=', pos)
            if (equal < 0) break
            val key = text.substring(pos, equal).trim().uppercase()
            pos = equal + 1
            val value: String
            if (pos < text.length && text[pos] == '"') {
                val end = text.indexOf('"', pos + 1)
                if (end < 0) break
                value = text.substring(pos + 1, end)
                pos = end + 1
            } else {
                val end = text.indexOf(',', pos).let { if (it < 0) text.length else it }
                value = text.substring(pos, end).trim()
                pos = end
            }
            result[key] = value
            if (pos < text.length && text[pos] == ',') pos++
        }
        return result
    }
}
