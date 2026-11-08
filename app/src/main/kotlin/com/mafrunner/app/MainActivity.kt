package com.mafrunner.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mafrunner.app.ble.BleConnectionState
import com.mafrunner.app.data.local.WorkoutEntity
import com.mafrunner.app.data.repository.WorkoutRepository
import com.mafrunner.app.domain.MafCategory
import com.mafrunner.app.domain.MafCalculator
import com.mafrunner.app.domain.MafZones
import com.mafrunner.app.service.WorkoutSessionState
import com.mafrunner.app.service.WorkoutTrackingService
import com.mafrunner.app.export.ExportManager
import com.mafrunner.app.ui.screens.*
import com.mafrunner.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private var workoutService by mutableStateOf<WorkoutTrackingService?>(null)
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WorkoutTrackingService.LocalBinder
            workoutService = binder.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            workoutService = null
            isServiceBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAppPermissions()

        val serviceIntent = Intent(this, WorkoutTrackingService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            MafRunnerTheme {
                MainAppContainer(workoutService = workoutService, activity = this)
            }
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onDestroy() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        super.onDestroy()
    }
}

@Composable
fun MainAppContainer(
    workoutService: WorkoutTrackingService?,
    activity: ComponentActivity
) {
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(0) } // 0: Setup, 1: Console, 2: History

    // Profile state
    var age by remember { mutableStateOf(35) }
    var category by remember { mutableStateOf(MafCategory.BEGINNER_RETURN) }
    val mafCalculator = remember { MafCalculator() }
    val zones = remember(age, category) { mafCalculator.calculate(age, category) }

    // Cadence State
    var cadenceEnabled by remember { mutableStateOf(true) }
    var cadenceSensitivity by remember { mutableStateOf(7f) }

    // Dialog States
    var showDevicePicker by remember { mutableStateOf(false) }
    var showTestSetup by remember { mutableStateOf(false) }
    var summaryWorkout by remember { mutableStateOf<WorkoutEntity?>(null) }

    // Service States
    val sessionState by workoutService?.sessionState?.collectAsState() ?: remember { mutableStateOf(WorkoutSessionState()) }
    val bleState by workoutService?.bleManager?.connectionState?.collectAsState() ?: remember { mutableStateOf(BleConnectionState.DISCONNECTED) }
    val bleBattery by workoutService?.bleManager?.batteryLevelFlow?.collectAsState() ?: remember { mutableStateOf(null) }
    val discoveredDevices by workoutService?.bleManager?.discoveredDevices?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val savedMac = workoutService?.bleManager?.getSavedMacAddress()

    // Real-time Cadence Telemetry Flows
    val liveEnergy by (workoutService?.cadenceDetector?.liveEnergy ?: remember { MutableStateFlow(0f) }).collectAsState()
    val audioThreshold by (workoutService?.cadenceDetector?.currentThreshold ?: remember { MutableStateFlow(0.6f) }).collectAsState()
    val lastStrikeTimeMs by (workoutService?.cadenceDetector?.lastStrikeTimestampMs ?: remember { MutableStateFlow(0L) }).collectAsState()

    // Real-time ECG Telemetry Flow
    val ecgSamples by (workoutService?.bleManager?.ecgSamplesFlow ?: remember { MutableStateFlow(emptyList<Int>()) }).collectAsState()

    // Real-time audio calibration preview on Setup Screen
    LaunchedEffect(currentTab, cadenceEnabled, sessionState.isActive, workoutService) {
        if (currentTab == 0 && cadenceEnabled && !sessionState.isActive) {
            workoutService?.cadenceDetector?.setSensitivity(cadenceSensitivity)
            workoutService?.cadenceDetector?.start()
        } else if (!sessionState.isActive) {
            workoutService?.cadenceDetector?.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (workoutService?.sessionState?.value?.isActive != true) {
                workoutService?.cadenceDetector?.stop()
            }
        }
    }

    // Workouts DB
    val repository = remember { WorkoutRepository(activity) }
    val workouts by repository.workoutsFlow.collectAsState(initial = emptyList())

    // Keep screen on during Console mode
    DisposableEffect(currentTab) {
        if (currentTab == 1) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0F1012)) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    label = { Text("Setup", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Text("1", fontWeight = FontWeight.Black, fontSize = 14.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = NordicWhite,
                        unselectedTextColor = NordicMuted,
                        indicatorColor = NordicCardBorder
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    label = { Text("Console", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Text("2", fontWeight = FontWeight.Black, fontSize = 14.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = NordicEmerald,
                        unselectedTextColor = NordicMuted,
                        indicatorColor = NordicCardBorder
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    label = { Text("History", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Text("3", fontWeight = FontWeight.Black, fontSize = 14.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = NordicCyan,
                        unselectedTextColor = NordicMuted,
                        indicatorColor = NordicCardBorder
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                0 -> SetupScreen(
                    age = age,
                    onAgeChange = { age = it },
                    category = category,
                    onCategoryChange = { category = it },
                    zones = zones,
                    bleState = bleState,
                    savedMac = savedMac,
                    onScanBle = {
                        workoutService?.bleManager?.startScan()
                        showDevicePicker = true
                    },
                    cadenceEnabled = cadenceEnabled,
                    onToggleCadence = { cadenceEnabled = it },
                    cadenceSensitivity = cadenceSensitivity,
                    onSensitivityChange = {
                        cadenceSensitivity = it
                        workoutService?.cadenceDetector?.setSensitivity(it)
                    },
                    liveAudioEnergy = liveEnergy,
                    audioThreshold = audioThreshold,
                    lastStrikeTimeMs = lastStrikeTimeMs,
                    strapBatteryLevel = bleBattery,
                    onStartWorkout = {
                        showTestSetup = true
                    }
                )
                1 -> ConsoleScreen(
                    state = sessionState,
                    zones = zones,
                    onPause = { workoutService?.pauseWorkout() },
                    onResume = { workoutService?.resumeWorkout() },
                    onFinish = {
                        scope.launch {
                            val saved = workoutService?.finishWorkout()
                            if (saved != null) {
                                summaryWorkout = saved
                            } else {
                                currentTab = 0
                            }
                        }
                    },
                    onSkipWarmup = { workoutService?.skipWarmup() },
                    onAdjustDuration = { deltaSec -> workoutService?.adjustTestDuration(deltaSec) },
                    onStartRecoveryEarly = { workoutService?.startRecoveryEarly() },
                    ecgSamples = ecgSamples
                )
                2 -> HistoryScreen(
                    workouts = workouts,
                    onExportAll = {
                        scope.launch {
                            val repo = workoutService?.repository ?: WorkoutRepository(activity)
                            ExportManager.exportAll(activity, workouts, repo)
                        }
                    },
                    onSaveWorkout = { id, durationSec, speed, distance, isBenchmark ->
                        scope.launch {
                            val repo = workoutService?.repository ?: WorkoutRepository(activity)
                            repo.updateWorkoutDetails(
                                workoutId = id,
                                speed = speed,
                                distance = distance,
                                isBenchmark = isBenchmark,
                                durationSec = durationSec
                            )
                        }
                    },
                    onDeleteWorkout = { id ->
                        scope.launch {
                            val repo = workoutService?.repository ?: WorkoutRepository(activity)
                            repo.deleteWorkout(id)
                        }
                    },
                    onExportSingle = { workout ->
                        scope.launch {
                            val repo = workoutService?.repository ?: WorkoutRepository(activity)
                            ExportManager.exportSingle(activity, workout, repo)
                        }
                    }
                )
            }

            // Dialogs
            if (showTestSetup) {
                TestSetupDialog(
                    onDismiss = { showTestSetup = false },
                    onStart = { isGuided, warmupSec, testSec ->
                        showTestSetup = false
                        workoutService?.configureZones(age, category)
                        workoutService?.startWorkout(
                            enableCadence = cadenceEnabled,
                            isGuidedTest = isGuided,
                            warmupDurationSec = warmupSec,
                            targetTestDurationSec = testSec
                        )
                        currentTab = 1
                    }
                )
            }

            if (showDevicePicker) {
                DevicePickerDialog(
                    devices = discoveredDevices,
                    onSelectDevice = { mac ->
                        workoutService?.bleManager?.connect(mac)
                        showDevicePicker = false
                    },
                    onDismiss = {
                        workoutService?.bleManager?.stopScan()
                        showDevicePicker = false
                    }
                )
            }

            summaryWorkout?.let { workout ->
                PostRunSummaryDialog(
                    workout = workout,
                    onSave = { speed, distance, isBenchmark ->
                        scope.launch {
                            val repo = workoutService?.repository ?: WorkoutRepository(activity)
                            repo.updateWorkoutDetails(workout.id, speed, distance, isBenchmark)
                        }
                        summaryWorkout = null
                        currentTab = 2
                    },
                    onDismiss = {
                        summaryWorkout = null
                        currentTab = 2
                    }
                )
            }
        }
    }
}
