package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

class CrossfadeTransitionTest {
    @Test fun lifecycle() {
        val machine = CrossfadeTransition()
        assertEquals(CrossfadeTransition.State.IDLE, machine.state)
        assertEquals(CrossfadeTransition.State.PREPARING, machine.dispatch(CrossfadeTransition.Event.PREPARE))
        val token = machine.generation
        assertTrue(machine.isCurrent(token))
        assertEquals(CrossfadeTransition.State.FADING, machine.dispatch(CrossfadeTransition.Event.READY))
        assertEquals(CrossfadeTransition.State.IDLE, machine.dispatch(CrossfadeTransition.Event.COMPLETE))
        assertFalse(machine.isCurrent(token))
    }
    @Test fun cancelledPreparationCannotResume() {
        val machine = CrossfadeTransition()
        machine.dispatch(CrossfadeTransition.Event.PREPARE)
        val stale = machine.generation
        machine.dispatch(CrossfadeTransition.Event.CANCEL)
        assertFalse(machine.isCurrent(stale))
        assertEquals(CrossfadeTransition.State.IDLE, machine.dispatch(CrossfadeTransition.Event.READY))
    }
    @Test fun staleGenerationCannotControlNewTransition() {
        val machine = CrossfadeTransition()
        machine.dispatch(CrossfadeTransition.Event.PREPARE)
        val old = machine.generation
        machine.dispatch(CrossfadeTransition.Event.ERROR)
        machine.dispatch(CrossfadeTransition.Event.PREPARE)
        assertFalse(machine.isCurrent(old))
        assertTrue(machine.isCurrent(machine.generation))
    }
}
