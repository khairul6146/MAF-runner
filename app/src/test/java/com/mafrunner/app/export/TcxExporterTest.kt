package com.mafrunner.app.export

import com.mafrunner.app.data.local.HeartRateSampleEntity
import com.mafrunner.app.data.local.WorkoutEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class TcxExporterTest {
    private val exporter = TcxExporter()

    @Test
    fun testTcxDistanceInterpolation() {
        val workout = WorkoutEntity(
            id = 1L,
            startTime = 1000L,
            endTime = 3000L,
            durationSec = 2,
            underSec = 0,
            inZoneSec = 2,
            overSec = 0,
            avgHr = 140,
            maxHr = 140,
            avgCadence = 180,
            treadmillSpeed = 10f,
            treadmillDistance = 1.0f,
            isBenchmark = true
        )
        val samples = listOf(
            HeartRateSampleEntity(1L, 1L, 1000L, 140, 180),
            HeartRateSampleEntity(2L, 1L, 2000L, 140, 180)
        )
        val xml = exporter.export(workout, samples)
        assertTrue(xml.contains("<DistanceMeters>500.0</DistanceMeters>"))
        assertTrue(xml.contains("<DistanceMeters>1000.0</DistanceMeters>"))
        assertTrue(xml.contains("<Value>140</Value>"))
    }
}
