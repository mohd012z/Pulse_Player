package com.pulseplayer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundPresetsTest {
    @Test fun flatDoesNotChangeAnyBand() {
        listOf(20.0, 100.0, 1000.0, 12000.0, 20000.0).forEach {
            assertEquals(0.0, SoundPresets.gainDb("Flat", it), 0.0)
        }
    }

    @Test fun warmBoostsLowFrequenciesRelativeToHigh() {
        assertTrue(SoundPresets.gainDb("Warm", 100.0) > SoundPresets.gainDb("Warm", 8000.0))
    }

    @Test fun vocalEmphasizesMidrange() {
        assertTrue(SoundPresets.gainDb("Vocal", 2000.0) > SoundPresets.gainDb("Vocal", 80.0))
    }

    @Test fun brightBoostsHighFrequencies() {
        assertTrue(SoundPresets.gainDb("Bright", 10000.0) > SoundPresets.gainDb("Bright", 100.0))
    }

    @Test fun customTvAndImmersivePresetsExist() {
        assertTrue(SoundPresets.names.contains("Immersive"))
        assertTrue(SoundPresets.names.contains("NovaStream TV"))
        assertTrue(SoundPresets.gainDb("NovaStream TV", 2300.0) > SoundPresets.gainDb("NovaStream TV", 550.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownPresetFailsClosed() {
        SoundPresets.gainDb("HiddenMode", 1000.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nanFrequencyRejected() {
        SoundPresets.gainDb("Flat", Double.NaN)
    }
}