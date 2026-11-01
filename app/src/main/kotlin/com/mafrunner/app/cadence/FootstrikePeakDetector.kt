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

    private val _liveEnergy = MutableStateFlow(0f)
    val liveEnergy: StateFlow<Float> = _liveEnergy.asStateFlow()

    private val _currentThreshold = MutableStateFlow(energyThreshold)
    val currentThreshold: StateFlow<Float> = _currentThreshold.asStateFlow()

    private val _lastStrikeTimestampMsFlow = MutableStateFlow(0L)
    val lastStrikeTimestampMsFlow: StateFlow<Long> = _lastStrikeTimestampMsFlow.asStateFlow()

    private var lastStrikeTimestampMs = 0L
    private val strikeIntervals = ArrayDeque<Long>(8)
    private var noiseFloor: Float = 0.1f

    fun setSensitivity(multiplier: Float) {
        // Multiplier 1..10 maps to energy threshold 0.9..0.1
        val clamped = multiplier.coerceIn(1f, 10f)
        energyThreshold = 1.0f - (clamped * 0.08f)
        _currentThreshold.value = energyThreshold
    }

    fun processSample(energy: Float, timestampMs: Long): Boolean {
        _liveEnergy.value = energy
        // Adaptive noise floor tracking prevents steady treadmill motor hum from false triggering
        val dynamicThreshold = maxOf(energyThreshold, noiseFloor * 1.35f)
        _currentThreshold.value = dynamicThreshold
        noiseFloor = (noiseFloor * 0.95f) + (energy * 0.05f)

        if (energy < dynamicThreshold) {
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
        _lastStrikeTimestampMsFlow.value = timestampMs
        return true
    }

    fun reset() {
        lastStrikeTimestampMs = 0L
        strikeIntervals.clear()
        _cadenceSpm.value = 0
        _liveEnergy.value = 0f
        _lastStrikeTimestampMsFlow.value = 0L
        noiseFloor = 0.1f
    }
}
