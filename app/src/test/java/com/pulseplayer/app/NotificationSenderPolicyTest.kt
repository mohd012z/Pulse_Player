package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

class NotificationSenderPolicyTest {
    @Test fun rejectsPotentiallySensitiveTitles() {
        assertNull(NotificationSenderPolicy.safeTitle(null))
        assertNull(NotificationSenderPolicy.safeTitle(""))
        assertNull(NotificationSenderPolicy.safeTitle("OTP: 123456"))
        assertNull(NotificationSenderPolicy.safeTitle("https://example.com"))
        assertNull(NotificationSenderPolicy.safeTitle("line one\nline two"))
        assertNull(NotificationSenderPolicy.safeTitle("Verification code 123456"))
        assertNull(NotificationSenderPolicy.safeTitle("x".repeat(61)))
    }
    @Test fun acceptsShortSenderNames() {
        assertEquals("Ahmad", NotificationSenderPolicy.safeTitle(" Ahmad "))
    }
}
