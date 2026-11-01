package com.mafrunner.app.domain

import com.mafrunner.app.data.local.WorkoutEntity

enum class AnalyticsPeriod(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    QUARTER("Quarter"),
    ALL("All Time")
}

data class PeriodAnalytics(
    val period: AnalyticsPeriod,
    val totalWorkouts: Int,
    val totalDurationSec: Long,
    val totalDistanceKm: Float,
    val overallInZonePercentage: Int,
    val avgHeartRate: Int,
    val avgCadence: Int,
    val underSec: Long,
    val inZoneSec: Long,
    val overSec: Long,
    val speedProgressionDelta: Float?,
    val benchmarkCount: Int
)

class WorkoutAnalyticsEngine {

    fun computeAnalytics(
        workouts: List<WorkoutEntity>,
        period: AnalyticsPeriod,
        nowMs: Long = System.currentTimeMillis()
    ): PeriodAnalytics {
        val windowMs = when (period) {
            AnalyticsPeriod.WEEK -> 7L * 24 * 60 * 60 * 1000L
            AnalyticsPeriod.MONTH -> 30L * 24 * 60 * 60 * 1000L
            AnalyticsPeriod.QUARTER -> 90L * 24 * 60 * 60 * 1000L
            AnalyticsPeriod.ALL -> Long.MAX_VALUE
        }
        val startTimeCutoff = if (windowMs == Long.MAX_VALUE) 0L else nowMs - windowMs
        val filtered = workouts.filter { it.startTime >= startTimeCutoff }

        if (filtered.isEmpty()) {
            return PeriodAnalytics(
                period = period,
                totalWorkouts = 0,
                totalDurationSec = 0L,
                totalDistanceKm = 0f,
                overallInZonePercentage = 0,
                avgHeartRate = 0,
                avgCadence = 0,
                underSec = 0L,
                inZoneSec = 0L,
                overSec = 0L,
                speedProgressionDelta = null,
                benchmarkCount = 0
            )
        }

        val totalDuration = filtered.sumOf { it.durationSec }
        val totalUnder = filtered.sumOf { it.underSec }
        val totalInZone = filtered.sumOf { it.inZoneSec }
        val totalOver = filtered.sumOf { it.overSec }
        val totalDist = filtered.mapNotNull { it.treadmillDistance }.sum()
        val inZonePct = if (totalDuration > 0) ((totalInZone.toDouble() / totalDuration) * 100).toInt() else 0

        val validHr = filtered.filter { it.avgHr > 0 }
        val avgHr = if (validHr.isNotEmpty()) validHr.map { it.avgHr }.average().toInt() else 0

        val validCadence = filtered.filter { it.avgCadence > 0 }
        val avgCadence = if (validCadence.isNotEmpty()) validCadence.map { it.avgCadence }.average().toInt() else 0

        val benchmarks = filtered.filter { it.isBenchmark && it.treadmillSpeed != null }
            .sortedBy { it.startTime }
        val speedProgression = if (benchmarks.size >= 2) {
            val earliest = benchmarks.first().treadmillSpeed ?: 0f
            val latest = benchmarks.last().treadmillSpeed ?: 0f
            latest - earliest
        } else null

        return PeriodAnalytics(
            period = period,
            totalWorkouts = filtered.size,
            totalDurationSec = totalDuration,
            totalDistanceKm = totalDist,
            overallInZonePercentage = inZonePct,
            avgHeartRate = avgHr,
            avgCadence = avgCadence,
            underSec = totalUnder,
            inZoneSec = totalInZone,
            overSec = totalOver,
            speedProgressionDelta = speedProgression,
            benchmarkCount = benchmarks.size
        )
    }
}
