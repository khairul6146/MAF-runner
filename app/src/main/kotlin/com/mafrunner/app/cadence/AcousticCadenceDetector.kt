package com.mafrunner.app.cadence

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

class AcousticCadenceDetector {

    private val peakDetector = FootstrikePeakDetector()
    val cadenceSpm: StateFlow<Int> = peakDetector.cadenceSpm
    val liveEnergy: StateFlow<Float> = peakDetector.liveEnergy
    val currentThreshold: StateFlow<Float> = peakDetector.currentThreshold
    val lastStrikeTimestampMs: StateFlow<Long> = peakDetector.lastStrikeTimestampMsFlow

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    fun setSensitivity(multiplier: Float) {
        peakDetector.setSensitivity(multiplier)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(1024)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (_: Exception) {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            } catch (_: Exception) {
                return
            }
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return
        }

        audioRecord?.startRecording()
        isRecording = true
        peakDetector.reset()

        val filter = BandpassFilter(sampleRate = SAMPLE_RATE, centerFreq = 100f, qFactor = 1.0f)
        val audioBuffer = ShortArray(512)

        recordingJob = scope.launch {
            while (isActive && isRecording) {
                val record = audioRecord ?: break
                val readShorts = record.read(audioBuffer, 0, audioBuffer.size)
                if (readShorts > 0) {
                    var sumSquares = 0.0
                    for (i in 0 until readShorts) {
                        // 12.0x digital pre-gain for deck footstrike detection
                        val rawSample = (audioBuffer[i] / 32768.0f) * 12.0f
                        val filtered = filter.filter(rawSample)
                        sumSquares += filtered * filtered
                    }
                    val rmsEnergy = (sqrt(sumSquares / readShorts).toFloat()).coerceIn(0f, 1f)
                    val nowMs = System.currentTimeMillis()
                    peakDetector.processSample(rmsEnergy, nowMs)
                }
            }
        }
    }

    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        peakDetector.reset()
    }
}
