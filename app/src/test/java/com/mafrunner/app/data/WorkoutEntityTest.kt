package com.mafrunner.app.data

import com.mafrunner.app.data.local.WorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutEntityTest {
    @Test
    fun testZonePercentageCalculation() {
        val workout = WorkoutEntity(
            id = 1L,
            startTime = 1000L,
            endTime = 2000L,
            durationSec = 1000,
            underSec = 100,
            inZoneSec = 850,
            overSec = 50,
            avgHr = 138,
            maxHr = 143,
            avgCadence = 180,
            treadmillSpeed = 8.5f,
            treadmillDistance = 4.95f,
            isBenchmark = true
        )
        assertEquals(85, workout.inZonePercentage)
    }

    @Test
    fun testEntityCopyAndUpdating() {
        val original = WorkoutEntity(
            id = 10L,
            startTime = 5000L,
            endTime = 7000L,
            durationSec = 2000,
            underSec = 200,
            inZoneSec = 1600,
            overSec = 200,
            avgHr = 140,
            maxHr = 145,
            avgCadence = 175,
            treadmillSpeed = null,
            treadmillDistance = null,
            isBenchmark = false
        )
        val updated = original.copy(
            treadmillSpeed = 9.2f,
            treadmillDistance = 5.1f,
            isBenchmark = true
        )
        assertEquals(9.2f, updated.treadmillSpeed ?: 0f, 0.01f)
        assertEquals(5.1f, updated.treadmillDistance ?: 0f, 0.01f)
        assertEquals(true, updated.isBenchmark)
    }

    @Test
    fun testGuidedTestAndRecoveryFields() {
        val testWorkout = WorkoutEntity(
            id = 20L,
            startTime = 10000L,
            endTime = 12000L,
            durationSec = 2100,
            underSec = 50,
            inZoneSec = 2000,
            overSec = 50,
            avgHr = 141,
            maxHr = 145,
            avgCadence = 178,
            isGuidedTest = true,
            targetDurationSec = 1800L,
            recoveryPeakHr = 144,
            recoveryHr60 = 118,
            recoveryHr120 = 98,
            recoveryDrop60 = 26,
            recoveryDrop120 = 46
        )
        assertEquals(true, testWorkout.isGuidedTest)
        assertEquals(1800L, testWorkout.targetDurationSec)
        assertEquals(144, testWorkout.recoveryPeakHr)
        assertEquals(118, testWorkout.recoveryHr60)
        assertEquals(98, testWorkout.recoveryHr120)
        assertEquals(26, testWorkout.recoveryDrop60)
        assertEquals(46, testWorkout.recoveryDrop120)
    }
}
