package com.mafrunner.app.export

import com.mafrunner.app.data.local.HeartRateSampleEntity
import com.mafrunner.app.data.local.WorkoutEntity
import java.text.SimpleDateFormat
import java.util.*

class CsvExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun export(workout: WorkoutEntity, samples: List<HeartRateSampleEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("WorkoutId,StartTime,DurationSec,AvgHR,MaxHR,AvgCadence,TreadmillSpeedKmH,TreadmillDistanceKm,IsBenchmark")
        sb.appendLine("${workout.id},${isoFormat.format(Date(workout.startTime))},${workout.durationSec},${workout.avgHr},${workout.maxHr},${workout.avgCadence},${workout.treadmillSpeed ?: ""},${workout.treadmillDistance ?: ""},${workout.isBenchmark}")
        sb.appendLine()
        sb.appendLine("Timestamp,TimeOffsetSec,HeartRateBPM,CadenceSPM")

        for (sample in samples) {
            val offsetSec = (sample.timestamp - workout.startTime) / 1000
            sb.appendLine("${isoFormat.format(Date(sample.timestamp))},$offsetSec,${sample.hr},${sample.cadence}")
        }
        return sb.toString()
    }
}
