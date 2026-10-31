package com.mafrunner.app.export

import com.mafrunner.app.data.local.HeartRateSampleEntity
import com.mafrunner.app.data.local.WorkoutEntity
import java.text.SimpleDateFormat
import java.util.*

class TcxExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun export(workout: WorkoutEntity, samples: List<HeartRateSampleEntity>): String {
        val startTimeStr = isoFormat.format(Date(workout.startTime))
        val totalDistanceMeters = (workout.treadmillDistance ?: 0f) * 1000f

        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\">")
        sb.appendLine("  <Activities>")
        sb.appendLine("    <Activity Sport=\"Running\">")
        sb.appendLine("      <Id>$startTimeStr</Id>")
        sb.appendLine("      <Lap StartTime=\"$startTimeStr\">")
        sb.appendLine("        <TotalTimeSeconds>${workout.durationSec}</TotalTimeSeconds>")
        sb.appendLine("        <DistanceMeters>$totalDistanceMeters</DistanceMeters>")
        sb.appendLine("        <MaximumSpeed>${(workout.treadmillSpeed ?: 0f) / 3.6f}</MaximumSpeed>")
        sb.appendLine("        <Calories>0</Calories>")
        sb.appendLine("        <AverageHeartRateBpm><Value>${workout.avgHr}</Value></AverageHeartRateBpm>")
        sb.appendLine("        <MaximumHeartRateBpm><Value>${workout.maxHr}</Value></MaximumHeartRateBpm>")
        sb.appendLine("        <Intensity>Active</Intensity>")
        sb.appendLine("        <TriggerMethod>Manual</TriggerMethod>")
        sb.appendLine("        <Track>")

        val totalSamples = samples.size
        for ((index, sample) in samples.withIndex()) {
            val sampleTimeStr = isoFormat.format(Date(sample.timestamp))
            val cumulativeDistance = if (totalSamples > 0 && totalDistanceMeters > 0f) {
                ((index + 1).toFloat() / totalSamples) * totalDistanceMeters
            } else {
                0f
            }

            sb.appendLine("          <Trackpoint>")
            sb.appendLine("            <Time>$sampleTimeStr</Time>")
            if (totalDistanceMeters > 0f) {
                sb.appendLine("            <DistanceMeters>$cumulativeDistance</DistanceMeters>")
            }
            sb.appendLine("            <HeartRateBpm>")
            sb.appendLine("              <Value>${sample.hr}</Value>")
            sb.appendLine("            </HeartRateBpm>")
            if (sample.cadence > 0) {
                sb.appendLine("            <Cadence>${sample.cadence}</Cadence>")
            }
            sb.appendLine("          </Trackpoint>")
        }

        sb.appendLine("        </Track>")
        sb.appendLine("      </Lap>")
        sb.appendLine("    </Activity>")
        sb.appendLine("  </Activities>")
        sb.appendLine("</TrainingCenterDatabase>")
        return sb.toString()
    }
}
