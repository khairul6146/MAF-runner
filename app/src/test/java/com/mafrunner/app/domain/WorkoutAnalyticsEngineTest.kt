package com.mafrunner.app.domain

import com.mafrunner.app.data.local.WorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutAnalyticsEngineTest {

    private val engine = WorkoutAnalyticsEngine()

    @Test
    fun testEmptyWorkoutsReturnZeroValues() {
        val analytics = engine.computeAnalytics(emptyList(), AnalyticsPeriod.WEEK, nowMs = 1000000L)
        assertEquals(0, analytics.totalWorkouts)
        assertEquals(0L, analytics.totalDurationSec)
        assertEquals(0f, analytics.totalDistanceKm, 0.01f)
        assertEquals(0, analytics.overallInZonePercentage)
        assertNull(analytics.speedProgressionDelta)
    }

    @Test
    fun testTimeWindowFiltering() {
        val now = 100_000_000L
        val oneDay = 24L * 60 * 60 * 1000L
        val w1 = createWorkout(id = 1L, startTime = now - (2 * oneDay), duration = 1800, inZone = 1620, speed = 8.0f) // 2 days ago (in Week)
        val w2 = createWorkout(id = 2L, startTime = now - (10 * oneDay), duration = 2400, inZone = 2000, speed = 8.5f) // 10 days ago (in Month, not Week)
        val w3 = createWorkout(id = 3L, startTime = now - (45 * oneDay), duration = 3000, inZone = 2400, speed = 9.0f) // 45 days ago (in Quarter, not Month)

        val list = listOf(w1, w2, w3)

        val weekAnalytics = engine.computeAnalytics(list, AnalyticsPeriod.WEEK, nowMs = now)
        assertEquals(1, weekAnalytics.totalWorkouts)
        assertEquals(1800L, weekAnalytics.totalDurationSec)

        val monthAnalytics = engine.computeAnalytics(list, AnalyticsPeriod.MONTH, nowMs = now)
        assertEquals(2, monthAnalytics.totalWorkouts)
        assertEquals(4200L, monthAnalytics.totalDurationSec)

        val quarterAnalytics = engine.computeAnalytics(list, AnalyticsPeriod.QUARTER, nowMs = now)
        assertEquals(3, quarterAnalytics.totalWorkouts)
        assertEquals(7200L, quarterAnalytics.totalDurationSec)
    }

    @Test
    fun testSpeedProgressionCalculation() {
        val now = 100_000_000L
        val oneDay = 24L * 60 * 60 * 1000L
        val w1 = createWorkout(id = 1L, startTime = now - (5 * oneDay), duration = 1800, inZone = 1600, speed = 8.0f, isBenchmark = true)
        val w2 = createWorkout(id = 2L, startTime = now - (1 * oneDay), duration = 1800, inZone = 1700, speed = 8.6f, isBenchmark = true)

        val analytics = engine.computeAnalytics(listOf(w1, w2), AnalyticsPeriod.WEEK, nowMs = now)
        assertNotNull(analytics.speedProgressionDelta)
        assertEquals(0.6f, analytics.speedProgressionDelta ?: 0f, 0.01f)
    }

    @Test
    fun testRecoveryDropAverages() {
        val now = 100_000_000L
        val w1 = createWorkout(1L, now - 1000L, 1800, 1600, 8.0f, true).copy(
            isGuidedTest = true,
            recoveryDrop60 = 24,
            recoveryDrop120 = 40
        )
        val w2 = createWorkout(2L, now - 500L, 1800, 1700, 8.5f, true).copy(
            isGuidedTest = true,
            recoveryDrop60 = 28,
            recoveryDrop120 = 48
        )
        val analytics = engine.computeAnalytics(listOf(w1, w2), AnalyticsPeriod.WEEK, nowMs = now)
        assertEquals(26, analytics.avgRecoveryDrop60)
        assertEquals(44, analytics.avgRecoveryDrop120)
    }

    private fun createWorkout(
        id: Long,
        startTime: Long,
        duration: Long,
        inZone: Long,
        speed: Float?,
        isBenchmark: Boolean = false
    ): WorkoutEntity {
        return WorkoutEntity(
            id = id,
            startTime = startTime,
            endTime = startTime + (duration * 1000),
            durationSec = duration,
            underSec = (duration - inZone) / 2,
            inZoneSec = inZone,
            overSec = (duration - inZone) / 2,
            avgHr = 138,
            maxHr = 143,
            avgCadence = 180,
            treadmillSpeed = speed,
            treadmillDistance = speed?.let { (it * (duration / 3600f)) },
            isBenchmark = isBenchmark
        )
    }
}
