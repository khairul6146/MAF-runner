package com.mafrunner.app.data.local

data class WorkoutEntity(
    val id: Long = 0L,
    val startTime: Long,
    val endTime: Long,
    val durationSec: Long,
    val underSec: Long,
    val inZoneSec: Long,
    val overSec: Long,
    val avgHr: Int,
    val maxHr: Int,
    val avgCadence: Int,
    val treadmillSpeed: Float? = null,
    val treadmillDistance: Float? = null,
    val isBenchmark: Boolean = false
) {
    val inZonePercentage: Int
        get() = if (durationSec > 0) ((inZoneSec.toDouble() / durationSec) * 100).toInt() else 0
}
