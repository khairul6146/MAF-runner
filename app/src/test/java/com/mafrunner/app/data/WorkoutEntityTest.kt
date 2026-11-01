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
}
