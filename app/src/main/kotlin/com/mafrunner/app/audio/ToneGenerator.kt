package com.mafrunner.app.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object ToneGenerator {

    /**
     * Generates a 16-bit PCM WAV file with a clean sine wave chime and envelope.
     */
    fun createWavFile(
        outputFile: File,
        freqHz: Double,
        durationMs: Int,
        sampleRate: Int = 44100
    ): File {
        val numSamples = (durationMs * sampleRate) / 1000
        val audioData = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Linear attack / decay envelope to avoid speaker clicks
            val envelope = when {
                i < numSamples * 0.1 -> i / (numSamples * 0.1)
                i > numSamples * 0.8 -> (numSamples - i) / (numSamples * 0.2)
                else -> 1.0
            }
            val sampleVal = (sin(2.0 * PI * freqHz * t) * envelope * 32767.0).toInt().coerceIn(-32768, 32767)
            audioData[i] = sampleVal.toShort()
        }

        val totalDataLen = numSamples * 2
        val totalAudioLen = totalDataLen + 36

        FileOutputStream(outputFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalAudioLen)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Sub-chunk 1 size (16 for PCM)
            header.putShort(1.toShort()) // Audio format (1 = PCM)
            header.putShort(1.toShort()) // Channels (1 = Mono)
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2) // Byte rate
            header.putShort(2.toShort()) // Block align
            header.putShort(16.toShort()) // Bits per sample
            header.put("data".toByteArray())
            header.putInt(totalDataLen)

            fos.write(header.array())

            val buffer = ByteBuffer.allocate(totalDataLen).order(ByteOrder.LITTLE_ENDIAN)
            for (s in audioData) {
                buffer.putShort(s)
            }
            fos.write(buffer.array())
        }

        return outputFile
    }
}
