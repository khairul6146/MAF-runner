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

    @Test
    fun testLastStrikeTimestamp() {
        val state = WorkoutSessionState(lastStrikeTimestampMs = 15000L)
        assertEquals(15000L, state.lastStrikeTimestampMs)
    }

    @Test
    fun testGuidedTestStateFields() {
        val state = WorkoutSessionState(
            isActive = true,
            isGuidedTest = true,
            testPhase = com.mafrunner.app.domain.TestPhase.TEST_STEADY,
            phaseRemainingSeconds = 1122L,
            targetTestDurationSeconds = 1800L,
            recoveryPeakHr = 145,
            recoveryHr60 = 118,
            recoveryHr120 = 99,
            recoveryDrop60 = 27,
            recoveryDrop120 = 46
        )
        assertEquals(true, state.isGuidedTest)
        assertEquals(com.mafrunner.app.domain.TestPhase.TEST_STEADY, state.testPhase)
        assertEquals("18:42", state.formattedPhaseCountdown)
        assertEquals(145, state.recoveryPeakHr)
        assertEquals(27, state.recoveryDrop60)
        assertEquals(46, state.recoveryDrop120)
    }

    @Test
    fun testStrapBatteryLevelField() {
        val state = WorkoutSessionState(strapBatteryLevel = 85)
        assertEquals(85, state.strapBatteryLevel)
        val defaultState = WorkoutSessionState()
        org.junit.Assert.assertNull(defaultState.strapBatteryLevel)
    }

    @Test
    fun testHrvAndRecentHrHistoryFields() {
        val defaultState = WorkoutSessionState()
        org.junit.Assert.assertNull(defaultState.currentHrvRmssd)
        org.junit.Assert.assertTrue(defaultState.recentHrHistory.isEmpty())

        val state = WorkoutSessionState(
            currentHrvRmssd = 42.5,
            recentHrHistory = listOf(130, 131, 132)
        )
        assertEquals(42.5, state.currentHrvRmssd)
        assertEquals(listOf(130, 131, 132), state.recentHrHistory)
    }
}
