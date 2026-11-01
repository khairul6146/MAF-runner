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
import com.mafrunner.app.domain.TestPhase
import com.mafrunner.app.domain.TestProtocolEngine
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
    lateinit var repository: WorkoutRepository
    private val alertEvaluator = AudioAlertEvaluator()
    private val mafCalculator = MafCalculator()

    private val _sessionState = MutableStateFlow(WorkoutSessionState())
    val sessionState: StateFlow<WorkoutSessionState> = _sessionState.asStateFlow()

    private val samples = mutableListOf<HeartRateSampleEntity>()
    private var activeZones = MafZones(ceiling = 140, targetMin = 135, floor = 130)

    private var workoutStartTime = 0L
    private var lastCeilingAlertMs = 0L
    private var lastFloorAlertMs = 0L
    private var cadenceRecordingEnabled = true
    private var timerJob: Job? = null

    private var testProtocolEngine: TestProtocolEngine? = null
    private var previousTestPhase: TestPhase = TestPhase.FREE_RUN

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
                    } else if (currentState.elapsedSeconds >= 30 && alertEvaluator.shouldTriggerFloorAlert(hr, activeZones.floor, lastFloorAlertMs, now)) {
                        alertEngine.playFloorAlert()
                        lastFloorAlertMs = now
                    }
                }
            }
        }

        // Observe BLE strap battery
        scope.launch {
            bleManager.batteryLevelFlow.collect { battery ->
                _sessionState.value = _sessionState.value.copy(strapBatteryLevel = battery)
            }
        }

        // Observe Cadence
        scope.launch {
            cadenceDetector.cadenceSpm.collect { spm ->
                _sessionState.value = _sessionState.value.copy(currentCadence = spm)
            }
        }
        scope.launch {
            cadenceDetector.lastStrikeTimestampMs.collect { timestamp ->
                if (timestamp > 0L) {
                    _sessionState.value = _sessionState.value.copy(lastStrikeTimestampMs = timestamp)
                }
            }
        }
    }

    fun configureZones(age: Int, category: MafCategory, manualCeiling: Int? = null) {
        activeZones = mafCalculator.calculate(age, category, manualCeiling)
    }

    fun startWorkout(
        enableCadence: Boolean = true,
        isGuidedTest: Boolean = false,
        warmupDurationSec: Long = 0L,
        targetTestDurationSec: Long = 1800L
    ) {
        samples.clear()
        workoutStartTime = System.currentTimeMillis()
        wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hours max

        testProtocolEngine = if (isGuidedTest) {
            TestProtocolEngine(
                warmupDurationSec = warmupDurationSec,
                targetTestDurationSec = targetTestDurationSec
            )
        } else {
            null
        }
        previousTestPhase = testProtocolEngine?.currentPhase ?: TestPhase.FREE_RUN

        _sessionState.value = WorkoutSessionState(
            isActive = true,
            isPaused = false,
            isGuidedTest = isGuidedTest,
            testPhase = testProtocolEngine?.currentPhase ?: TestPhase.FREE_RUN,
            phaseRemainingSeconds = testProtocolEngine?.phaseRemainingSeconds ?: 0L,
            targetTestDurationSeconds = targetTestDurationSec
        )
        cadenceRecordingEnabled = enableCadence
        if (enableCadence) {
            cadenceDetector.start()
        }

        startForegroundService()

        if (isGuidedTest) {
            if (warmupDurationSec > 0L) {
                alertEngine.speechCoach.speakWarmupStart()
            } else {
                val targetMid = activeZones.targetMin + (activeZones.ceiling - activeZones.targetMin) / 2
                alertEngine.speechCoach.speakTestStart((targetTestDurationSec / 60).toInt(), targetMid)
            }
        }

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

                    var currentZone: MafZone? = null
                    if (currentHr > 0) {
                        currentZone = mafCalculator.evaluateZone(currentHr, activeZones)
                        when (currentZone) {
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

                    val engine = testProtocolEngine
                    if (engine != null) {
                        val newPhase = engine.tick(currentHr, currentCadence, currentZone)

                        if (newPhase != previousTestPhase) {
                            when (newPhase) {
                                TestPhase.TEST_STEADY -> {
                                    val targetMid = activeZones.targetMin + (activeZones.ceiling - activeZones.targetMin) / 2
                                    alertEngine.speechCoach.speakTestStart((engine.targetTestDurationSec / 60).toInt(), targetMid)
                                }
                                TestPhase.RECOVERY -> {
                                    cadenceDetector.stop()
                                    alertEngine.speechCoach.speakRecoveryStart()
                                }
                                TestPhase.COMPLETE -> {
                                    val drop60 = engine.recoveryDrop60 ?: 0
                                    val drop120 = engine.recoveryDrop120 ?: 0
                                    alertEngine.speechCoach.speakRecoveryComplete(drop60, drop120)
                                    showTestCompleteNotification(drop60, drop120)
                                }
                                else -> {}
                            }
                            previousTestPhase = newPhase
                        }

                        // Voice coach triggers when out of zone during steady test
                        if (newPhase == TestPhase.TEST_STEADY && currentHr > 0) {
                            val now = System.currentTimeMillis()
                            if (currentZone == MafZone.OVER_CEILING) {
                                alertEngine.speechCoach.speakCeilingWarning(currentHr, now)
                            } else if (current.elapsedSeconds >= 30 && currentZone == MafZone.BELOW_FLOOR) {
                                alertEngine.speechCoach.speakFloorWarning(currentHr, now)
                            }
                        }

                        _sessionState.value = current.copy(
                            elapsedSeconds = current.elapsedSeconds + 1,
                            underSeconds = under,
                            inZoneSeconds = inZone,
                            overSeconds = over,
                            testPhase = engine.currentPhase,
                            phaseRemainingSeconds = engine.phaseRemainingSeconds,
                            recoveryPeakHr = engine.recoveryPeakHr,
                            recoveryHr60 = engine.recoveryHr60,
                            recoveryHr120 = engine.recoveryHr120,
                            recoveryDrop60 = engine.recoveryDrop60,
                            recoveryDrop120 = engine.recoveryDrop120,
                            testUnderSeconds = engine.testUnderSeconds,
                            testInZoneSeconds = engine.testInZoneSeconds,
                            testOverSeconds = engine.testOverSeconds,
                            testAvgHr = engine.testAvgHr,
                            testAvgCadence = engine.testAvgCadence
                        )
                    } else {
                        _sessionState.value = current.copy(
                            elapsedSeconds = current.elapsedSeconds + 1,
                            underSeconds = under,
                            inZoneSeconds = inZone,
                            overSeconds = over
                        )
                    }
                    updateNotification()
                }
            }
        }
    }

    fun skipWarmup() {
        testProtocolEngine?.let { engine ->
            engine.skipWarmup()
            previousTestPhase = engine.currentPhase
            val targetMid = activeZones.targetMin + (activeZones.ceiling - activeZones.targetMin) / 2
            alertEngine.speechCoach.speakTestStart((engine.targetTestDurationSec / 60).toInt(), targetMid)
            _sessionState.value = _sessionState.value.copy(
                testPhase = engine.currentPhase,
                phaseRemainingSeconds = engine.phaseRemainingSeconds
            )
            updateNotification()
        }
    }

    fun adjustTestDuration(deltaSeconds: Long) {
        testProtocolEngine?.let { engine ->
            engine.addTimeSeconds(deltaSeconds)
            _sessionState.value = _sessionState.value.copy(
                phaseRemainingSeconds = engine.phaseRemainingSeconds,
                targetTestDurationSeconds = engine.targetTestDurationSec
            )
            updateNotification()
        }
    }

    fun startRecoveryEarly() {
        val currentHr = _sessionState.value.currentHr
        testProtocolEngine?.let { engine ->
            engine.startRecoveryEarly(currentHr)
            previousTestPhase = engine.currentPhase
            cadenceDetector.stop()
            alertEngine.speechCoach.speakRecoveryStart()
            _sessionState.value = _sessionState.value.copy(
                testPhase = engine.currentPhase,
                phaseRemainingSeconds = engine.phaseRemainingSeconds,
                recoveryPeakHr = engine.recoveryPeakHr
            )
            updateNotification()
        }
    }

    fun pauseWorkout() {
        _sessionState.value = _sessionState.value.copy(isPaused = true)
        cadenceDetector.stop()
        updateNotification()
    }

    fun resumeWorkout() {
        _sessionState.value = _sessionState.value.copy(isPaused = false)
        if (cadenceRecordingEnabled && _sessionState.value.testPhase != TestPhase.RECOVERY) {
            cadenceDetector.start()
        }
        updateNotification()
    }

    suspend fun finishWorkout(
        treadmillSpeed: Float? = null,
        treadmillDistance: Float? = null,
        isBenchmark: Boolean = false
    ): WorkoutEntity? {
        val current = _sessionState.value
        val engine = testProtocolEngine
        timerJob?.cancel()
        timerJob = null

        cadenceDetector.stop()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        _sessionState.value = WorkoutSessionState(isActive = false)

        if (current.elapsedSeconds < 5) return null // Too short to record

        val isGuided = engine != null
        val isBench = if (isGuided) true else isBenchmark

        val avgHr = if (isGuided && engine.testAvgHr > 0) {
            engine.testAvgHr
        } else if (samples.isNotEmpty()) {
            samples.map { it.hr }.average().toInt()
        } else 0

        val maxHr = if (samples.isNotEmpty()) samples.maxOf { it.hr } else 0

        val avgCadence = if (isGuided && engine.testAvgCadence > 0) {
            engine.testAvgCadence
        } else if (samples.isNotEmpty()) {
            samples.map { it.cadence }.filter { it > 0 }.let { if (it.isNotEmpty()) it.average().toInt() else 0 }
        } else 0

        val underSec = if (isGuided) engine.testUnderSeconds else current.underSeconds
        val inZoneSec = if (isGuided) engine.testInZoneSeconds else current.inZoneSeconds
        val overSec = if (isGuided) engine.testOverSeconds else current.overSeconds

        val workout = WorkoutEntity(
            startTime = workoutStartTime,
            endTime = System.currentTimeMillis(),
            durationSec = current.elapsedSeconds,
            underSec = underSec,
            inZoneSec = inZoneSec,
            overSec = overSec,
            avgHr = avgHr,
            maxHr = maxHr,
            avgCadence = avgCadence,
            treadmillSpeed = treadmillSpeed,
            treadmillDistance = treadmillDistance,
            isBenchmark = isBench,
            isGuidedTest = isGuided,
            targetDurationSec = engine?.targetTestDurationSec ?: 0L,
            recoveryPeakHr = engine?.recoveryPeakHr,
            recoveryHr60 = engine?.recoveryHr60,
            recoveryHr120 = engine?.recoveryHr120,
            recoveryDrop60 = engine?.recoveryDrop60,
            recoveryDrop120 = engine?.recoveryDrop120
        )

        val id = repository.saveWorkout(workout, samples)
        testProtocolEngine = null
        return workout.copy(id = id)
    }

    private fun showTestCompleteNotification(drop60: Int, drop120: Int) {
        val notifyIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MAF Test & Recovery Complete!")
            .setContentText("HR Drop: -$drop60 bpm (1m), -$drop120 bpm (2m). Tap to log treadmill data.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun startForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
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
