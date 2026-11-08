package com.mafrunner.app.ui

import com.mafrunner.app.domain.MafZones
import com.mafrunner.app.ui.components.RealtimeGraphScaling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeGraphScalingTest {

    private val standardZones = MafZones(
        ceiling = 145,
        targetMin = 135,
        floor = 130
    )

    @Test
    fun testDefaultRangeEnclosesCeilingAndFloor() {
        val (minY, maxY) = RealtimeGraphScaling.calculateYRange(emptyList(), standardZones)

        assertEquals(115f, minY, 0.01f) // 130 - 15
        assertEquals(160f, maxY, 0.01f) // 145 + 15
        assertTrue(minY <= standardZones.floor)
        assertTrue(maxY >= standardZones.ceiling)
        assertTrue(maxY - minY >= 30f)
    }

    @Test
    fun testExpansionWhenSamplesExceedCeiling() {
        val samples = listOf(135, 142, 175)
        val (minY, maxY) = RealtimeGraphScaling.calculateYRange(samples, standardZones)

        assertEquals(115f, minY, 0.01f)
        assertEquals(180f, maxY, 0.01f) // 175 + 5
        assertTrue(maxY > 160f)
    }

    @Test
    fun testExpansionWhenSamplesDropBelowFloor() {
        val samples = listOf(80, 100, 132)
        val (minY, maxY) = RealtimeGraphScaling.calculateYRange(samples, standardZones)

        assertEquals(75f, minY, 0.01f) // 80 - 5
        assertEquals(160f, maxY, 0.01f)
        assertTrue(minY < 115f)
    }

    @Test
    fun testMinimumSpanPreservation() {
        // Tight / edge-case zones where ceiling and floor are very close
        val tightZones = MafZones(
            ceiling = 42,
            targetMin = 42,
            floor = 42
        )
        val (minY, maxY) = RealtimeGraphScaling.calculateYRange(emptyList(), tightZones)

        assertTrue("Span should be >= 30f, got ${maxY - minY}", maxY - minY >= 30f)
        assertTrue("Min should be >= 40f, got $minY", minY >= 40f)
        assertTrue("Max should be <= 220f, got $maxY", maxY <= 220f)
    }

    @Test
    fun testBoundsClampedToMin40AndMax220() {
        val extremeLow = listOf(30, 35)
        val (minYLow, _) = RealtimeGraphScaling.calculateYRange(extremeLow, standardZones)
        assertEquals(40f, minYLow, 0.01f)

        val extremeHigh = listOf(225, 230)
        val (_, maxYHigh) = RealtimeGraphScaling.calculateYRange(extremeHigh, standardZones)
        assertEquals(220f, maxYHigh, 0.01f)
    }
}
