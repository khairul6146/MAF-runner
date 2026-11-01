package com.mafrunner.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleBatteryParserTest {
    private val parser = BleBatteryParser()

    @Test
    fun testStandardBatteryLevel() {
        // 0x55 = 85%
        val bytes = byteArrayOf(0x55.toByte())
        assertEquals(85, parser.parse(bytes))
    }

    @Test
    fun testMinAndMaxBatteryLevel() {
        assertEquals(0, parser.parse(byteArrayOf(0x00.toByte())))
        assertEquals(100, parser.parse(byteArrayOf(0x64.toByte())))
    }

    @Test
    fun testClampingOutOfRange() {
        // Negative signed byte 0xFF (-1 in Java/Kotlin signed byte, 255 unsigned)
        assertEquals(100, parser.parse(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun testNullAndEmptyInput() {
        assertNull(parser.parse(null))
        assertNull(parser.parse(byteArrayOf()))
    }
}
