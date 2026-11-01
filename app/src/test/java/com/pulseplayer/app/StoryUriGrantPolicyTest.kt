package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

class StoryUriGrantPolicyTest {
    @Test fun persistsOnlyTheReadPermissionReturnedByPicker() {
        var calls = 0
        assertTrue(StoryUriGrantPolicy.persistReadGrant(3, 1) {
            assertEquals(1, it)
            calls++
        })
        assertEquals(1, calls)
    }

    @Test fun rejectsMissingReadGrantWithoutCallingResolver() {
        var calls = 0
        assertFalse(StoryUriGrantPolicy.persistReadGrant(2, 1) { calls++ })
        assertFalse(StoryUriGrantPolicy.persistReadGrant(0, 1) { calls++ })
        assertFalse(StoryUriGrantPolicy.persistReadGrant(1, 0) { calls++ })
        assertEquals(0, calls)
    }

    @Test fun revokedGrantFailsClosed() {
        assertFalse(StoryUriGrantPolicy.persistReadGrant(1, 1) {
            throw SecurityException("Grant revoked")
        })
    }
}
