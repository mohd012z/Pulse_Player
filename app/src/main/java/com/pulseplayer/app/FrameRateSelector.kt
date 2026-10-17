package com.pulseplayer.app

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Select a same-resolution refresh mode that displays an integer number of
 * refreshes for each source frame. Source FPS must be user-specified or measured;
 * never infer it from YouTube's page type.
 */
internal object FrameRateSelector {
    data class Mode(val id: Int, val width: Int, val height: Int, val hz: Float)

    fun choose(modes: List<Mode>, currentWidth: Int, currentHeight: Int,
               contentFps: Float): Mode? {
        if (!contentFps.isFinite() || contentFps !in 23f..120f) return null
        return modes.filter {
            it.id > 0 && it.width == currentWidth && it.height == currentHeight &&
                it.hz.isFinite() && it.hz + 0.15f >= contentFps &&
                abs(it.hz / contentFps - (it.hz / contentFps).roundToInt()) < 0.012f
        }.minByOrNull { it.hz }
    }
}
