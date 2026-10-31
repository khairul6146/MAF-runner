package com.mafrunner.app.domain

enum class MafZone {
    BELOW_FLOOR,
    MAF_TARGET,
    OVER_CEILING
}

data class MafZones(
    val ceiling: Int,
    val targetMin: Int,
    val floor: Int
)
