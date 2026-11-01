package com.mafrunner.app.data.repository

import android.content.ContentValues
import android.content.Context
import com.mafrunner.app.data.local.HeartRateSampleEntity
import com.mafrunner.app.data.local.WorkoutDatabaseHelper
import com.mafrunner.app.data.local.WorkoutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class WorkoutRepository(context: Context) {

    private val dbHelper = WorkoutDatabaseHelper(context)
    private val _workoutsFlow = MutableStateFlow<List<WorkoutEntity>>(emptyList())
    val workoutsFlow: Flow<List<WorkoutEntity>> = _workoutsFlow.asStateFlow()

    init {
        refreshWorkouts()
    }

    fun refreshWorkouts() {
        val list = mutableListOf<WorkoutEntity>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            WorkoutDatabaseHelper.TABLE_WORKOUTS,
            null, null, null, null, null,
            "${WorkoutDatabaseHelper.COL_START_TIME} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    WorkoutEntity(
                        id = it.getLong(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_ID)),
                        startTime = it.getLong(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_START_TIME)),
                        endTime = it.getLong(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_END_TIME)),
                        durationSec = it.getLong(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_DURATION_SEC)),
                        underSec = it.getLong(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_UNDER_SEC)),
                        inZoneSec = it.getLong(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_IN_ZONE_SEC)),
                        overSec = it.getLong(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_OVER_SEC)),
                        avgHr = it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_AVG_HR)),
                        maxHr = it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_MAX_HR)),
                        avgCadence = it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_AVG_CADENCE)),
                        treadmillSpeed = if (!it.isNull(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_TREADMILL_SPEED))) it.getFloat(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_TREADMILL_SPEED)) else null,
                        treadmillDistance = if (!it.isNull(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_TREADMILL_DISTANCE))) it.getFloat(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_TREADMILL_DISTANCE)) else null,
                        isBenchmark = it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_IS_BENCHMARK)) == 1,
                        isGuidedTest = it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_IS_GUIDED_TEST)) == 1,
                        targetDurationSec = it.getLong(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_TARGET_DURATION_SEC)),
                        recoveryPeakHr = if (!it.isNull(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_RECOVERY_HR_PEAK))) it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_RECOVERY_HR_PEAK)) else null,
                        recoveryHr60 = if (!it.isNull(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_RECOVERY_HR_60))) it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_RECOVERY_HR_60)) else null,
                        recoveryHr120 = if (!it.isNull(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_RECOVERY_HR_120))) it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_RECOVERY_HR_120)) else null,
                        recoveryDrop60 = if (!it.isNull(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_RECOVERY_DROP_60))) it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_RECOVERY_DROP_60)) else null,
                        recoveryDrop120 = if (!it.isNull(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_RECOVERY_DROP_120))) it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_RECOVERY_DROP_120)) else null
                    )
                )
            }
        }
        _workoutsFlow.value = list
    }

    suspend fun saveWorkout(
        workout: WorkoutEntity,
        samples: List<HeartRateSampleEntity>
    ): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(WorkoutDatabaseHelper.COL_START_TIME, workout.startTime)
                put(WorkoutDatabaseHelper.COL_END_TIME, workout.endTime)
                put(WorkoutDatabaseHelper.COL_DURATION_SEC, workout.durationSec)
                put(WorkoutDatabaseHelper.COL_UNDER_SEC, workout.underSec)
                put(WorkoutDatabaseHelper.COL_IN_ZONE_SEC, workout.inZoneSec)
                put(WorkoutDatabaseHelper.COL_OVER_SEC, workout.overSec)
                put(WorkoutDatabaseHelper.COL_AVG_HR, workout.avgHr)
                put(WorkoutDatabaseHelper.COL_MAX_HR, workout.maxHr)
                put(WorkoutDatabaseHelper.COL_AVG_CADENCE, workout.avgCadence)
                workout.treadmillSpeed?.let { put(WorkoutDatabaseHelper.COL_TREADMILL_SPEED, it) }
                workout.treadmillDistance?.let { put(WorkoutDatabaseHelper.COL_TREADMILL_DISTANCE, it) }
                put(WorkoutDatabaseHelper.COL_IS_BENCHMARK, if (workout.isBenchmark) 1 else 0)
                put(WorkoutDatabaseHelper.COL_IS_GUIDED_TEST, if (workout.isGuidedTest) 1 else 0)
                put(WorkoutDatabaseHelper.COL_TARGET_DURATION_SEC, workout.targetDurationSec)
                workout.recoveryPeakHr?.let { put(WorkoutDatabaseHelper.COL_RECOVERY_HR_PEAK, it) }
                workout.recoveryHr60?.let { put(WorkoutDatabaseHelper.COL_RECOVERY_HR_60, it) }
                workout.recoveryHr120?.let { put(WorkoutDatabaseHelper.COL_RECOVERY_HR_120, it) }
                workout.recoveryDrop60?.let { put(WorkoutDatabaseHelper.COL_RECOVERY_DROP_60, it) }
                workout.recoveryDrop120?.let { put(WorkoutDatabaseHelper.COL_RECOVERY_DROP_120, it) }
            }
            val workoutId = db.insert(WorkoutDatabaseHelper.TABLE_WORKOUTS, null, values)

            for (sample in samples) {
                val sampleValues = ContentValues().apply {
                    put(WorkoutDatabaseHelper.COL_WORKOUT_ID, workoutId)
                    put(WorkoutDatabaseHelper.COL_TIMESTAMP, sample.timestamp)
                    put(WorkoutDatabaseHelper.COL_HR, sample.hr)
                    put(WorkoutDatabaseHelper.COL_CADENCE, sample.cadence)
                }
                db.insert(WorkoutDatabaseHelper.TABLE_SAMPLES, null, sampleValues)
            }

            db.setTransactionSuccessful()
            refreshWorkouts()
            workoutId
        } finally {
            db.endTransaction()
        }
    }

    suspend fun updateWorkoutDetails(
        workoutId: Long,
        speed: Float?,
        distance: Float?,
        isBenchmark: Boolean,
        durationSec: Long? = null
    ): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            if (durationSec != null) {
                put(WorkoutDatabaseHelper.COL_DURATION_SEC, durationSec)
            }
            if (speed != null) {
                put(WorkoutDatabaseHelper.COL_TREADMILL_SPEED, speed)
            } else {
                putNull(WorkoutDatabaseHelper.COL_TREADMILL_SPEED)
            }
            if (distance != null) {
                put(WorkoutDatabaseHelper.COL_TREADMILL_DISTANCE, distance)
            } else {
                putNull(WorkoutDatabaseHelper.COL_TREADMILL_DISTANCE)
            }
            put(WorkoutDatabaseHelper.COL_IS_BENCHMARK, if (isBenchmark) 1 else 0)
        }
        db.update(
            WorkoutDatabaseHelper.TABLE_WORKOUTS,
            values,
            "${WorkoutDatabaseHelper.COL_ID} = ?",
            arrayOf(workoutId.toString())
        )
        refreshWorkouts()
    }

    suspend fun deleteWorkout(workoutId: Long): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                WorkoutDatabaseHelper.TABLE_SAMPLES,
                "${WorkoutDatabaseHelper.COL_WORKOUT_ID} = ?",
                arrayOf(workoutId.toString())
            )
            val rows = db.delete(
                WorkoutDatabaseHelper.TABLE_WORKOUTS,
                "${WorkoutDatabaseHelper.COL_ID} = ?",
                arrayOf(workoutId.toString())
            )
            db.setTransactionSuccessful()
            refreshWorkouts()
            rows > 0
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getWorkoutById(workoutId: Long): WorkoutEntity? = withContext(Dispatchers.IO) {
        _workoutsFlow.value.find { it.id == workoutId }
    }

    suspend fun getSamplesForWorkout(workoutId: Long): List<HeartRateSampleEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<HeartRateSampleEntity>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            WorkoutDatabaseHelper.TABLE_SAMPLES,
            null,
            "${WorkoutDatabaseHelper.COL_WORKOUT_ID} = ?",
            arrayOf(workoutId.toString()),
            null, null,
            "${WorkoutDatabaseHelper.COL_TIMESTAMP} ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    HeartRateSampleEntity(
                        id = it.getLong(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_ID)),
                        workoutId = it.getLong(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_WORKOUT_ID)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_TIMESTAMP)),
                        hr = it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_HR)),
                        cadence = it.getInt(it.getColumnIndexOrThrow(WorkoutDatabaseHelper.COL_CADENCE))
                    )
                )
            }
        }
        list
    }
}
