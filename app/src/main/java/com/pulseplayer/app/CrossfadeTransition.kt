package com.pulseplayer.app

/**
 * Pure transition lifecycle for coordinating two independent players.
 * No playback is started by this class; its caller owns player creation and release.
 */
class CrossfadeTransition {
    enum class State { IDLE, PREPARING, FADING }
    enum class Event { PREPARE, READY, TICK, CANCEL, COMPLETE, ERROR }
    var state: State = State.IDLE
        private set
    var generation: Long = 0
        private set

    fun dispatch(event: Event): State {
        state = when (event) {
            Event.PREPARE -> {
                generation++
                State.PREPARING
            }
            Event.READY -> if (state == State.PREPARING) State.FADING else state
            Event.TICK -> state
            Event.CANCEL, Event.ERROR, Event.COMPLETE -> {
                generation++
                State.IDLE
            }
        }
        return state
    }

    fun isCurrent(token: Long): Boolean = token == generation && state != State.IDLE
}
