package com.mafrunner.app.cadence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class FootstrikePeakDetector(
    private val minRefractoryMs: Long = 220L, // Enforces physiological ceiling of ~272 SPM
    private var energyThreshold: Float = 0.5f
) {
    private val _cadenceSpm = MutableStateFlow(0)
    val cadenceSpm: StateFlow<Int> = _cadenceSpm.asStateFlow()

    private var lastStrikeTimestampMs = 0L
    private val strikeIntervals = ArrayDeque<Long>(8)

    fun setSensitivity(multiplier: Float) {
        // Multiplier 1..10 maps to energy threshold 0.9..0.1
        val clamped = multiplier.coerceIn(1f, 10f)
        energyThreshold = 1.0f - (clamped * 0.08f)
    }

    fun processSample(energy: Float, timestampMs: Long): Boolean {
        if (energy < energyThreshold) {
            return false
        }

        val elapsedSinceLast = timestampMs - lastStrikeTimestampMs
        if (lastStrikeTimestampMs != 0L && elapsedSinceLast < minRefractoryMs) {
            // Reject bounce reverberation
            return false
        }

        if (lastStrikeTimestampMs != 0L && elapsedSinceLast <= 1500L) { // Only count if within 40 SPM minimum
            strikeIntervals.addLast(elapsedSinceLast)
            if (strikeIntervals.size > 6) {
                strikeIntervals.removeFirst()
            }
            // Compute rolling average cadence (60000ms / avgIntervalMs)
            val avgInterval = strikeIntervals.average()
            if (avgInterval > 0) {
                val spm = (60000.0 / avgInterval).roundToInt()
                _cadenceSpm.value = spm.coerceIn(0, 240)
            }
        }

        lastStrikeTimestampMs = timestampMs
        return true
    }

    fun reset() {
        lastStrikeTimestampMs = 0L
        strikeIntervals.clear()
        _cadenceSpm.value = 0
    }
}
