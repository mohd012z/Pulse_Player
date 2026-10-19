package com.pulseplayer.inspector

import org.junit.Assert.*
import org.junit.Test

class ProgressiveMediaInspectorTest {
    @Test fun rejectsWatchPagesWithoutNetwork() {
        val result = ProgressiveMediaInspector.inspect("https://youtube.com/watch?v=dQw4w9WgXcQ")
        assertNotNull(result.error)
        assertNull(result.containerMimeType)
    }
    @Test fun rejectsLocalHostsWithoutNetwork() {
        val result = ProgressiveMediaInspector.inspect("https://127.0.0.1/movie.mp4")
        assertNotNull(result.error)
    }
    @Test fun rejectsNonHttpsWithoutNetwork() {
        val result = ProgressiveMediaInspector.inspect("http://example.com/movie.mp4")
        assertNotNull(result.error)
    }
}
