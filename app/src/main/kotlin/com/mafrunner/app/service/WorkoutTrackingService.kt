package com.mafrunner.app.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mafrunner.app.MainActivity
import com.mafrunner.app.R
import com.mafrunner.app.audio.AudioAlertEngine
import com.mafrunner.app.audio.AudioAlertEvaluator
import com.mafrunner.app.ble.BleHeartRateManager
import com.mafrunner.app.cadence.AcousticCadenceDetector
import com.mafrunner.app.data.local.HeartRateSampleEntity
import com.mafrunner.app.data.local.WorkoutEntity
import com.mafrunner.app.data.repository.WorkoutRepository
import com.mafrunner.app.domain.MafCalculator
import com.mafrunner.app.domain.MafCategory
import com.mafrunner.app.domain.MafZone
import com.mafrunner.app.domain.MafZones
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WorkoutTrackingService : Service() {

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): WorkoutTrackingService = this@WorkoutTrackingService
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    lateinit var bleManager: BleHeartRateManager
        private set
    lateinit var cadenceDetector: AcousticCadenceDetector
        private set
    private lateinit var alertEngine: AudioAlertEngine
    private lateinit var repository: WorkoutRepository
    private val alertEvaluator = AudioAlertEvaluator()
    private val mafCalculator = MafCalculator()

    private val _sessionState = MutableStateFlow(WorkoutSessionState())
    val sessionState: StateFlow<WorkoutSessionState> = _sessionState.asStateFlow()

    private val samples = mutableListOf<HeartRateSampleEntity>()
    private var activeZones = MafZones(ceiling = 140, targetMin = 135, floor = 130)

    private var workoutStartTime = 0L
    private var lastCeilingAlertMs = 0L
    private var lastFloorAlertMs = 0L
    private var timerJob: Job? = null

    companion object {
        const val CHANNEL_ID = "maf_workout_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        bleManager = BleHeartRateManager(this)
        cadenceDetector = AcousticCadenceDetector()
        alertEngine = AudioAlertEngine(this)
        repository = WorkoutRepository(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MafRunner:WorkoutWakeLock")

        createNotificationChannel()

        // Observe BLE heart rate
        scope.launch {
            bleManager.heartRateFlow.collect { hr ->
                val currentState = _sessionState.value
                _sessionState.value = currentState.copy(currentHr = hr)

                if (currentState.isActive && !currentState.isPaused && hr != null) {
                    val now = System.currentTimeMillis()
                    if (alertEvaluator.shouldTriggerCeilingAlert(hr, activeZones.ceiling, lastCeilingAlertMs, now)) {
                        alertEngine.playCeilingAlert()
                        lastCeilingAlertMs = now
                    } else if (alertEvaluator.shouldTriggerFloorAlert(hr, activeZones.floor, lastFloorAlertMs, now)) {
                        alertEngine.playFloorAlert()
                        lastFloorAlertMs = now
                    }
                }
            }
        }

        // Observe Cadence
        scope.launch {
            cadenceDetector.cadenceSpm.collect { spm ->
                _sessionState.value = _sessionState.value.copy(currentCadence = spm)
            }
        }
    }

    fun configureZones(age: Int, category: MafCategory, manualCeiling: Int? = null) {
        activeZones = mafCalculator.calculate(age, category, manualCeiling)
    }

    fun startWorkout() {
        samples.clear()
        workoutStartTime = System.currentTimeMillis()
        wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hours max

        _sessionState.value = WorkoutSessionState(isActive = true, isPaused = false)
        cadenceDetector.start()

        startForegroundService()

        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val current = _sessionState.value
                if (current.isActive && !current.isPaused) {
                    val currentHr = current.currentHr ?: 0
                    val currentCadence = current.currentCadence

                    var under = current.underSeconds
                    var inZone = current.inZoneSeconds
                    var over = current.overSeconds

                    if (currentHr > 0) {
                        when (mafCalculator.evaluateZone(currentHr, activeZones)) {
                            MafZone.BELOW_FLOOR -> under++
                            MafZone.MAF_TARGET -> inZone++
                            MafZone.OVER_CEILING -> over++
                        }
                        samples.add(
                            HeartRateSampleEntity(
                                workoutId = 0L,
                                timestamp = System.currentTimeMillis(),
                                hr = currentHr,
                                cadence = currentCadence
                            )
                        )
                    }

                    _sessionState.value = current.copy(
                        elapsedSeconds = current.elapsedSeconds + 1,
                        underSeconds = under,
                        inZoneSeconds = inZone,
                        overSeconds = over
                    )
                    updateNotification()
                }
            }
        }
    }

    fun pauseWorkout() {
        _sessionState.value = _sessionState.value.copy(isPaused = true)
        updateNotification()
    }

    fun resumeWorkout() {
        _sessionState.value = _sessionState.value.copy(isPaused = false)
        updateNotification()
    }

    suspend fun finishWorkout(
        treadmillSpeed: Float? = null,
        treadmillDistance: Float? = null,
        isBenchmark: Boolean = false
    ): WorkoutEntity? {
        val current = _sessionState.value
        timerJob?.cancel()
        timerJob = null

        cadenceDetector.stop()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        _sessionState.value = WorkoutSessionState(isActive = false)

        if (current.elapsedSeconds < 5) return null // Too short to record

        val avgHr = if (samples.isNotEmpty()) samples.map { it.hr }.average().toInt() else 0
        val maxHr = if (samples.isNotEmpty()) samples.maxOf { it.hr } else 0
        val avgCadence = if (samples.isNotEmpty()) samples.map { it.cadence }.filter { it > 0 }.let { if (it.isNotEmpty()) it.average().toInt() else 0 } else 0

        val workout = WorkoutEntity(
            startTime = workoutStartTime,
            endTime = System.currentTimeMillis(),
            durationSec = current.elapsedSeconds,
            underSec = current.underSeconds,
            inZoneSec = current.inZoneSeconds,
            overSec = current.overSeconds,
            avgHr = avgHr,
            maxHr = maxHr,
            avgCadence = avgCadence,
            treadmillSpeed = treadmillSpeed,
            treadmillDistance = treadmillDistance,
            isBenchmark = isBenchmark
        )

        val id = repository.saveWorkout(workout, samples)
        return workout.copy(id = id)
    }

    private fun startForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = _sessionState.value
        val hrText = state.currentHr?.let { "$it BPM" } ?: "-- BPM"
        val statusText = if (state.isPaused) "PAUSED" else "RUNNING"
        val contentText = "$statusText • $hrText • Cadence: ${state.currentCadence} SPM • ${state.formattedTime}"

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MAF Runner Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MAF Runner Workout Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live running stats during active treadmill sessions"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        timerJob?.cancel()
        cadenceDetector.stop()
        alertEngine.release()
        bleManager.disconnect()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        scope.cancel()
        super.onDestroy()
    }
}
