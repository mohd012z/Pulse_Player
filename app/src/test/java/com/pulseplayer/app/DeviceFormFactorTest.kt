package com.pulseplayer.app

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceFormFactorTest {
    @Test fun televisionUsesFixedLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            DeviceFormFactor.orientationForTelevision(true)
        )
    }

    @Test fun mobileRespectsUserRotationSetting() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER,
            DeviceFormFactor.orientationForTelevision(false)
        )
    }
}
