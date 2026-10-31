package com.pulseplayer.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRemoteDispatchContractTest {
    @Test
    fun exactlyOneMediaKeyDispatcherIsDeclared() {
        val relative = "src/main/java/com/pulseplayer/app/MainActivity.kt"
        val source = sequenceOf(File(relative), File("app/$relative"))
            .firstOrNull { it.isFile }
            ?: error("MainActivity.kt not found in the Gradle project")
        val text = source.readText()
        val overrides = Regex("""\boverride\s+fun\s+dispatchKeyEvent\s*\(""")
            .findAll(text).count()
        assertEquals("A duplicate override breaks Kotlin compilation", 1, overrides)
        assertTrue("TV media controls must remain wired", text.contains("KEYCODE_MEDIA_PLAY_PAUSE"))
        assertTrue("Unhandled D-pad keys must reach Android", text.contains("return super.dispatchKeyEvent(event)"))
    }
}
