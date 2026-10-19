package com.pulseplayer.inspector

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class BoundedManifestInspectorTest {
    @Test fun acceptsExactly512KiB() {
        val input = ByteArrayInputStream(ByteArray(BoundedManifestInspector.MAX_BYTES) { 'a'.code.toByte() })
        assertEquals(BoundedManifestInspector.MAX_BYTES, BoundedManifestInspector.readManifest(input).length)
    }
    @Test fun rejectsOversizedManifest() {
        val input = ByteArrayInputStream(ByteArray(BoundedManifestInspector.MAX_BYTES + 1))
        try {
            BoundedManifestInspector.readManifest(input)
            fail("Expected size rejection")
        } catch (_: IOException) {}
    }
    @Test fun parsesQuotedCodecLists() {
        val text = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2200000,RESOLUTION=1280x720,CODECS=\"avc1.42e01e,mp4a.40.2\"\n720.m3u8"
        val result = BoundedManifestInspector.parseHls(text)
        assertEquals(1, result.variants.size)
        assertEquals("avc1.42e01e,mp4a.40.2", result.variants.single().codecs)
        assertEquals(2200000L, result.variants.single().bandwidth)
    }
    @Test fun rejectsNonHls() {
        try {
            BoundedManifestInspector.parseHls("<html>Not media</html>")
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {}
    }
}
