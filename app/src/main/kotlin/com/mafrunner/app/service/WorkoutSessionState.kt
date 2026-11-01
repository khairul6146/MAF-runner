package com.mafrunner.app.service

import com.mafrunner.app.domain.TestPhase

data class WorkoutSessionState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val currentHr: Int? = null,
    val currentCadence: Int = 0,
    val lastStrikeTimestampMs: Long = 0L,
    val underSeconds: Long = 0L,
    val inZoneSeconds: Long = 0L,
    val overSeconds: Long = 0L,
    val isGuidedTest: Boolean = false,
    val testPhase: TestPhase = TestPhase.FREE_RUN,
    val phaseRemainingSeconds: Long = 0L,
    val targetTestDurationSeconds: Long = 0L,
    val recoveryPeakHr: Int? = null,
    val recoveryHr60: Int? = null,
    val recoveryHr120: Int? = null,
    val recoveryDrop60: Int? = null,
    val recoveryDrop120: Int? = null,
    val testUnderSeconds: Long = 0L,
    val testInZoneSeconds: Long = 0L,
    val testOverSeconds: Long = 0L,
    val testAvgHr: Int = 0,
    val testAvgCadence: Int = 0,
    val strapBatteryLevel: Int? = null
) {
    val inZonePercentage: Int
        get() = if (elapsedSeconds > 0) ((inZoneSeconds.toDouble() / elapsedSeconds) * 100).toInt() else 0

    val testInZonePercentage: Int
        get() {
            val total = testUnderSeconds + testInZoneSeconds + testOverSeconds
            return if (total > 0L) ((testInZoneSeconds.toDouble() / total) * 100).toInt() else 0
        }

    val formattedTime: String
        get() {
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val formattedPhaseCountdown: String
        get() {
            val minutes = phaseRemainingSeconds / 60
            val seconds = phaseRemainingSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}
