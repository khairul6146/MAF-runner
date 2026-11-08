package com.mafrunner.app.ble

import kotlin.math.round
import kotlin.math.sqrt

/**
 * Protocol engine for Fitcare / Heartool devices.
 * Decodes proprietary 16-bit ECG sample streaming frames (0xA2 / 0xA3),
 * calculates real-time HRV (RMSSD) from beat-to-beat RR intervals,
 * and builds hardware command frames (e.g. armband threshold alerts).
 */
class FitcareProtocolEngine {

    fun parseEcgPacket(data: ByteArray): List<Int>? = Companion.parseEcgPacket(data)

    fun calculateRmssd(rrIntervalsMs: List<Double>): Double? = Companion.calculateRmssd(rrIntervalsMs)

    fun buildArmbandThresholdCommand(maxHr: Int): ByteArray = Companion.buildArmbandThresholdCommand(maxHr)

    companion object {
        /**
         * Decodes Fitcare proprietary ECG packets.
         * Valid headers: 0xA2 (fixed frame) or 0xA3 (streaming frame).
         * Checksum: trailing byte must match modulo-256 sum of all preceding bytes.
         * Samples: 16-bit Big-Endian signed values.
         *   - Starts at index 2 for 0xA2
         *   - Starts at index 3 for 0xA3 (skipping header, length, seq)
         */
        fun parseEcgPacket(data: ByteArray): List<Int>? {
            if (data.size < 4) return null

            val header = data[0].toInt() and 0xFF
            if (header != 0xA2 && header != 0xA3) return null

            // Validate checksum (last byte is sum of all preceding bytes modulo 256)
            val expectedChecksum = data.dropLast(1).sumOf { it.toInt() and 0xFF } % 256
            val actualChecksum = data.last().toInt() and 0xFF
            if (actualChecksum != expectedChecksum) return null

            val startIndex = if (header == 0xA3) 3 else 2
            val samples = mutableListOf<Int>()
            var i = startIndex

            while (i + 1 < data.size - 1) {
                val rawVal = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                val sample = if (rawVal > 32767) rawVal - 65536 else rawVal
                samples.add(sample)
                i += 2
            }

            return samples
        }

        /**
         * Calculates RMSSD (Root Mean Square of Successive Differences) for HRV in ms.
         * Returns null if fewer than 2 intervals are provided.
         * Result is rounded to 1 decimal place.
         */
        fun calculateRmssd(rrIntervalsMs: List<Double>): Double? {
            if (rrIntervalsMs.size < 2) return null

            var sumSquaredDiffs = 0.0
            for (i in 1 until rrIntervalsMs.size) {
                val diff = rrIntervalsMs[i] - rrIntervalsMs[i - 1]
                sumSquaredDiffs += diff * diff
            }

            val mean = sumSquaredDiffs / (rrIntervalsMs.size - 1)
            val rmssd = sqrt(mean)
            return round(rmssd * 10.0) / 10.0
        }

        /**
         * Builds an armband threshold command frame for setting hardware vibration/LED alerts.
         * Format: [0xA4, 0x02, maxHr, Checksum]
         */
        fun buildArmbandThresholdCommand(maxHr: Int): ByteArray {
            val header = 0xA4.toByte()
            val length = 0x02.toByte()
            val hrByte = (maxHr and 0xFF).toByte()
            val checksum = ((0xA4 + 0x02 + (maxHr and 0xFF)) % 256).toByte()
            return byteArrayOf(header, length, hrByte, checksum)
        }
    }
}
