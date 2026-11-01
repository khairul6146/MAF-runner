package com.mafrunner.app.domain

class TestProtocolEngine(
    val warmupDurationSec: Long = 0L,
    var targetTestDurationSec: Long = 1800L
) {
    var currentPhase: TestPhase = if (warmupDurationSec > 0L) TestPhase.WARMUP else TestPhase.TEST_STEADY
        private set

    var phaseRemainingSeconds: Long = if (warmupDurationSec > 0L) warmupDurationSec else targetTestDurationSec
        private set

    // Isolated steady-phase statistics
    var testUnderSeconds: Long = 0L
        private set
    var testInZoneSeconds: Long = 0L
        private set
    var testOverSeconds: Long = 0L
        private set

    private val steadyHrSamples = mutableListOf<Int>()
    private val steadyCadenceSamples = mutableListOf<Int>()

    val testAvgHr: Int
        get() = if (steadyHrSamples.isNotEmpty()) steadyHrSamples.average().toInt() else 0

    val testAvgCadence: Int
        get() = if (steadyCadenceSamples.isNotEmpty()) {
            val nonZero = steadyCadenceSamples.filter { it > 0 }
            if (nonZero.isNotEmpty()) nonZero.average().toInt() else 0
        } else 0

    val testInZonePercentage: Int
        get() {
            val totalSteady = testUnderSeconds + testInZoneSeconds + testOverSeconds
            return if (totalSteady > 0L) ((testInZoneSeconds.toDouble() / totalSteady) * 100).toInt() else 0
        }

    // Recovery statistics
    var recoveryPeakHr: Int? = null
        private set
    var recoveryHr60: Int? = null
        private set
    var recoveryHr120: Int? = null
        private set
    var recoveryDrop60: Int? = null
        private set
    var recoveryDrop120: Int? = null
        private set

    fun skipWarmup() {
        if (currentPhase == TestPhase.WARMUP) {
            currentPhase = TestPhase.TEST_STEADY
            phaseRemainingSeconds = targetTestDurationSec
        }
    }

    fun addTimeSeconds(deltaSeconds: Long) {
        if (currentPhase == TestPhase.TEST_STEADY) {
            val newRemaining = phaseRemainingSeconds + deltaSeconds
            if (newRemaining > 0L) {
                phaseRemainingSeconds = newRemaining
                targetTestDurationSec += deltaSeconds
            }
        }
    }

    fun startRecoveryEarly(currentHr: Int?) {
        if (currentPhase == TestPhase.WARMUP || currentPhase == TestPhase.TEST_STEADY) {
            transitionToRecovery(currentHr)
        }
    }

    private fun transitionToRecovery(currentHr: Int?) {
        currentPhase = TestPhase.RECOVERY
        phaseRemainingSeconds = 120L
        recoveryPeakHr = currentHr
    }

    fun tick(currentHr: Int?, currentCadence: Int, zone: MafZone?): TestPhase {
        when (currentPhase) {
            TestPhase.FREE_RUN -> {}
            TestPhase.WARMUP -> {
                if (phaseRemainingSeconds > 1L) {
                    phaseRemainingSeconds--
                } else {
                    currentPhase = TestPhase.TEST_STEADY
                    phaseRemainingSeconds = targetTestDurationSec
                }
            }
            TestPhase.TEST_STEADY -> {
                if (zone != null) {
                    when (zone) {
                        MafZone.BELOW_FLOOR -> testUnderSeconds++
                        MafZone.MAF_TARGET -> testInZoneSeconds++
                        MafZone.OVER_CEILING -> testOverSeconds++
                    }
                }
                if (currentHr != null && currentHr > 0) {
                    steadyHrSamples.add(currentHr)
                }
                if (currentCadence > 0) {
                    steadyCadenceSamples.add(currentCadence)
                }

                if (phaseRemainingSeconds > 1L) {
                    phaseRemainingSeconds--
                } else {
                    transitionToRecovery(currentHr)
                }
            }
            TestPhase.RECOVERY -> {
                if (phaseRemainingSeconds > 1L) {
                    phaseRemainingSeconds--
                    val elapsedRecovery = 120L - phaseRemainingSeconds
                    if (elapsedRecovery == 60L && currentHr != null) {
                        recoveryHr60 = currentHr
                        recoveryPeakHr?.let { peak ->
                            recoveryDrop60 = peak - currentHr
                        }
                    }
                } else {
                    phaseRemainingSeconds = 0L
                    currentPhase = TestPhase.COMPLETE
                    if (currentHr != null) {
                        recoveryHr120 = currentHr
                        recoveryPeakHr?.let { peak ->
                            recoveryDrop120 = peak - currentHr
                        }
                    }
                }
            }
            TestPhase.COMPLETE -> {}
        }
        return currentPhase
    }
}
