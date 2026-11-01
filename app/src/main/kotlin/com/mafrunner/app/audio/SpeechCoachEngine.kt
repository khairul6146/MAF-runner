package com.mafrunner.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object SpeechCoachPrompts {
    fun ceilingWarning(currentHr: Int): String =
        "Heart rate high at $currentHr BPM. Ease back your pace."

    fun floorWarning(currentHr: Int): String =
        "Heart rate low at $currentHr BPM. Pick up your pace."

    fun warmupStart(): String =
        "Warmup started. Gradually bring your heart rate up to your MAF zone."

    fun testStart(durationMinutes: Int, targetHr: Int): String =
        "Warmup complete. Beginning $durationMinutes-minute MAF test. Keep heart rate locked at $targetHr BPM."

    fun recoveryStart(): String =
        "Test complete! Stand still on the treadmill side rails for 2 minutes to measure cardiac recovery."

    fun recoveryComplete(drop60: Int, drop120: Int): String =
        "Recovery complete. Heart rate dropped $drop60 BPM in 1 minute, and $drop120 BPM in 2 minutes."
}

class SpeechCoachEvaluator(val cooldownMs: Long = 20_000L) {
    fun canSpeakOutOfZone(lastSpeechTimestampMs: Long, currentTimestampMs: Long): Boolean {
        if (lastSpeechTimestampMs == 0L) return true
        return (currentTimestampMs - lastSpeechTimestampMs) >= cooldownMs
    }
}

class SpeechCoachEngine(
    private val context: Context,
    val evaluator: SpeechCoachEvaluator = SpeechCoachEvaluator()
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastSpeechTimestampMs: Long = 0L

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            isTtsReady = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }

    fun speak(text: String, flush: Boolean = false) {
        if (!isTtsReady || tts == null) return
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queueMode, null, "SpeechCoach_${System.currentTimeMillis()}")
    }

    fun speakCeilingWarning(currentHr: Int, currentTimestampMs: Long = System.currentTimeMillis()): Boolean {
        if (evaluator.canSpeakOutOfZone(lastSpeechTimestampMs, currentTimestampMs)) {
            lastSpeechTimestampMs = currentTimestampMs
            speak(SpeechCoachPrompts.ceilingWarning(currentHr), flush = true)
            return true
        }
        return false
    }

    fun speakFloorWarning(currentHr: Int, currentTimestampMs: Long = System.currentTimeMillis()): Boolean {
        if (evaluator.canSpeakOutOfZone(lastSpeechTimestampMs, currentTimestampMs)) {
            lastSpeechTimestampMs = currentTimestampMs
            speak(SpeechCoachPrompts.floorWarning(currentHr), flush = true)
            return true
        }
        return false
    }

    fun speakWarmupStart() {
        speak(SpeechCoachPrompts.warmupStart(), flush = false)
    }

    fun speakTestStart(durationMinutes: Int, targetHr: Int) {
        speak(SpeechCoachPrompts.testStart(durationMinutes, targetHr), flush = false)
    }

    fun speakRecoveryStart() {
        speak(SpeechCoachPrompts.recoveryStart(), flush = true)
    }

    fun speakRecoveryComplete(drop60: Int, drop120: Int) {
        speak(SpeechCoachPrompts.recoveryComplete(drop60, drop120), flush = false)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isTtsReady = false
    }
}
