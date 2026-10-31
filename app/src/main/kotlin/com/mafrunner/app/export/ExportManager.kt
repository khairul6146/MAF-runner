package com.mafrunner.app.export

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mafrunner.app.data.local.WorkoutEntity
import com.mafrunner.app.data.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ExportManager {

    private val csvExporter = CsvExporter()
    private val tcxExporter = TcxExporter()
    private val fileDateFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    suspend fun exportAll(
        activity: Activity,
        workouts: List<WorkoutEntity>,
        repository: WorkoutRepository
    ): Int = withContext(Dispatchers.IO) {
        if (workouts.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "No workouts to export", Toast.LENGTH_SHORT).show()
            }
            return@withContext 0
        }

        val exportDir = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MAFRunner").apply {
            mkdirs()
        }

        val generatedFiles = mutableListOf<File>()
        val contentUris = ArrayList<Uri>()

        // 1. Export Master Workouts Index CSV
        val masterCsvFile = File(exportDir, "maf_all_workouts_${fileDateFormatter.format(Date())}.csv")
        masterCsvFile.bufferedWriter().use { writer ->
            writer.write("Id,StartTime,DurationSec,UnderSec,InZoneSec,OverSec,InZonePct,AvgHR,MaxHR,AvgCadence,TreadmillSpeed,TreadmillDistance,IsBenchmark\n")
            for (w in workouts) {
                writer.write("${w.id},${w.startTime},${w.durationSec},${w.underSec},${w.inZoneSec},${w.overSec},${w.inZonePercentage},${w.avgHr},${w.maxHr},${w.avgCadence},${w.treadmillSpeed ?: ""},${w.treadmillDistance ?: ""},${w.isBenchmark}\n")
            }
        }
        generatedFiles.add(masterCsvFile)

        // 2. Export detailed TCX and CSV for each workout
        for (w in workouts) {
            val samples = repository.getSamplesForWorkout(w.id)
            val dateStr = fileDateFormatter.format(Date(w.startTime))

            val tcxContent = tcxExporter.export(w, samples)
            val tcxFile = File(exportDir, "run_${w.id}_$dateStr.tcx")
            tcxFile.writeText(tcxContent)
            generatedFiles.add(tcxFile)

            val csvContent = csvExporter.export(w, samples)
            val csvFile = File(exportDir, "run_${w.id}_$dateStr.csv")
            csvFile.writeText(csvContent)
            generatedFiles.add(csvFile)
        }

        // 3. Obtain FileProvider URIs
        for (f in generatedFiles) {
            try {
                val uri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    f
                )
                contentUris.add(uri)
            } catch (_: Exception) {}
        }

        // 4. Save master CSV to public Downloads on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, masterCsvFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MAFRunner")
                }
                val uri = activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let { destUri ->
                    activity.contentResolver.openOutputStream(destUri)?.use { out ->
                        masterCsvFile.inputStream().use { input -> input.copyTo(out) }
                    }
                }
            } catch (_: Exception) {}
        }

        // 5. Launch Android System Share Chooser
        withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, contentUris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(shareIntent, "Share MAF Workouts (TCX / CSV)"))
            Toast.makeText(
                activity,
                "Exported ${generatedFiles.size} files to Downloads/MAFRunner",
                Toast.LENGTH_LONG
            ).show()
        }

        generatedFiles.size
    }
}
