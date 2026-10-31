package com.mafrunner.app.cadence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootstrikePeakDetectorTest {
    @Test
    fun testRefractoryPeriodRejectsBounces() {
        val detector = FootstrikePeakDetector(minRefractoryMs = 220L)
        // First strike
        assertTrue(detector.processSample(energy = 0.8f, timestampMs = 1000L))
        // Rebound at 100ms should be rejected
        assertFalse(detector.processSample(energy = 0.9f, timestampMs = 1100L))
        // Legitimate second strike at 350ms (~171 SPM) should pass
        assertTrue(detector.processSample(energy = 0.85f, timestampMs = 1350L))
    }

    @Test
    fun testCadenceCalculation() {
        val detector = FootstrikePeakDetector(minRefractoryMs = 220L)
        // Simulate 180 SPM (every 333ms = 60000 / 180)
        var t = 1000L
        for (i in 0 until 6) {
            detector.processSample(energy = 0.9f, timestampMs = t)
            t += 333L
        }
        assertEquals(180, detector.cadenceSpm.value)
    }
}
