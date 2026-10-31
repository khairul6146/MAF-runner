package com.mafrunner.app.audio

class AudioAlertEvaluator(
    private val repeatIntervalMs: Long = 2000L,
    private val floorIntervalMs: Long = 30000L
) {
    fun shouldTriggerCeilingAlert(
        currentHr: Int,
        ceiling: Int,
        lastAlertTimeMs: Long,
        nowMs: Long
    ): Boolean {
        if (currentHr < ceiling) return false
        if (lastAlertTimeMs == 0L) return true
        return (nowMs - lastAlertTimeMs) >= repeatIntervalMs
    }

    fun shouldTriggerFloorAlert(
        currentHr: Int,
        floor: Int,
        lastAlertTimeMs: Long,
        nowMs: Long
    ): Boolean {
        if (currentHr >= floor) return false
        if (lastAlertTimeMs == 0L) return nowMs >= floorIntervalMs
        return (nowMs - lastAlertTimeMs) >= floorIntervalMs
    }
}
