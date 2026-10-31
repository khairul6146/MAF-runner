package com.mafrunner.app.domain

enum class MafCategory(val offsetBpm: Int, val label: String, val description: String) {
    ILLNESS_REHAB(-10, "-10 BPM", "Illness, major rehab, or medication"),
    BEGINNER_RETURN(-5, "-5 BPM", "Beginner, returning from injury, or inconsistent"),
    CONSISTENT_BASE(0, "0 BPM", "Consistent training for 1-2+ years without issue"),
    ADVANCED_PRO(+5, "+5 BPM", "Competitive training for 2+ years with progression")
}
