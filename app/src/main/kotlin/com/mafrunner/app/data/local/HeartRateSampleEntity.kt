package com.mafrunner.app.data.local

data class HeartRateSampleEntity(
    val id: Long = 0L,
    val workoutId: Long,
    val timestamp: Long,
    val hr: Int,
    val cadence: Int
)
