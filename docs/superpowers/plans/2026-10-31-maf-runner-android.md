# MAF Runner Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-ready, standalone Android application (`.apk`) for personal treadmill training using Dr. Phil Maffetone's MAF 180 method, pairing with the Coospo H9Z chest strap via low-latency Bluetooth Low Energy, tracking acoustic footstrike cadence via microphone DSP, and displaying real-time high-contrast Nordic Athletic OLED metrics with instant threshold alerting.

**Architecture:** Modern Native Android using Kotlin 2.x and Jetpack Compose. Bluetooth LE Heart Rate Service (`0x180D`) streams direct byte telemetry into a low-latency alert evaluator and `SoundPool` audio engine. A dedicated PCM audio pipeline (`AudioRecord` + 40–200 Hz IIR bandpass filter + peak detector) extracts footstrike cadence. Workouts and second-by-second telemetry are persisted in a local Room (SQLite) database with TCX/CSV export and managed by an uninterrupted Android Foreground Service.

**Tech Stack:** Kotlin 2.1+, Jetpack Compose, Material 3, Android BLE (`BluetoothGatt`), `AudioRecord` (DSP), `SoundPool`, Android Jetpack Room (SQLite), Kotlin Coroutines & StateFlow, Gradle Wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-31-maf-runner-design.md`](file:///C:/Users/khair/MyProjects/maf-runner-android/docs/superpowers/specs/2026-10-31-maf-runner-design.md)

## Global Constraints
* **Platform:** Android 10+ (minSdk 29, targetSdk 35).
* **Package / Namespace:** `com.mafrunner.app`
* **Hardware Profile:** Universal Bluetooth SIG Heart Rate Service `0000180D-0000-1000-8000-00805F9B34FB` (Coospo H9Z compatible).
* **UI Palette:** Nordic Athletic OLED (Pure Black `#000000`, Nordic Cyan `#00D2FF`, Precision Laser Emerald `#00E676`, Crimson Alert `#FF3B30`, Tabular Numerals).
* **Latency Requirement:** Sub-5ms alert evaluation and audio triggering upon BLE packet arrival; no multi-second rolling-average smoothing on ceiling alarms.
* **Storage:** 100% offline, private local SQLite database; zero external cloud dependencies.

## Review Focus
1. **Bluetooth Disconnection Recovery:** Strap electrode loses contact mid-run; expect app to automatically reconnect within seconds without wiping session elapsed time or crashing.
2. **Audio Alert Overload / Stutter:** HR stays at or above MAF ceiling continuously; expect clean, spaced repeating warning pulses without audio clipping, resource leaks, or overlapping screech.
3. **Acoustic Cadence Noise Rejection:** Treadmill motor hum or footsteps of adjacent treadmill; expect bandpass filter and dynamic refractory debounce (220ms minimum) to prevent artificial doubling or 300+ SPM anomalies.
4. **Foreground Service Survives Backgrounding:** User locks screen or presses Home; expect Foreground Service to maintain BLE connection and continuous workout logging without OS termination.
5. **TCX Distance Validity:** Third-party upload to Strava/Garmin; expect valid XML trackpoints with synthetic cumulative distance distribution so workout does not register as stationary 0 km.

---

### Task 1: Project Scaffolding & Nordic OLED Design Tokens

**Files:**
- Create: `build.gradle.kts` (root)
- Create: `settings.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/mafrunner/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/mafrunner/app/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/mafrunner/app/ui/theme/Type.kt`
- Create: `app/src/test/java/com/mafrunner/app/ColorTokenTest.kt`

**Interfaces:**
- Produces: `NordicBlack`, `NordicCyan`, `NordicEmerald`, `NordicCrimson`, `NordicDarkGray`, `NordicLightGray`, `MafRunnerTheme`

- [ ] **Step 1: Write unit test verifying color hex values and contrast**
```kotlin
package com.mafrunner.app

import com.mafrunner.app.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTokenTest {
    @Test
    fun testNordicOledColors() {
        assertEquals(0xFF000000L, NordicBlack.value.toLong() shr 32 or (NordicBlack.value.toLong() and 0xFFFFFFFFL))
        assertEquals(0xFF00E676L, NordicEmerald.value.toLong() and 0xFFFFFFFFL)
        assertEquals(0xFFFF3B30L, NordicCrimson.value.toLong() and 0xFFFFFFFFL)
        assertEquals(0xFF00D2FFL, NordicCyan.value.toLong() and 0xFFFFFFFFL)
    }
}
```

- [ ] **Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.ColorTokenTest`
Expected: FAIL (unresolved symbols)

- [ ] **Step 3: Scaffold Android project and define Nordic OLED tokens**
Initialize project with Android Gradle Plugin 8.7+, Compose BOM 2024.10.00+, Kotlin 2.1+, and declare permissions in `AndroidManifest.xml` (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_HEALTH`, `POST_NOTIFICATIONS`, `WAKE_LOCK`).

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.ColorTokenTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add .
git commit -m "chore: scaffold android project and nordic oled design tokens"
```

---

### Task 2: MAF 180 Formula & Zone Engine

**Files:**
- Create: `app/src/main/java/com/mafrunner/app/domain/MafCalculator.kt`
- Create: `app/src/main/java/com/mafrunner/app/domain/MafZone.kt`
- Create: `app/src/test/java/com/mafrunner/app/domain/MafCalculatorTest.kt`

**Interfaces:**
- Produces: `enum class MafCategory(val offsetBpm: Int)`, `data class MafZones(val ceiling: Int, val targetMin: Int, val floor: Int)`, `class MafCalculator { fun calculate(age: Int, category: MafCategory, manualCeiling: Int? = null): MafZones; fun evaluateZone(hr: Int, zones: MafZones): MafZone }`

- [ ] **Step 1: Write unit tests for Maffetone formula and zone evaluations**
```kotlin
package com.mafrunner.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MafCalculatorTest {
    private val calculator = MafCalculator()

    @Test
    fun testBaseFormulaWithCategories() {
        // Age 35: Base = 180 - 35 = 145
        assertEquals(135, calculator.calculate(35, MafCategory.ILLNESS_REHAB).ceiling) // -10
        assertEquals(140, calculator.calculate(35, MafCategory.BEGINNER_RETURN).ceiling) // -5
        assertEquals(145, calculator.calculate(35, MafCategory.CONSISTENT_BASE).ceiling) // 0
        assertEquals(150, calculator.calculate(35, MafCategory.ADVANCED_PRO).ceiling) // +5
    }

    @Test
    fun testZoneRanges() {
        val zones = calculator.calculate(35, MafCategory.BEGINNER_RETURN) // Ceiling = 140
        assertEquals(140, zones.ceiling)
        assertEquals(135, zones.targetMin) // Ceiling - 5
        assertEquals(130, zones.floor) // Ceiling - 10

        assertEquals(MafZone.BELOW_FLOOR, calculator.evaluateZone(128, zones))
        assertEquals(MafZone.MAF_TARGET, calculator.evaluateZone(137, zones))
        assertEquals(MafZone.OVER_CEILING, calculator.evaluateZone(140, zones))
        assertEquals(MafZone.OVER_CEILING, calculator.evaluateZone(145, zones))
    }
}
```

- [ ] **Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.domain.MafCalculatorTest`
Expected: FAIL

- [ ] **Step 3: Implement `MafCalculator` and `MafZone` domain logic**
Implement 180-formula logic, category offsets, target range bounds `[ceiling - 5, ceiling]`, and floor `ceiling - 10`.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.domain.MafCalculatorTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/mafrunner/app/domain/ app/src/test/java/com/mafrunner/app/domain/
git commit -m "feat: implement MAF 180 formula and zone evaluation engine"
```

---

### Task 3: Bluetooth LE Heart Rate Manager & Packet Decoder

**Files:**
- Create: `app/src/main/java/com/mafrunner/app/ble/BleHeartRateParser.kt`
- Create: `app/src/main/java/com/mafrunner/app/ble/BleHeartRateManager.kt`
- Create: `app/src/test/java/com/mafrunner/app/ble/BleHeartRateParserTest.kt`

**Interfaces:**
- Consumes: Standard Bluetooth SIG 0x180D / 0x2A37 specifications
- Produces: `data class HeartRateMeasurement(val hr: Int, val rrIntervals: List<Int>)`, `class BleHeartRateParser { fun parse(bytes: ByteArray): HeartRateMeasurement }`, `class BleHeartRateManager(context: Context) { val heartRateFlow: StateFlow<Int?>; val connectionState: StateFlow<BleState>; fun startScan(); fun connect(macAddress: String); fun disconnect() }`

- [ ] **Step 1: Write test for GATT 0x2A37 packet decoding (8-bit, 16-bit, and RR intervals)**
```kotlin
package com.mafrunner.app.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class BleHeartRateParserTest {
    private val parser = BleHeartRateParser()

    @Test
    fun test8BitHeartRateParsing() {
        // Flags: 0x00 (8-bit format), HR: 142 bpm
        val packet = byteArrayOf(0x00, 142.toByte())
        val measurement = parser.parse(packet)
        assertEquals(142, measurement.hr)
    }

    @Test
    fun test16BitHeartRateParsing() {
        // Flags: 0x01 (16-bit format), HR: 145 (0x0091)
        val packet = byteArrayOf(0x01, 0x91.toByte(), 0x00.toByte())
        val measurement = parser.parse(packet)
        assertEquals(145, measurement.hr)
    }
}
```

- [ ] **Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.ble.BleHeartRateParserTest`
Expected: FAIL

- [ ] **Step 3: Implement `BleHeartRateParser` and `BleHeartRateManager`**
Implement packet decoding per Bluetooth SIG spec; build `BleHeartRateManager` with auto-reconnection loop, MAC address persistence, and immediate `StateFlow` emission.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.ble.BleHeartRateParserTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/mafrunner/app/ble/ app/src/test/java/com/mafrunner/app/ble/
git commit -m "feat: implement BLE heart rate parser and connection manager"
```

---

### Task 4: Acoustic Cadence DSP Engine

**Files:**
- Create: `app/src/main/java/com/mafrunner/app/cadence/BandpassFilter.kt`
- Create: `app/src/main/java/com/mafrunner/app/cadence/FootstrikePeakDetector.kt`
- Create: `app/src/main/java/com/mafrunner/app/cadence/AcousticCadenceDetector.kt`
- Create: `app/src/test/java/com/mafrunner/app/cadence/FootstrikePeakDetectorTest.kt`

**Interfaces:**
- Produces: `class BandpassFilter(sampleRate: Int, lowCut: Float = 40f, highCut: Float = 200f)`, `class FootstrikePeakDetector(minRefractoryMs: Long = 220L) { fun processSample(energy: Float, timestampMs: Long): Boolean; val cadenceSpm: StateFlow<Int> }`, `class AcousticCadenceDetector(context: Context) { fun start(); fun stop(); fun setSensitivity(multiplier: Float) }`

- [ ] **Step 1: Write test for refractory debounce and cadence calculation**
```kotlin
package com.mafrunner.app.cadence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootstrikePeakDetectorTest {
    @Test
    fun testRefractoryPeriodRejectsBounces() {
        val detector = FootstrikePeakDetector(minRefractoryMs = 220L)
        // First strike
        assertTrue(detector.processSample(energy = 0.8f, timestampMs = 1000L))
        // Rebound at 100ms should be rejected
        assertFalse(detector.processSample(energy = 0.9f, timestampMs = 1100L))
        // Legitimate second strike at 350ms (~171 SPM) should pass
        assertTrue(detector.processSample(energy = 0.85f, timestampMs = 1350L))
    }
}
```

- [ ] **Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.cadence.FootstrikePeakDetectorTest`
Expected: FAIL

- [ ] **Step 3: Implement IIR Bandpass Filter, FootstrikePeakDetector, and AudioRecord streamer**
Implement 40–200 Hz digital bandpass filter; calculate short-term RMS energy; enforce 220ms refractory period; compute SPM from rolling window of intervals; stream audio in background coroutine via `AudioRecord`.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.cadence.FootstrikePeakDetectorTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/mafrunner/app/cadence/ app/src/test/java/com/mafrunner/app/cadence/
git commit -m "feat: implement acoustic footstrike cadence DSP engine"
```

---

### Task 5: Immediate Audio Alert Engine

**Files:**
- Create: `app/src/main/java/com/mafrunner/app/audio/ToneGenerator.kt`
- Create: `app/src/main/java/com/mafrunner/app/audio/AudioAlertEngine.kt`
- Create: `app/src/test/java/com/mafrunner/app/audio/AudioAlertEvaluatorTest.kt`

**Interfaces:**
- Produces: `class AudioAlertEvaluator { fun shouldTriggerCeilingAlert(currentHr: Int, ceiling: Int, lastAlertTimeMs: Long, nowMs: Long): Boolean }`, `class AudioAlertEngine(context: Context) { fun playCeilingAlert(); fun playFloorAlert(); fun release() }`

- [ ] **Step 1: Write test for alert throttling and trigger evaluation**
```kotlin
package com.mafrunner.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAlertEvaluatorTest {
    private val evaluator = AudioAlertEvaluator(repeatIntervalMs = 2000L)

    @Test
    fun testCeilingAlertTriggering() {
        val ceiling = 140
        // HR at or above ceiling triggers immediately
        assertTrue(evaluator.shouldTriggerCeilingAlert(140, ceiling, lastAlertTimeMs = 0L, nowMs = 1000L))
        // HR above ceiling within 2s throttled
        assertFalse(evaluator.shouldTriggerCeilingAlert(142, ceiling, lastAlertTimeMs = 1000L, nowMs = 2000L))
        // HR above ceiling after 2s triggers again
        assertTrue(evaluator.shouldTriggerCeilingAlert(141, ceiling, lastAlertTimeMs = 1000L, nowMs = 3100L))
        // HR below ceiling never triggers
        assertFalse(evaluator.shouldTriggerCeilingAlert(138, ceiling, lastAlertTimeMs = 0L, nowMs = 5000L))
    }
}
```

- [ ] **Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.audio.AudioAlertEvaluatorTest`
Expected: FAIL

- [ ] **Step 3: Implement `AudioAlertEvaluator` and `AudioAlertEngine` with `SoundPool`**
Pre-load synthesized high-contrast alert chimes into `SoundPool`; implement audio focus ducking via `AudioManager.requestAudioFocus()`.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.audio.AudioAlertEvaluatorTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/mafrunner/app/audio/ app/src/test/java/com/mafrunner/app/audio/
git commit -m "feat: implement immediate audio alert engine with SoundPool"
```

---

### Task 6: Local Persistence with Room Database

**Files:**
- Create: `app/src/main/java/com/mafrunner/app/data/local/WorkoutEntity.kt`
- Create: `app/src/main/java/com/mafrunner/app/data/local/HeartRateSampleEntity.kt`
- Create: `app/src/main/java/com/mafrunner/app/data/local/WorkoutDao.kt`
- Create: `app/src/main/java/com/mafrunner/app/data/local/AppDatabase.kt`
- Create: `app/src/main/java/com/mafrunner/app/data/repository/WorkoutRepository.kt`
- Create: `app/src/test/java/com/mafrunner/app/data/WorkoutEntityTest.kt`

**Interfaces:**
- Produces: `WorkoutEntity(id, startTime, endTime, durationSec, underSec, inZoneSec, overSec, avgHr, maxHr, avgCadence, treadmillSpeed, treadmillDistance, isBenchmark)`, `WorkoutRepository { suspend fun saveWorkout(workout: WorkoutEntity, samples: List<HeartRateSampleEntity>); fun getAllWorkouts(): Flow<List<WorkoutEntity>> }`

- [ ] **Step 1: Write unit test for entity zone percentage calculation**
```kotlin
package com.mafrunner.app.data

import com.mafrunner.app.data.local.WorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutEntityTest {
    @Test
    fun testZonePercentageCalculation() {
        val workout = WorkoutEntity(
            id = 1L, startTime = 1000L, endTime = 2000L, durationSec = 1000,
            underSec = 100, inZoneSec = 850, overSec = 50,
            avgHr = 138, maxHr = 143, avgCadence = 180,
            treadmillSpeed = 8.5f, treadmillDistance = 4.95f, isBenchmark = true
        )
        assertEquals(85, workout.inZonePercentage)
    }
}
```

- [ ] **Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.data.WorkoutEntityTest`
Expected: FAIL

- [ ] **Step 3: Implement Room database, DAOs, and repository**
Set up SQLite schema with Room annotations; implement transaction methods for saving sessions and telemetry streams.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.data.WorkoutEntityTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/mafrunner/app/data/ app/src/test/java/com/mafrunner/app/data/
git commit -m "feat: implement Room database and workout repository"
```

---

### Task 7: Background Workout Tracking Foreground Service

**Files:**
- Create: `app/src/main/java/com/mafrunner/app/service/WorkoutTrackingService.kt`
- Create: `app/src/main/java/com/mafrunner/app/service/WorkoutSessionState.kt`
- Create: `app/src/test/java/com/mafrunner/app/service/WorkoutSessionStateTest.kt`

**Interfaces:**
- Consumes: `BleHeartRateManager`, `AcousticCadenceDetector`, `AudioAlertEngine`, `WorkoutRepository`
- Produces: `WorkoutTrackingService : Service()`, `val workoutStateFlow: StateFlow<WorkoutSessionState>`

- [ ] **Step 1: Write test for workout session timer and state transitions**
```kotlin
package com.mafrunner.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSessionStateTest {
    @Test
    fun testStateTransitions() {
        val state = WorkoutSessionState(isActive = true, isPaused = false, elapsedSeconds = 120)
        val paused = state.copy(isPaused = true)
        assertEquals(true, paused.isPaused)
        assertEquals(120, paused.elapsedSeconds)
    }
}
```

- [ ] **Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.service.WorkoutSessionStateTest`
Expected: FAIL

- [ ] **Step 3: Implement `WorkoutTrackingService`**
Bind BLE listener, cadence detector, and alert engine; manage `FOREGROUND_SERVICE` notification with live BPM and timer updates; hold `PARTIAL_WAKE_LOCK`.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.service.WorkoutSessionStateTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/mafrunner/app/service/ app/src/test/java/com/mafrunner/app/service/
git commit -m "feat: implement background workout tracking foreground service"
```

---

### Task 8: Jetpack Compose Nordic OLED User Interface

**Files:**
- Create: `app/src/main/java/com/mafrunner/app/ui/screens/SetupScreen.kt`
- Create: `app/src/main/java/com/mafrunner/app/ui/screens/ConsoleScreen.kt`
- Create: `app/src/main/java/com/mafrunner/app/ui/screens/HistoryScreen.kt`
- Create: `app/src/main/java/com/mafrunner/app/ui/screens/DevicePickerDialog.kt`
- Create: `app/src/main/java/com/mafrunner/app/ui/screens/PostRunSummaryDialog.kt`
- Create: `app/src/main/java/com/mafrunner/app/MainActivity.kt`

**Interfaces:**
- Consumes: Nordic OLED Theme Tokens, `WorkoutTrackingService`, `BleHeartRateManager`
- Produces: Full 3-screen Jetpack Compose navigation (`SetupScreen`, `ConsoleScreen`, `HistoryScreen`)

- [ ] **Step 1: Implement `SetupScreen` (Maffetone 180 Calculator, BLE Pairing Sheet, Cadence toggle)**
- [ ] **Step 2: Implement `ConsoleScreen` (Nordic OLED Treadmill View with 118sp tabular BPM, real-time dynamic border/accent zone colors, sweat-proof hit targets)**
- [ ] **Step 3: Implement `HistoryScreen` & `PostRunSummaryDialog` with MAF benchmark tags and speed/distance notes**
- [ ] **Step 4: Connect screen state to `MainActivity` with `keepScreenOn = true` in Console mode**
- [ ] **Step 5: Verify build compiles cleanly**
Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/com/mafrunner/app/ui/ app/src/main/java/com/mafrunner/app/MainActivity.kt
git commit -m "feat: implement nordic athletic OLED compose interface"
```

---

### Task 9: Data Exporters (CSV & TCX with Distance Synthesis)

**Files:**
- Create: `app/src/main/java/com/mafrunner/app/export/CsvExporter.kt`
- Create: `app/src/main/java/com/mafrunner/app/export/TcxExporter.kt`
- Create: `app/src/test/java/com/mafrunner/app/export/TcxExporterTest.kt`

**Interfaces:**
- Produces: `class CsvExporter { fun export(workout: WorkoutEntity, samples: List<HeartRateSampleEntity>): String }`, `class TcxExporter { fun export(workout: WorkoutEntity, samples: List<HeartRateSampleEntity>): String }`

- [ ] **Step 1: Write unit test verifying TCX XML format and synthetic distance interpolation**
```kotlin
package com.mafrunner.app.export

import com.mafrunner.app.data.local.HeartRateSampleEntity
import com.mafrunner.app.data.local.WorkoutEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class TcxExporterTest {
    private val exporter = TcxExporter()

    @Test
    fun testTcxDistanceInterpolation() {
        val workout = WorkoutEntity(
            id = 1L, startTime = 1000L, endTime = 3000L, durationSec = 2,
            underSec = 0, inZoneSec = 2, overSec = 0,
            avgHr = 140, maxHr = 140, avgCadence = 180,
            treadmillSpeed = 10f, treadmillDistance = 1000f, isBenchmark = true
        )
        val samples = listOf(
            HeartRateSampleEntity(1L, 1L, 1000L, 140, 180),
            HeartRateSampleEntity(2L, 1L, 2000L, 140, 180)
        )
        val xml = exporter.export(workout, samples)
        assertTrue(xml.contains("<DistanceMeters>500.0</DistanceMeters>"))
        assertTrue(xml.contains("<DistanceMeters>1000.0</DistanceMeters>"))
        assertTrue(xml.contains("<Value>140</Value>"))
    }
}
```

- [ ] **Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.export.TcxExporterTest`
Expected: FAIL

- [ ] **Step 3: Implement CSV and TCX exporters**
Generate standard Training Center XML (TCX) and comma-separated telemetry files.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.export.TcxExporterTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/mafrunner/app/export/ app/src/test/java/com/mafrunner/app/export/
git commit -m "feat: implement CSV and TCX workout exporters"
```

---

### Task 10: End-to-End Build Verification & Release APK Assembly

**Files:**
- Modify: `app/build.gradle.kts` (configure release signing/optimization)

- [ ] **Step 1: Execute full test suite**
Run: `./gradlew test`
Expected: All unit tests pass with zero failures.

- [ ] **Step 2: Build release/debug APK**
Run: `./gradlew assembleDebug`
Expected: Generates installable `app-debug.apk` in `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Verify APK package contents**
Run: `android describe` or verify generated APK file size (~6–10 MB).

- [ ] **Step 4: Commit**
```bash
git add app/build.gradle.kts
git commit -m "chore: complete end-to-end build verification and APK assembly"
```
