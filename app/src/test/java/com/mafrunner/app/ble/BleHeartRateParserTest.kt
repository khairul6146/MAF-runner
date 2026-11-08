package com.mafrunner.app.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class BleHeartRateParserTest {
    private val parser = BleHeartRateParser()

    @Test
    fun test8BitHeartRateParsing() {
        // Flags: 0x00 (8-bit format), HR: 142 bpm
        val packet = byteArrayOf(0x00, 142.toByte())
        val measurement = parser.parse(packet)
        assertEquals(142, measurement.hr)
    }

    @Test
    fun test16BitHeartRateParsing() {
        // Flags: 0x01 (16-bit format), HR: 145 (0x0091)
        val packet = byteArrayOf(0x01, 0x91.toByte(), 0x00.toByte())
        val measurement = parser.parse(packet)
        assertEquals(145, measurement.hr)
    }

    @Test
    fun testWithRrIntervals() {
        // Flags: 0x10 (8-bit format + RR intervals present)
        // HR: 140 bpm, RR interval: 1024 / 1024 sec = 1000ms (0x0400)
        val packet = byteArrayOf(0x10, 140.toByte(), 0x00.toByte(), 0x04.toByte())
        val measurement = parser.parse(packet)
        assertEquals(140, measurement.hr)
        assertEquals(1, measurement.rrIntervals.size)
        assertEquals(1024, measurement.rrIntervals[0])
        assertEquals(listOf(1000.0), measurement.rrIntervalsMs)
    }
}
