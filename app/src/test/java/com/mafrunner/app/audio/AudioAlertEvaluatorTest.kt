package com.mafrunner.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAlertEvaluatorTest {
    private val evaluator = AudioAlertEvaluator(repeatIntervalMs = 2000L)

    @Test
    fun testCeilingAlertTriggering() {
        val ceiling = 140
        // HR at or above ceiling triggers immediately
        assertTrue(evaluator.shouldTriggerCeilingAlert(140, ceiling, lastAlertTimeMs = 0L, nowMs = 1000L))
        // HR above ceiling within 2s throttled
        assertFalse(evaluator.shouldTriggerCeilingAlert(142, ceiling, lastAlertTimeMs = 1000L, nowMs = 2000L))
        // HR above ceiling after 2s triggers again
        assertTrue(evaluator.shouldTriggerCeilingAlert(141, ceiling, lastAlertTimeMs = 1000L, nowMs = 3100L))
        // HR below ceiling never triggers
        assertFalse(evaluator.shouldTriggerCeilingAlert(138, ceiling, lastAlertTimeMs = 0L, nowMs = 5000L))
    }

    @Test
    fun testFloorAlertTriggering() {
        val floor = 130
        // Persistent under floor after 30s triggers
        assertTrue(evaluator.shouldTriggerFloorAlert(128, floor, lastAlertTimeMs = 0L, nowMs = 35000L))
        // Under floor before 30s interval throttled
        assertFalse(evaluator.shouldTriggerFloorAlert(125, floor, lastAlertTimeMs = 35000L, nowMs = 45000L))
    }
}
