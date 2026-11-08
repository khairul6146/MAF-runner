package com.mafrunner.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FitcareProtocolEngineTest {

    private val engine = FitcareProtocolEngine()

    @Test
    fun testParseEcgPacketValidA2Frame() {
        // 0xA2 frame (fixed packet): startIndex = 2
        // Samples: 500 (0x01, 0xF4), -300 (0xFE, 0xD4)
        // Header: 0xA2 (162), Length: 0x07 (7), sample1: 0x01, 0xF4, sample2: 0xFE, 0xD4
        // Checksum: (162 + 7 + 1 + 244 + 254 + 212) % 256 = 880 % 256 = 112 (0x70)
        val packet = byteArrayOf(
            0xA2.toByte(),
            0x07.toByte(),
            0x01.toByte(),
            0xF4.toByte(),
            0xFE.toByte(),
            0xD4.toByte(),
            0x70.toByte()
        )

        val samples = engine.parseEcgPacket(packet)
        assertNotNull(samples)
        assertEquals(listOf(500, -300), samples)
    }

    @Test
    fun testParseEcgPacketValidA3Frame() {
        // 0xA3 frame (streaming packet): startIndex = 3 (skips header, length, seq)
        // Header: 0xA3 (163), Length: 0x08 (8), Seq: 0x2A (42)
        // Samples: 1234 (0x04, 0xD2), -50 (0xFF, 0xCE)
        // Checksum: (163 + 8 + 42 + 4 + 210 + 255 + 206) % 256 = 888 % 256 = 120 (0x78)
        val packet = byteArrayOf(
            0xA3.toByte(),
            0x08.toByte(),
            0x2A.toByte(),
            0x04.toByte(),
            0xD2.toByte(),
            0xFF.toByte(),
            0xCE.toByte(),
            0x78.toByte()
        )

        val samples = engine.parseEcgPacket(packet)
        assertNotNull(samples)
        assertEquals(listOf(1234, -50), samples)
    }

    @Test
    fun testParseEcgPacketInvalidChecksumReturnsNull() {
        // Valid A2 frame with corrupted checksum (0x00 instead of 0x70)
        val corruptedPacket = byteArrayOf(
            0xA2.toByte(),
            0x07.toByte(),
            0x01.toByte(),
            0xF4.toByte(),
            0xFE.toByte(),
            0xD4.toByte(),
            0x00.toByte()
        )

        val samples = engine.parseEcgPacket(corruptedPacket)
        assertNull(samples)
    }

    @Test
    fun testParseEcgPacketInvalidHeaderReturnsNull() {
        val invalidHeaderPacket = byteArrayOf(0xA1.toByte(), 0x04.toByte(), 0x01.toByte(), 0x00.toByte())
        assertNull(engine.parseEcgPacket(invalidHeaderPacket))
    }

    @Test
    fun testParseEcgPacketTooShortReturnsNull() {
        val shortPacket = byteArrayOf(0xA2.toByte(), 0x02.toByte(), 0x00.toByte())
        assertNull(engine.parseEcgPacket(shortPacket))
    }

    @Test
    fun testCalculateRmssdKnownValues() {
        // rrIntervalsMs: [800.0, 850.0, 820.0]
        // diff1 = 50.0 -> diff1^2 = 2500.0
        // diff2 = -30.0 -> diff2^2 = 900.0
        // mean = 3400.0 / 2 = 1700.0
        // sqrt(1700) = 41.231... -> rounded = 41.2
        val rrList = listOf(800.0, 850.0, 820.0)
        val rmssd = engine.calculateRmssd(rrList)
        assertNotNull(rmssd)
        assertEquals(41.2, rmssd!!, 0.001)
    }

    @Test
    fun testCalculateRmssdConstantValues() {
        val rrList = listOf(800.0, 800.0, 800.0)
        val rmssd = engine.calculateRmssd(rrList)
        assertNotNull(rmssd)
        assertEquals(0.0, rmssd!!, 0.001)
    }

    @Test
    fun testCalculateRmssdInsufficientSamplesReturnsNull() {
        assertNull(engine.calculateRmssd(emptyList<Double>()))
        assertNull(engine.calculateRmssd(listOf(800.0)))
    }

    @Test
    fun testBuildArmbandThresholdCommand() {
        // maxHr = 155 (0x9B)
        // Header: 0xA4, Length: 0x02, Data: 0x9B
        // Checksum: (164 + 2 + 155) % 256 = 321 % 256 = 65 (0x41)
        val command = engine.buildArmbandThresholdCommand(155)
        val expected = byteArrayOf(0xA4.toByte(), 0x02.toByte(), 155.toByte(), 0x41.toByte())
        assertEquals(expected.toList(), command.toList())
    }
}
