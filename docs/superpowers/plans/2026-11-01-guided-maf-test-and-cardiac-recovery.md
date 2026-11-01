# Guided MAF Test Protocol & Cardiac Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a Guided MAF Test Protocol with phased state execution (Warmup, Zone-Locked Steady Test, 2-Minute Standing Recovery), Android Text-To-Speech (TTS) coaching alerts on zone drift, automated cardiac recovery sampling ($\Delta HR_{60s}$, $\Delta HR_{120s}$), custom duration controls (pre-run, live, and history), and isolated steady-phase benchmark metrics.

**Architecture:** A foreground service state machine (`TestProtocolEngine`) running within `WorkoutTrackingService` coordinates phase countdowns, samples recovery points at exact seconds, and invokes `SpeechCoachEngine` for audio coaching. SQLite schema is upgraded to version 2 via `ALTER TABLE` to persist guided test metadata and recovery drops. The Compose UI exposes a pre-run setup dialog with custom steppers, an OLED phase stepper on the console, live `+5m`/`-5m` buttons, and an editable duration field in workout history.

**Tech Stack:** Kotlin 2.1, Jetpack Compose, Android Foreground Service, Android `android.speech.tts.TextToSpeech`, Android `android.media.SoundPool`, Android Native `SQLiteOpenHelper`, Coroutines & Flow.

**Spec:** [`docs/superpowers/specs/2026-11-01-guided-maf-test-and-cardiac-recovery-design.md`](file:///C:/Users/khair/MyProjects/maf-runner-android/docs/superpowers/specs/2026-11-01-guided-maf-test-and-cardiac-recovery-design.md)

## Global Constraints
- Target package directory: `app/src/main/kotlin/com/mafrunner/app/`
- Target test directory: `app/src/test/java/com/mafrunner/app/`
- Design system: Nordic Athletic OLED (`#000000` pitch black, `#00E676` neon green, `#00D2FF` cyan, `#FF3B30` red, `#FFB800` amber, `#111111` card background, `#222222` border).
- Tabular monospace numerals for all timers, BPM, SPM, and delta metrics.
- Zero external dependencies: rely strictly on native Android APIs (`SQLiteOpenHelper`, `TextToSpeech`).

## Review Focus
1. **Uninterrupted Screen-Off Recovery:** When the phone screen turns off during the 2-minute recovery phase, heart rate must continue to be sampled at $T=60s$ and $T=120s$ without coroutine freezing.
2. **Benchmark Metric Isolation:** Ensure low-HR warmup and 0-SPM standing recovery points do not pollute `avgHr`, `avgCadence`, or `inZonePercentage` in the benchmark record.
3. **TTS Speech Rate-Limiting:** Verify out-of-zone spoken alerts enforce a minimum 20-second cooldown so audio cues do not bombard the runner.
4. **Database Backward Compatibility:** Verify `WorkoutDatabaseHelper.onUpgrade()` cleanly migrates existing version 1 databases using `ALTER TABLE` without wiping previous workout records.
5. **Non-Destructive Duration Editing:** Editing duration in `WorkoutDetailDialog` updates the summary duration and pace without deleting historical `hr_samples`.

---

### Task 1: Database Schema Version 2 & Repository Duration Updates

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/data/local/WorkoutEntity.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/data/local/WorkoutDatabaseHelper.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/data/repository/WorkoutRepository.kt`
- Modify: `app/src/test/java/com/mafrunner/app/data/WorkoutEntityTest.kt`

**Interfaces:**
- Consumes: Existing `WorkoutEntity` and `WorkoutDatabaseHelper`.
- Produces: 
  - `WorkoutEntity` with fields: `isGuidedTest: Boolean`, `targetDurationSec: Long`, `recoveryPeakHr: Int?`, `recoveryHr60: Int?`, `recoveryHr120: Int?`, `recoveryDrop60: Int?`, `recoveryDrop120: Int?`.
  - `WorkoutDatabaseHelper` at `DATABASE_VERSION = 2` with `ALTER TABLE` migration.
  - `WorkoutRepository.updateWorkoutDetails(workoutId, durationSec, speed, distance, isBenchmark)`.

- [x] **Step 1: Write the failing unit tests for schema v2 fields and repository duration update**
  In `WorkoutEntityTest.kt`, add tests asserting:
  - Default values for `isGuidedTest` (false), `recoveryDrop60` (null), `recoveryDrop120` (null).
  - Construction with guided test recovery values.
  - Duration calculation with custom `durationSec`.

- [x] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.data.WorkoutEntityTest --console=plain`
  Expected: FAIL (missing fields in `WorkoutEntity`).

- [x] **Step 3: Update `WorkoutEntity.kt` with guided test and recovery fields**
  Add `isGuidedTest: Boolean = false`, `targetDurationSec: Long = 0L`, `recoveryPeakHr: Int? = null`, `recoveryHr60: Int? = null`, `recoveryHr120: Int? = null`, `recoveryDrop60: Int? = null`, `recoveryDrop120: Int? = null`.

- [x] **Step 4: Update `WorkoutDatabaseHelper.kt` for Version 2**
  Set `DATABASE_VERSION = 2`. Add column constants `COL_IS_GUIDED_TEST`, `COL_TARGET_DURATION_SEC`, `COL_RECOVERY_HR_PEAK`, `COL_RECOVERY_HR_60`, `COL_RECOVERY_HR_120`, `COL_RECOVERY_DROP_60`, `COL_RECOVERY_DROP_120`. In `onCreate`, include them in `CREATE TABLE workouts`. In `onUpgrade`, execute `ALTER TABLE workouts ADD COLUMN ...` when `oldVersion < 2`.

- [x] **Step 5: Update `WorkoutRepository.kt`**
  - Read new columns from cursor in `refreshWorkouts()`.
  - Write new columns in `saveWorkout()`.
  - Add optional `durationSec: Long? = null` parameter to `updateWorkoutDetails()` to update `COL_DURATION_SEC` when non-null.

- [x] **Step 6: Run tests to verify they pass**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.data.WorkoutEntityTest --console=plain`
  Expected: PASS.

- [x] **Step 7: Commit changes**
  ```bash
  git add app/src/main/kotlin/com/mafrunner/app/data/ local/
  git commit -m "feat(data): upgrade database to v2 with guided test and recovery schema"
  ```

---

### Task 2: Test Protocol State Machine & Metrics Isolator

**Files:**
- Create: `app/src/main/kotlin/com/mafrunner/app/domain/TestPhase.kt`
- Create: `app/src/main/kotlin/com/mafrunner/app/domain/TestProtocolEngine.kt`
- Create: `app/src/test/java/com/mafrunner/app/domain/TestProtocolEngineTest.kt`

**Interfaces:**
- Consumes: `MafZone`, `MafZones`, `HeartRateSampleEntity`.
- Produces:
  - `TestPhase` enum (`FREE_RUN`, `WARMUP`, `TEST_STEADY`, `RECOVERY`, `COMPLETE`).
  - `TestProtocolEngine` managing countdowns, phase transitions, steady-metric isolation, and recovery sampling.

- [x] **Step 1: Write the failing tests for `TestProtocolEngine`**
  In `TestProtocolEngineTest.kt`, test:
  - Starting guided test with 5m warmup and 30m steady test begins in `WARMUP`.
  - Ticking seconds transitions `WARMUP` -> `TEST_STEADY` at 0s.
  - `skipWarmup()` immediately transitions to `TEST_STEADY`.
  - In `TEST_STEADY`, ticking accumulates steady stats (`testUnderSec`, `testInZoneSec`, `testOverSec`).
  - At 0s in `TEST_STEADY`, transitions to `RECOVERY` with initial peak HR recorded.
  - In `RECOVERY`, samples HR at $T=60s$ and $T=120s$, computing $\Delta HR_{60}$ and $\Delta HR_{120}$.
  - At $T=120s$, transitions to `COMPLETE`.

- [x] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.domain.TestProtocolEngineTest --console=plain`
  Expected: FAIL (`TestProtocolEngine` unresolved).

- [x] **Step 3: Implement `TestPhase.kt`**
  ```kotlin
  package com.mafrunner.app.domain

  enum class TestPhase {
      FREE_RUN,
      WARMUP,
      TEST_STEADY,
      RECOVERY,
      COMPLETE
  }
  ```

- [x] **Step 4: Implement `TestProtocolEngine.kt`**
  Implement state machine managing:
  - `currentPhase: TestPhase`
  - `phaseRemainingSeconds: Long`
  - `tick(currentHr: Int?, currentCadence: Int, zone: MafZone?)`
  - `skipWarmup()`
  - `startRecoveryEarly()`
  - `addTimeSeconds(deltaSeconds: Long)` (for on-the-fly $+5m$/$-5m$ adjustment during `TEST_STEADY`)
  - Isolated steady metrics: `testAvgHr`, `testAvgCadence`, `testInZonePercentage`.
  - Recovery stats: `peakHr`, `hr60`, `hr120`, `drop60`, `drop120`.

- [x] **Step 5: Run tests to verify they pass**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.domain.TestProtocolEngineTest --console=plain`
  Expected: PASS.

- [x] **Step 6: Commit changes**
  ```bash
  git add app/src/main/kotlin/com/mafrunner/app/domain/TestPhase.kt app/src/main/kotlin/com/mafrunner/app/domain/TestProtocolEngine.kt app/src/test/java/com/mafrunner/app/domain/TestProtocolEngineTest.kt
  git commit -m "feat(domain): implement test protocol state machine and metric isolator"
  ```

---

### Task 3: Text-To-Speech Audio Coaching Engine

**Files:**
- Create: `app/src/main/kotlin/com/mafrunner/app/audio/SpeechCoachEngine.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/audio/AudioAlertEngine.kt`
- Create: `app/src/test/java/com/mafrunner/app/audio/SpeechCoachEngineTest.kt`

**Interfaces:**
- Consumes: Android `TextToSpeech`, `AudioManager`.
- Produces:
  - `SpeechCoachEngine.speak(text: String, flush: Boolean = false)`
  - Out-of-zone coaching alerts with 20-second rate limiting:
    - `speakCeilingWarning(currentHr: Int)`
    - `speakFloorWarning(currentHr: Int)`
  - Phase transition coaching prompts:
    - `speakWarmupStart()`
    - `speakTestStart(durationMinutes: Int, targetHr: Int)`
    - `speakRecoveryStart()`
    - `speakRecoveryComplete(drop60: Int, drop120: Int)`

- [x] **Step 1: Write unit tests for speech coaching rate limiting & prompt formatting**
  In `SpeechCoachEngineTest.kt`, test:
  - Formatting of ceiling alert: `"Heart rate high at 145 BPM. Ease back your pace."`
  - Formatting of floor alert: `"Heart rate low at 132 BPM. Pick up your pace."`
  - 20-second cooldown gate: Calling ceiling warning twice within 10 seconds only triggers speech once.
  - Phase transition prompts format duration and drop metrics cleanly.

- [x] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.audio.SpeechCoachEngineTest --console=plain`
  Expected: FAIL (`SpeechCoachEngine` unresolved).

- [x] **Step 3: Implement `SpeechCoachEngine.kt`**
  Implement using Android `TextToSpeech` with:
  - `TextToSpeech.OnInitListener` with `Locale.US` fallback.
  - Rate limiting timestamp check (`lastSpeechTimestampMs`).
  - `QUEUE_FLUSH` for urgent ceiling alerts, `QUEUE_ADD` for phase announcements.
  - Audio focus request for speech transient ducking.

- [x] **Step 4: Integrate with `AudioAlertEngine.kt`**
  Add `speechCoach: SpeechCoachEngine` property and delegate spoken coaching calls cleanly alongside tone beeps.

- [x] **Step 5: Run tests to verify they pass**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.audio.SpeechCoachEngineTest --console=plain`
  Expected: PASS.

- [x] **Step 6: Commit changes**
  ```bash
  git add app/src/main/kotlin/com/mafrunner/app/audio/
  git commit -m "feat(audio): add text-to-speech coaching engine with 20s rate limiting"
  ```

---

### Task 4: Service Orchestration & Lifecycle Automation

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/service/WorkoutSessionState.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/service/WorkoutTrackingService.kt`
- Modify: `app/src/test/java/com/mafrunner/app/service/WorkoutSessionStateTest.kt`

**Interfaces:**
- Consumes: `TestProtocolEngine`, `SpeechCoachEngine`, `WorkoutRepository`.
- Produces:
  - Extended `WorkoutSessionState` exposing `isGuidedTest`, `testPhase`, `phaseRemainingSeconds`, `recoveryPeakHr`, `recoveryDrop60`, `recoveryDrop120`.
  - Service methods:
    - `startGuidedTest(warmupSec: Long, testDurationSec: Long, enableCadence: Boolean)`
    - `skipWarmup()`
    - `adjustTestDuration(deltaSec: Long)`
    - `finishToRecoveryEarly()`
  - High-priority background notification on recovery completion.

- [x] **Step 1: Write failing tests for `WorkoutSessionState`**
  In `WorkoutSessionStateTest.kt`, test state formatting with `TestPhase`, phase countdown string (`18:42`), and recovery drop properties.

- [x] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.service.WorkoutSessionStateTest --console=plain`
  Expected: FAIL.

- [x] **Step 3: Update `WorkoutSessionState.kt`**
  Add `isGuidedTest: Boolean = false`, `testPhase: TestPhase = TestPhase.FREE_RUN`, `phaseRemainingSeconds: Long = 0L`, `targetTestDurationSeconds: Long = 0L`, `recoveryPeakHr: Int? = null`, `recoveryHr60: Int? = null`, `recoveryHr120: Int? = null`, `recoveryDrop60: Int? = null`, `recoveryDrop120: Int? = null`.

- [x] **Step 4: Update `WorkoutTrackingService.kt`**
  - Integrate `TestProtocolEngine`.
  - Wire `timerJob` tick loop to advance protocol engine every 1000ms.
  - Wire out-of-zone transitions to trigger `speechCoach.speakCeilingWarning()` and `speakFloorWarning()` with rate limit.
  - When `TestProtocolEngine` transitions to `RECOVERY`, stop cadence recording and announce recovery start.
  - When `RECOVERY` completes ($T=120s$), play completion chime, trigger recovery summary speech, post high-priority completion notification, and trigger auto-save or UI prompt.
  - In `finishWorkout()`, save guided test fields (`is_guided_test = 1`, `recovery_drop_60`, `recovery_drop_120`, and isolated steady-phase `avgHr`, `avgCadence`, `inZonePercentage`).

- [x] **Step 5: Run tests to verify they pass**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.service.WorkoutSessionStateTest --console=plain`
  Expected: PASS.

- [x] **Step 6: Commit changes**
  ```bash
  git add app/src/main/kotlin/com/mafrunner/app/service/
  git commit -m "feat(service): wire guided test state machine and recovery lifecycle in service"
  ```

---

### Task 5: Pre-Run Setup Sheet & Live Console Phased UI

**Files:**
- Create: `app/src/main/kotlin/com/mafrunner/app/ui/screens/TestSetupDialog.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/ui/screens/ConsoleScreen.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/MainActivity.kt`

**Interfaces:**
- Consumes: `WorkoutSessionState`, `TestPhase`.
- Produces:
  - `TestSetupDialog`: Modal to select Free Run vs Guided MAF Test, Warmup (Skip/5m/10m), Presets (20m/30m/45m/60m), and custom stepper (`-5m`, `-1m`, `+1m`, `+5m`).
  - `ConsoleScreen` phased header showing stepper `[WARMUP]` -> `[TEST]` -> `[RECOVERY]`, live countdown timer, and `+5m`/`-5m` quick duration adjust buttons.

- [x] **Step 1: Create `TestSetupDialog.kt`**
  Build Nordic OLED dialog with:
  - Mode selector: "Free Run" vs "Guided MAF Test".
  - Warmup duration chips: `Skip`, `5 min`, `10 min`.
  - Target duration presets (`20m`, `30m`, `45m`, `60m`) and fine-grained stepper buttons (`-5m`, `-1m`, `+1m`, `+5m`) with custom minute display.
  - "START TEST" action callback `onStartTest(warmupSec, testSec)`.

- [x] **Step 2: Update `ConsoleScreen.kt`**
  - Add Phased Header bar at the top of the console when `isGuidedTest` is true.
  - Display phase badge and countdown timer in monospace.
  - In `WARMUP` phase: Display "Skip to Test" button.
  - In `TEST_STEADY` phase: Display `+5m` and `-5m` adjustment pills.
  - In `RECOVERY` phase: Display 2-minute standing countdown ring with peak HR and live drop. Disable pause button during recovery.

- [x] **Step 3: Wire in `MainActivity.kt`**
  Wire start button to open `TestSetupDialog` before launching session, and wire `onSkipWarmup`, `onAdjustDuration`, and `onFinishEarlyToRecovery`.

- [x] **Step 4: Verify Compilation**
  Run: `.\gradlew.bat compileDebugKotlin --console=plain`
  Expected: BUILD SUCCESSFUL.

- [x] **Step 5: Commit changes**
  ```bash
  git add app/src/main/kotlin/com/mafrunner/app/ui/screens/TestSetupDialog.kt app/src/main/kotlin/com/mafrunner/app/ui/screens/ConsoleScreen.kt app/src/main/kotlin/com/mafrunner/app/MainActivity.kt
  git commit -m "feat(ui): add guided test setup sheet and console phased header"
  ```

---

### Task 6: Post-Run Recovery Assessment & History Duration Editing

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/ui/screens/WorkoutDetailDialog.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/ui/screens/HistoryScreen.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/ui/screens/PostRunSummaryDialog.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/domain/WorkoutAnalyticsEngine.kt`
- Modify: `app/src/test/java/com/mafrunner/app/domain/WorkoutAnalyticsEngineTest.kt`

**Interfaces:**
- Consumes: `WorkoutEntity` with recovery and duration fields.
- Produces:
  - `WorkoutDetailDialog` with editable duration (mm:ss), editable speed/distance, and cardiac recovery display card.
  - `HistoryScreen` displaying guided test badge and recovery drops.
  - `WorkoutAnalyticsEngine` calculating average recovery drop trends across periods.

- [x] **Step 1: Write unit tests for recovery analytics in `WorkoutAnalyticsEngineTest.kt`**
  Test period analytics computing average $\Delta HR_{60}$ and $\Delta HR_{120}$ across guided test workouts in the selected horizon.

- [x] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.domain.WorkoutAnalyticsEngineTest --console=plain`
  Expected: FAIL.

- [x] **Step 3: Update `WorkoutAnalyticsEngine.kt`**
  Add `avgRecoveryDrop60: Int?` and `avgRecoveryDrop120: Int?` to `PeriodAnalytics`. Calculate averages for workouts where `isGuidedTest == true` and recovery drops are non-null.

- [x] **Step 4: Update `WorkoutDetailDialog.kt`**
  - Add editable `Duration (minutes / seconds)` input field alongside treadmill speed and distance.
  - Add Cardiac Recovery Card showing $\Delta HR_{60}$ and $\Delta HR_{120}$ with color-coded classification (Green: $\ge 25$ bpm, Cyan: $15-24$ bpm, Amber: $<15$ bpm).
  - Update `onSave` callback to pass edited `durationSec`.

- [x] **Step 5: Update `HistoryScreen.kt` and `PostRunSummaryDialog.kt`**
  - Display `[MAF TEST]` badge on guided test history list items.
  - Display $\Delta HR_{60}$ recovery drop chip directly on the history card.
  - Show recovery results in `PostRunSummaryDialog` when finishing a guided test.

- [x] **Step 6: Run tests to verify they pass**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.domain.WorkoutAnalyticsEngineTest --console=plain`
  Expected: PASS.

- [x] **Step 7: Commit changes**
  ```bash
  git add app/src/main/kotlin/com/mafrunner/app/ui/screens/WorkoutDetailDialog.kt app/src/main/kotlin/com/mafrunner/app/ui/screens/HistoryScreen.kt app/src/main/kotlin/com/mafrunner/app/ui/screens/PostRunSummaryDialog.kt app/src/main/kotlin/com/mafrunner/app/domain/WorkoutAnalyticsEngine.kt app/src/test/java/com/mafrunner/app/domain/WorkoutAnalyticsEngineTest.kt
  git commit -m "feat(ui): add cardiac recovery card and editable duration to history"
  ```

---

### Task 7: End-to-End Build & APK Assembly

**Files:**
- Output: `MAFRunner.apk` (project root)

- [x] **Step 1: Run full unit test suite**
  Run: `.\gradlew.bat test --console=plain`
  Expected: BUILD SUCCESSFUL (all test classes pass).

- [x] **Step 2: Assemble Debug APK**
  Run: `.\gradlew.bat assembleDebug --console=plain`
  Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Copy binary to root `MAFRunner.apk`**
  Run: `Copy-Item "app/build/outputs/apk/debug/app-debug.apk" -Destination "MAFRunner.apk" -Force`

- [x] **Step 4: Verify APK timestamp and size**
  Run: `Get-Item MAFRunner.apk | Select-Object Name, Length, LastWriteTime`
  Expected: Size ~31MB, current timestamp.

- [x] **Step 5: Commit final deliverable**
  ```bash
  git add MAFRunner.apk docs/superpowers/plans/
  git commit -m "chore: assemble fresh APK with guided maf test and cardiac recovery engine"
  ```
