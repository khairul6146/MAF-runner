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
}
