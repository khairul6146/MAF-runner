package com.mafrunner.app.domain

class MafCalculator {

    fun calculate(age: Int, category: MafCategory, manualCeiling: Int? = null): MafZones {
        val calculatedCeiling = manualCeiling ?: ((180 - age) + category.offsetBpm)
        val targetMin = calculatedCeiling - 5
        val floor = calculatedCeiling - 10
        return MafZones(
            ceiling = calculatedCeiling,
            targetMin = targetMin,
            floor = floor
        )
    }

    fun evaluateZone(currentHr: Int, zones: MafZones): MafZone {
        return when {
            currentHr >= zones.ceiling -> MafZone.OVER_CEILING
            currentHr < zones.floor -> MafZone.BELOW_FLOOR
            else -> MafZone.MAF_TARGET
        }
    }
}
