package com.pulseplayer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniPlayerGesturesTest {
    @Test fun swipeLeftGoesToNext() {
        assertEquals(MiniPlayerGestures.Action.NEXT_TRACK,
            MiniPlayerGestures.classify(-90f, 3f, -500f, 55f))
    }

    @Test fun swipeRightGoesToPrevious() {
        assertEquals(MiniPlayerGestures.Action.PREVIOUS_TRACK,
            MiniPlayerGestures.classify(90f, 3f, 500f, 55f))
    }

    @Test fun ignoresVerticalAndSlowGestures() {
        assertEquals(MiniPlayerGestures.Action.IGNORE,
            MiniPlayerGestures.classify(90f, 200f, 500f, 55f))
        assertEquals(MiniPlayerGestures.Action.IGNORE,
            MiniPlayerGestures.classify(90f, 5f, 20f, 55f))
    }
}