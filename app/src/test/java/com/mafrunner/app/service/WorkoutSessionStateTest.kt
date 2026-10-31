package com.mafrunner.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSessionStateTest {
    @Test
    fun testStateTransitions() {
        val state = WorkoutSessionState(
            isActive = true,
            isPaused = false,
            elapsedSeconds = 120,
            currentHr = 138,
            currentCadence = 180,
            underSeconds = 10,
            inZoneSeconds = 100,
            overSeconds = 10
        )
        val paused = state.copy(isPaused = true)
        assertEquals(true, paused.isPaused)
        assertEquals(120, paused.elapsedSeconds)
        assertEquals(138, paused.currentHr)
    }

    @Test
    fun testFormatting() {
        val state = WorkoutSessionState(elapsedSeconds = 125)
        assertEquals("02:05", state.formattedTime)
    }
}
