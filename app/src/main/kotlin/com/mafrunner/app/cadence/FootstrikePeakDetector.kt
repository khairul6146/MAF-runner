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
    private var noiseFloor: Float = 0.05f
    private var sensitivityMultiplier: Float = 5f

    fun setSensitivity(multiplier: Float) {
        val clamped = multiplier.coerceIn(1f, 10f)
        sensitivityMultiplier = clamped
        val clearance = 0.04f + ((10f - clamped) * 0.04f)
        energyThreshold = clearance
        _currentThreshold.value = (noiseFloor + clearance).coerceIn(0.06f, 0.95f)
    }

    fun processSample(energy: Float, timestampMs: Long): Boolean {
        _liveEnergy.value = energy
        // Auto-baseline floating threshold above treadmill motor noise
        val clearance = 0.04f + ((10f - sensitivityMultiplier) * 0.04f)
        val dynamicThreshold = (noiseFloor + clearance).coerceIn(0.06f, 0.95f)
        _currentThreshold.value = dynamicThreshold

        // Ambient noise floor slowly converges to steady background hum
        noiseFloor = (noiseFloor * 0.96f) + (energy * 0.04f)

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
        noiseFloor = 0.05f
    }
}
