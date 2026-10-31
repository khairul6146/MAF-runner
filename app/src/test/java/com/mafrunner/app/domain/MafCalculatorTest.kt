package com.mafrunner.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MafCalculatorTest {
    private val calculator = MafCalculator()

    @Test
    fun testBaseFormulaWithCategories() {
        // Age 35: Base = 180 - 35 = 145
        assertEquals(135, calculator.calculate(35, MafCategory.ILLNESS_REHAB).ceiling) // -10
        assertEquals(140, calculator.calculate(35, MafCategory.BEGINNER_RETURN).ceiling) // -5
        assertEquals(145, calculator.calculate(35, MafCategory.CONSISTENT_BASE).ceiling) // 0
        assertEquals(150, calculator.calculate(35, MafCategory.ADVANCED_PRO).ceiling) // +5
    }

    @Test
    fun testZoneRanges() {
        val zones = calculator.calculate(35, MafCategory.BEGINNER_RETURN) // Ceiling = 140
        assertEquals(140, zones.ceiling)
        assertEquals(135, zones.targetMin) // Ceiling - 5
        assertEquals(130, zones.floor) // Ceiling - 10

        assertEquals(MafZone.BELOW_FLOOR, calculator.evaluateZone(128, zones))
        assertEquals(MafZone.MAF_TARGET, calculator.evaluateZone(137, zones))
        assertEquals(MafZone.OVER_CEILING, calculator.evaluateZone(140, zones))
        assertEquals(MafZone.OVER_CEILING, calculator.evaluateZone(145, zones))
    }

    @Test
    fun testManualOverride() {
        val zones = calculator.calculate(35, MafCategory.BEGINNER_RETURN, manualCeiling = 142)
        assertEquals(142, zones.ceiling)
        assertEquals(137, zones.targetMin)
        assertEquals(132, zones.floor)
    }
}
