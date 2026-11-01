package com.mafrunner.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCoachEngineTest {

    @Test
    fun testPromptFormatting() {
        assertEquals(
            "Heart rate high at 145 BPM. Ease back your pace.",
            SpeechCoachPrompts.ceilingWarning(145)
        )
        assertEquals(
            "Heart rate low at 132 BPM. Pick up your pace.",
            SpeechCoachPrompts.floorWarning(132)
        )
        assertEquals(
            "Warmup started. Gradually bring your heart rate up to your MAF zone.",
            SpeechCoachPrompts.warmupStart()
        )
        assertEquals(
            "Warmup complete. Beginning 30-minute MAF test. Keep heart rate locked at 142 BPM.",
            SpeechCoachPrompts.testStart(30, 142)
        )
        assertEquals(
            "Test complete! Stand still on the treadmill side rails for 2 minutes to measure cardiac recovery.",
            SpeechCoachPrompts.recoveryStart()
        )
        assertEquals(
            "Recovery complete. Heart rate dropped 26 BPM in 1 minute, and 46 BPM in 2 minutes.",
            SpeechCoachPrompts.recoveryComplete(26, 46)
        )
    }

    @Test
    fun testRateLimitingGating() {
        val evaluator = SpeechCoachEvaluator(cooldownMs = 20_000L)

        // Initial speech at T=0
        assertTrue(evaluator.canSpeakOutOfZone(lastSpeechTimestampMs = 0L, currentTimestampMs = 25_000L))

        // Triggered at T=25,000. Subsequent check at T=35,000 (only 10s elapsed) -> must be blocked
        assertFalse(evaluator.canSpeakOutOfZone(lastSpeechTimestampMs = 25_000L, currentTimestampMs = 35_000L))

        // Check at T=45,000 (20s elapsed) -> allowed
        assertTrue(evaluator.canSpeakOutOfZone(lastSpeechTimestampMs = 25_000L, currentTimestampMs = 45_000L))

        // Check at T=46,000 (21s elapsed) -> allowed
        assertTrue(evaluator.canSpeakOutOfZone(lastSpeechTimestampMs = 25_000L, currentTimestampMs = 46_000L))
    }
}
