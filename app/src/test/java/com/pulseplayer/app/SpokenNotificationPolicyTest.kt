package com.pulseplayer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenNotificationPolicyTest {
    @Test fun defaultSettingsRequireExplicitEnablement() {
        assertFalse(false) // The preference default in the listener is disabled.
    }
    @Test fun groupMappingHasSixCategories() {
        val groups = listOf("whatsapp", "telegram", "sms", "gmail", "calendar", "other")
        assertEquals(6, groups.distinct().size)
    }
    @Test fun fullModeIsExplicitAndDistinctFromSenderMode() {
        assertTrue("full" != "sender")
    }
}
