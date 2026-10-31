package com.mafrunner.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class WorkoutDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "maf_runner.db"
        const val DATABASE_VERSION = 1

        const val TABLE_WORKOUTS = "workouts"
        const val TABLE_SAMPLES = "hr_samples"

        // Workouts Columns
        const val COL_ID = "id"
        const val COL_START_TIME = "start_time"
        const val COL_END_TIME = "end_time"
        const val COL_DURATION_SEC = "duration_sec"
        const val COL_UNDER_SEC = "under_sec"
        const val COL_IN_ZONE_SEC = "in_zone_sec"
        const val COL_OVER_SEC = "over_sec"
        const val COL_AVG_HR = "avg_hr"
        const val COL_MAX_HR = "max_hr"
        const val COL_AVG_CADENCE = "avg_cadence"
        const val COL_TREADMILL_SPEED = "treadmill_speed"
        const val COL_TREADMILL_DISTANCE = "treadmill_distance"
        const val COL_IS_BENCHMARK = "is_benchmark"

        // Samples Columns
        const val COL_WORKOUT_ID = "workout_id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_HR = "hr"
        const val COL_CADENCE = "cadence"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_WORKOUTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_START_TIME INTEGER NOT NULL,
                $COL_END_TIME INTEGER NOT NULL,
                $COL_DURATION_SEC INTEGER NOT NULL,
                $COL_UNDER_SEC INTEGER NOT NULL,
                $COL_IN_ZONE_SEC INTEGER NOT NULL,
                $COL_OVER_SEC INTEGER NOT NULL,
                $COL_AVG_HR INTEGER NOT NULL,
                $COL_MAX_HR INTEGER NOT NULL,
                $COL_AVG_CADENCE INTEGER NOT NULL,
                $COL_TREADMILL_SPEED REAL,
                $COL_TREADMILL_DISTANCE REAL,
                $COL_IS_BENCHMARK INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SAMPLES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_WORKOUT_ID INTEGER NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_HR INTEGER NOT NULL,
                $COL_CADENCE INTEGER NOT NULL,
                FOREIGN KEY ($COL_WORKOUT_ID) REFERENCES $TABLE_WORKOUTS ($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SAMPLES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WORKOUTS")
        onCreate(db)
    }
}
