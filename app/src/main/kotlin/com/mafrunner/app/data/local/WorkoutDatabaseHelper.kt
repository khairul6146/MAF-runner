package com.mafrunner.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class WorkoutDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "maf_runner.db"
        const val DATABASE_VERSION = 2

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
        const val COL_IS_GUIDED_TEST = "is_guided_test"
        const val COL_TARGET_DURATION_SEC = "target_duration_sec"
        const val COL_RECOVERY_HR_PEAK = "recovery_hr_peak"
        const val COL_RECOVERY_HR_60 = "recovery_hr_60"
        const val COL_RECOVERY_HR_120 = "recovery_hr_120"
        const val COL_RECOVERY_DROP_60 = "recovery_drop_60"
        const val COL_RECOVERY_DROP_120 = "recovery_drop_120"

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
                $COL_IS_BENCHMARK INTEGER NOT NULL DEFAULT 0,
                $COL_IS_GUIDED_TEST INTEGER NOT NULL DEFAULT 0,
                $COL_TARGET_DURATION_SEC INTEGER NOT NULL DEFAULT 0,
                $COL_RECOVERY_HR_PEAK INTEGER,
                $COL_RECOVERY_HR_60 INTEGER,
                $COL_RECOVERY_HR_120 INTEGER,
                $COL_RECOVERY_DROP_60 INTEGER,
                $COL_RECOVERY_DROP_120 INTEGER
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
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_WORKOUTS ADD COLUMN $COL_IS_GUIDED_TEST INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_WORKOUTS ADD COLUMN $COL_TARGET_DURATION_SEC INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_WORKOUTS ADD COLUMN $COL_RECOVERY_HR_PEAK INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE $TABLE_WORKOUTS ADD COLUMN $COL_RECOVERY_HR_60 INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE $TABLE_WORKOUTS ADD COLUMN $COL_RECOVERY_HR_120 INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE $TABLE_WORKOUTS ADD COLUMN $COL_RECOVERY_DROP_60 INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE $TABLE_WORKOUTS ADD COLUMN $COL_RECOVERY_DROP_120 INTEGER DEFAULT NULL")
        }
    }
}
