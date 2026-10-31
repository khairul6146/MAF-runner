package com.mafrunner.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File

class AudioAlertEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val soundPool: SoundPool
    private var ceilingSoundId: Int = 0
    private var floorSoundId: Int = 0

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        try {
            // Generate clean synthesized WAV files in cache for immediate SoundPool loading
            val cacheDir = context.cacheDir
            val ceilingWav = File(cacheDir, "maf_ceiling_alert.wav")
            ToneGenerator.createWavFile(ceilingWav, freqHz = 880.0, durationMs = 280) // High urgency A5 beep
            ceilingSoundId = soundPool.load(ceilingWav.absolutePath, 1)

            val floorWav = File(cacheDir, "maf_floor_alert.wav")
            ToneGenerator.createWavFile(floorWav, freqHz = 440.0, durationMs = 200) // Gentle mellow A4 chime
            floorSoundId = soundPool.load(floorWav.absolutePath, 1)
        } catch (_: Exception) {}
    }

    private fun requestTransientAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    fun playCeilingAlert() {
        requestTransientAudioFocus()
        if (ceilingSoundId != 0) {
            soundPool.play(ceilingSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }

        // Haptic feedback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(250)
        }
    }

    fun playFloorAlert() {
        requestTransientAudioFocus()
        if (floorSoundId != 0) {
            soundPool.play(floorSoundId, 0.7f, 0.7f, 0, 0, 1.0f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
