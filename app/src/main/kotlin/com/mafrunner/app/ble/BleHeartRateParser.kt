package com.mafrunner.app.ble

data class HeartRateMeasurement(
    val hr: Int,
    val rrIntervals: List<Int> = emptyList()
)

class BleHeartRateParser {

    /**
     * Parses standard Bluetooth SIG GATT Characteristic 0x2A37 (Heart Rate Measurement).
     * Byte 0: Flags
     *   Bit 0: 0 = 8-bit HR format, 1 = 16-bit HR format
     *   Bit 4: 1 = RR intervals present
     */
    fun parse(bytes: ByteArray): HeartRateMeasurement {
        if (bytes.isEmpty()) return HeartRateMeasurement(0)

        val flags = bytes[0].toInt()
        val is16Bit = (flags and 0x01) != 0
        val rrPresent = (flags and 0x10) != 0

        var offset = 1
        val hr = if (is16Bit) {
            if (bytes.size < 3) return HeartRateMeasurement(0)
            val lsb = bytes[offset++].toInt() and 0xFF
            val msb = bytes[offset++].toInt() and 0xFF
            (msb shl 8) or lsb
        } else {
            if (bytes.size < 2) return HeartRateMeasurement(0)
            bytes[offset++].toInt() and 0xFF
        }

        // Energy Expended check (Bit 3)
        val energyExpendedPresent = (flags and 0x08) != 0
        if (energyExpendedPresent && bytes.size >= offset + 2) {
            offset += 2
        }

        val rrIntervals = mutableListOf<Int>()
        if (rrPresent) {
            while (bytes.size >= offset + 2) {
                val lsb = bytes[offset++].toInt() and 0xFF
                val msb = bytes[offset++].toInt() and 0xFF
                val rrValue = (msb shl 8) or lsb
                rrIntervals.add(rrValue)
            }
        }

        return HeartRateMeasurement(hr = hr, rrIntervals = rrIntervals)
    }
}
