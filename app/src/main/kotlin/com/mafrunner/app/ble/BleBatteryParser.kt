package com.mafrunner.app.ble

class BleBatteryParser {
    fun parse(bytes: ByteArray?): Int? {
        if (bytes == null || bytes.isEmpty()) return null
        val raw = bytes[0].toInt() and 0xFF
        return raw.coerceIn(0, 100)
    }
}
