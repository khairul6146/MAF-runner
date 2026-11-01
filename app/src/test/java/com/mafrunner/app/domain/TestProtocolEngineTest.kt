package com.mafrunner.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TestProtocolEngineTest {

    @Test
    fun testWarmupToSteadyToRecoveryLifecycle() {
        val engine = TestProtocolEngine(
            warmupDurationSec = 300L,
            targetTestDurationSec = 1800L
        )

        assertEquals(TestPhase.WARMUP, engine.currentPhase)
        assertEquals(300L, engine.phaseRemainingSeconds)

        // Tick 10 seconds of warmup at low HR
        for (i in 1..10) {
            engine.tick(currentHr = 110, currentCadence = 140, zone = MafZone.BELOW_FLOOR)
        }
        assertEquals(290L, engine.phaseRemainingSeconds)
        // Steady metrics must remain 0 during warmup!
        assertEquals(0L, engine.testInZoneSeconds)
        assertEquals(0, engine.testAvgHr)

        // Skip warmup early
        engine.skipWarmup()
        assertEquals(TestPhase.TEST_STEADY, engine.currentPhase)
        assertEquals(1800L, engine.phaseRemainingSeconds)

        // Tick 100 seconds in target zone
        for (i in 1..100) {
            engine.tick(currentHr = 142, currentCadence = 178, zone = MafZone.MAF_TARGET)
        }
        assertEquals(1700L, engine.phaseRemainingSeconds)
        assertEquals(100L, engine.testInZoneSeconds)
        assertEquals(142, engine.testAvgHr)
        assertEquals(178, engine.testAvgCadence)
        assertEquals(100, engine.testInZonePercentage)

        // Add 5 minutes on the fly (+300s)
        engine.addTimeSeconds(300L)
        assertEquals(2000L, engine.phaseRemainingSeconds)

        // Trigger recovery early with current HR = 144
        engine.startRecoveryEarly(currentHr = 144)
        assertEquals(TestPhase.RECOVERY, engine.currentPhase)
        assertEquals(120L, engine.phaseRemainingSeconds)
        assertEquals(144, engine.recoveryPeakHr)

        // Tick 60 seconds into recovery (HR drops to 118)
        for (i in 1..60) {
            engine.tick(currentHr = 118, currentCadence = 0, zone = MafZone.BELOW_FLOOR)
        }
        assertEquals(60L, engine.phaseRemainingSeconds)
        assertEquals(118, engine.recoveryHr60)
        assertEquals(26, engine.recoveryDrop60) // 144 - 118 = 26
        assertNull(engine.recoveryHr120)

        // Tick remaining 60 seconds into recovery (HR drops to 98)
        for (i in 1..60) {
            engine.tick(currentHr = 98, currentCadence = 0, zone = MafZone.BELOW_FLOOR)
        }
        assertEquals(0L, engine.phaseRemainingSeconds)
        assertEquals(TestPhase.COMPLETE, engine.currentPhase)
        assertEquals(98, engine.recoveryHr120)
        assertEquals(46, engine.recoveryDrop120) // 144 - 98 = 46

        // Steady metrics must still be isolated from recovery!
        assertEquals(100L, engine.testInZoneSeconds)
        assertEquals(142, engine.testAvgHr)
        assertEquals(178, engine.testAvgCadence)
    }

    @Test
    fun testDirectStartWithoutWarmup() {
        val engine = TestProtocolEngine(
            warmupDurationSec = 0L,
            targetTestDurationSec = 1200L
        )
        assertEquals(TestPhase.TEST_STEADY, engine.currentPhase)
        assertEquals(1200L, engine.phaseRemainingSeconds)
    }
}
