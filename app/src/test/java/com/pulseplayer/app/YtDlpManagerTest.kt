package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class YtDlpManagerTest {
    @Test fun supportedUrls() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=abcDEF12345",
            "https://m.youtube.com/watch?v=abcDEF12345",
            "https://www.youtube.com/shorts/AbCdEfGhIjK",
            "https://www.youtube.com/playlist?list=PL1234567890123",
            "https://youtube.com/amp/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90",
        ).forEach { assertTrue("Expected supported: $it", YtDlpManager.isSupportedUrl(it)) }
    }

    @Test fun rejectedUrls() {
        listOf(
            "", "   ", "not a url",
            "https://vimeo.com/12345",
            "https://soundcloud.com/x/y",
            "ftp://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/toolongtobeanid",
            "https://example.com/youtube.com/watch?v=x",
            "https://www.joox.com/single/123",
            "https://youtube.com.evil.org/watch?v=dQw4w9WgXcQ",
        ).forEach { assertFalse("Expected rejected: $it", YtDlpManager.isSupportedUrl(it)) }
    }

    @Test fun formatNormalization() {
        assertEquals("MP3", YtDlpManager.normalizeFormat("mp3"))
        assertEquals("MP3", YtDlpManager.normalizeFormat("  mp3 "))
        assertEquals("FLAC", YtDlpManager.normalizeFormat("flac"))
        assertEquals("M4A", YtDlpManager.normalizeFormat("m4a"))
        assertEquals("OPUS", YtDlpManager.normalizeFormat("Opus"))
        assertEquals("MP3", YtDlpManager.normalizeFormat("wave"))
        assertEquals("MP3", YtDlpManager.normalizeFormat(""))
    }

    @Test fun commandForMp3() {
        val dir = File("/sdcard/Download/PulsePlayer")
        val cmd = YtDlpManager.buildCommand("/data/ytdlp/yt-dlp_aarch64",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ", dir, "MP3")
        assertEquals("/data/ytdlp/yt-dlp_aarch64", cmd[0])
        assertTrue(cmd.contains("--extract-audio"))
        assertTrue(cmd.contains("--audio-format"))
        assertEquals("mp3", cmd[cmd.indexOf("--audio-format") + 1])
        assertEquals("192K", cmd[cmd.indexOf("--audio-quality") + 1])
        assertEquals("%(title)s [%(id)s].%(ext)s",
            File(cmd[cmd.indexOf("--output") + 1]).name)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", cmd.last())
    }

    @Test fun commandForFlacHasNoQuality() {
        val dir = File("/sdcard/Download/PulsePlayer")
        val flac = YtDlpManager.buildCommand("/b", "https://youtu.be/x1y2z3A4B5C", dir, "FLAC")
        assertEquals("flac", flac[flac.indexOf("--audio-format") + 1])
        assertFalse(flac.contains("--audio-quality"))
    }

    @Test fun commandForM4aAndOpus() {
        val dir = File("/d")
        val m4a = YtDlpManager.buildCommand("/b", "https://youtu.be/x1y2z3A4B5C", dir, "m4a")
        assertEquals("m4a", m4a[m4a.indexOf("--audio-format") + 1])
        assertEquals("192K", m4a[m4a.indexOf("--audio-quality") + 1])
        val opus = YtDlpManager.buildCommand("/b", "https://youtu.be/x1y2z3A4B5C", dir, "OPUS")
        assertEquals("opus", opus[opus.indexOf("--audio-format") + 1])
        assertEquals("160K", opus[opus.indexOf("--audio-quality") + 1])
    }

    @Test fun thumbnailOptionIsOptional() {
        val dir = File("/d")
        val plain = YtDlpManager.buildCommand("/b", "https://youtu.be/x1y2z3A4B5C", dir, "MP3")
        assertFalse(plain.contains("--write-thumbnail"))
        val withThumb = YtDlpManager.buildCommand("/b", "https://youtu.be/x1y2z3A4B5C", dir, "MP3", writeThumbnail = true)
        assertTrue(withThumb.contains("--write-thumbnail"))
        assertTrue(withThumb.contains("--convert-thumbnails"))
        assertEquals("jpg", withThumb[withThumb.indexOf("--convert-thumbnails") + 1])
    }

    @Test fun progressParsingMatchesRealYtDlpOutput() {
        // Real yt-dlp lines captured from a live run (2026.08.19).
        val p = YtDlpManager.parseProgress("[download]  42.7% of  246.27KiB at  878.57KiB/s ETA 00:12")
        assertNotNull(p)
        assertEquals(42.7, p!!.percent, 0.001)
        assertEquals("878.57KiB/s", p.speed)
        assertEquals(12L, p.etaSeconds)

        val p2 = YtDlpManager.parseProgress("[download]   1.2% of  246.27KiB at    1.83MiB/s ETA 01:05")
        assertNotNull(p2)
        assertEquals("1.83MiB/s", p2!!.speed)
        assertEquals(65L, p2.etaSeconds)

        val p3 = YtDlpManager.parseProgress("[download] 100% of  246.27KiB in 00:00:01 at 959.37KiB/s")
        assertEquals("959.37KiB/s", p3!!.speed)
        assertNull(p3.etaSeconds) // "in HH:MM:SS", no ETA

        assertNull(YtDlpManager.parseProgress("[info] Looking for similar titles"))
        assertNull(YtDlpManager.parseProgress("garbage line"))
        assertNull(YtDlpManager.parseProgress(""))
    }

    @Test fun assetNameMapping() {
        assertEquals("yt-dlp_aarch64", YtDlpManager.assetNameForAbi("arm64-v8a"))
        assertEquals("yt-dlp_aarch64", YtDlpManager.assetNameForAbi("arm64"))
        assertThrows(IllegalArgumentException::class.java) { YtDlpManager.assetNameForAbi("armeabi-v7a") }
        assertThrows(IllegalArgumentException::class.java) { YtDlpManager.assetNameForAbi("armeabi") }
    }
}
