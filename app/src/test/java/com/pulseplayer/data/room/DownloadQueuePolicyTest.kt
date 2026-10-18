package com.pulseplayer.data.room

import org.junit.Assert.*
import org.junit.Test

class DownloadQueuePolicyTest {
    @Test fun completedIsImmutable() {
        DownloadState.values().forEach { assertFalse(DownloadQueuePolicy.canTransition(DownloadState.COMPLETED, it)) }
    }
    @Test fun failedMayRetryButCannotCompleteDirectly() {
        assertTrue(DownloadQueuePolicy.canTransition(DownloadState.FAILED, DownloadState.PENDING))
        assertFalse(DownloadQueuePolicy.canTransition(DownloadState.FAILED, DownloadState.COMPLETED))
    }
    @Test fun clampsProgress() {
        assertEquals(1f, DownloadQueuePolicy.safeProgress(4f), 0f)
        assertEquals(0f, DownloadQueuePolicy.safeProgress(Float.NaN), 0f)
    }
    @Test fun sanitizesNames() {
        assertEquals("hello_world.mp4", DownloadQueuePolicy.safeDisplayName("hello/world", "mp4"))
        try { DownloadQueuePolicy.safeDisplayName("file", "exe"); fail("Expected rejection") }
        catch (_: IllegalArgumentException) {}
    }
}
