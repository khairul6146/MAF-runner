package com.mafrunner.app.service

data class WorkoutSessionState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val currentHr: Int? = null,
    val currentCadence: Int = 0,
    val underSeconds: Long = 0L,
    val inZoneSeconds: Long = 0L,
    val overSeconds: Long = 0L
) {
    val inZonePercentage: Int
        get() = if (elapsedSeconds > 0) ((inZoneSeconds.toDouble() / elapsedSeconds) * 100).toInt() else 0

    val formattedTime: String
        get() {
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}
