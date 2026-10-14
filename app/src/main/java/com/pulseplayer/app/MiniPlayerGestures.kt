package com.pulseplayer.app

import kotlin.math.abs

/** Pure gesture classifier for the mini-player title strip.
 * Dragging the timeline is independent and never classified here.
 */
object MiniPlayerGestures {
    enum class Action { NEXT_TRACK, PREVIOUS_TRACK, IGNORE }

    fun classify(deltaX: Float, deltaY: Float, velocityX: Float, minDistancePx: Float): Action {
        if (!deltaX.isFinite() || !deltaY.isFinite() || !velocityX.isFinite() ||
            !minDistancePx.isFinite() || minDistancePx <= 0f) return Action.IGNORE
        if (abs(deltaX) < minDistancePx || abs(velocityX) < 250f ||
            abs(deltaY) > minDistancePx * 1.35f ||
            deltaX * velocityX <= 0f) return Action.IGNORE
        return if (deltaX < 0) Action.NEXT_TRACK else Action.PREVIOUS_TRACK
    }
}