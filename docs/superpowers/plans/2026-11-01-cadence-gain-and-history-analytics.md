# Cadence Audio Gain Calibration & History Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overhaul acoustic cadence audio capture with 12x pre-gain, noise-suppression bypass, and auto-baseline clearance, and add complete workout inspection, editing, deletion, and weekly/monthly/quarterly aerobic analytics to the History screen.

**Architecture:** Switch `AudioRecord` to `VOICE_RECOGNITION` with 12x digital pre-gain; implement dynamic noise-floor clearance in `FootstrikePeakDetector`. Add `deleteWorkout` in SQLite helper. Build `WorkoutDetailDialog` for full inspection, editing, and deletion. Implement `WorkoutAnalyticsEngine` to compute aggregated compliance, progression, and volume across selectable time horizons (Week, Month, Quarter, All) with Jetpack Compose controls.

**Tech Stack:** Kotlin 2.1+, Jetpack Compose (Material3), Android AudioRecord, SQLiteOpenHelper, JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-01-cadence-gain-and-history-analytics.md`

## Global Constraints
- Maintain high-contrast Nordic Athletic OLED design (`#000000`, `#00E676`, `#00D2FF`, `#FF3B30`, `#FFFFB800`).
- No external heavy charting libraries; custom native Compose Canvas/Surface visualizers.
- Zero audio buffer memory leaks or background microphone capture when idle.
- All unit tests must pass before compiling the final APK.

## Review Focus
1. **Audio Gain Stability:** 12x gain must not cause numeric overflow or stuck cadence states.
2. **Cascade Delete Integrity:** Deleting a workout must cleanly delete rows in both `workouts` and `hr_samples`.
3. **Empty Filter Windows:** Selecting a time window (e.g. Quarter) with no matching runs must display empty placeholders without arithmetic exceptions.
4. **Edit State Propagation:** Editing treadmill speed/distance in `WorkoutDetailDialog` must immediately update the history card and active analytics.
5. **Backwards Compatibility:** Old workouts without speed/distance must calculate analytics without null pointer exceptions.

---

### Task 1: Overhaul Cadence Audio Engine with High-Gain & Auto-Baseline Clearance

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/cadence/AcousticCadenceDetector.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/cadence/FootstrikePeakDetector.kt`
- Modify: `app/src/test/java/com/mafrunner/app/cadence/FootstrikePeakDetectorTest.kt`

- [ ] **Step 1: Write unit tests for new sensitivity clearance math**
  In `FootstrikePeakDetectorTest.kt`, test that sensitivity 10 produces responsive low clearance (`~noiseFloor + 0.05f`) and sensitivity 1 produces higher clearance (`~noiseFloor + 0.41f`), allowing low-energy thuds (`0.10f`) to register at high sensitivity.
- [ ] **Step 2: Run test to confirm failure**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat testDebugUnitTest --tests "com.mafrunner.app.cadence.FootstrikePeakDetectorTest" --console=plain`
- [ ] **Step 3: Update `FootstrikePeakDetector` threshold clearance logic**
  Implement dynamic baseline clearance formula based on slider sensitivity.
- [ ] **Step 4: Update `AcousticCadenceDetector` with 12x gain and `VOICE_RECOGNITION`**
  Set `AudioSource.VOICE_RECOGNITION` and apply 12.0x digital gain before RMS calculation.
- [ ] **Step 5: Run tests to verify pass**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat testDebugUnitTest --tests "com.mafrunner.app.cadence.FootstrikePeakDetectorTest" --console=plain`
- [ ] **Step 6: Commit changes**
  Commit message: `feat: add 12x digital gain and auto-baseline clearance to cadence engine`

---

### Task 2: Add Workout Deletion and Record Updating in Local Database

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/data/local/WorkoutDatabaseHelper.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/data/repository/WorkoutRepository.kt`
- Modify: `app/src/test/java/com/mafrunner/app/data/WorkoutEntityTest.kt`

- [ ] **Step 1: Write test for workout deletion and repository methods**
  Add unit test for workout deletion and entity helpers.
- [ ] **Step 2: Add `deleteWorkout` to `WorkoutRepository`**
  Implement SQL deletion with cascade to samples table and flow refresh.
- [ ] **Step 3: Run unit tests**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat testDebugUnitTest --tests "com.mafrunner.app.data.WorkoutEntityTest" --console=plain`
- [ ] **Step 4: Commit changes**
  Commit message: `feat: implement workout deletion and update methods in repository`

---

### Task 3: Implement Workout Detail, Edit & Delete Modal (`WorkoutDetailDialog.kt`)

**Files:**
- Create: `app/src/main/kotlin/com/mafrunner/app/ui/screens/WorkoutDetailDialog.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/ui/screens/HistoryScreen.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/MainActivity.kt`

- [ ] **Step 1: Create `WorkoutDetailDialog.kt`**
  Implement full-screen or large bottom sheet dialog displaying:
  - Date, time, formatted duration, average & max HR, cadence
  - Zone percentage & visual distribution bar
  - Editable speed (km/h) and distance (km) text fields
  - MAF benchmark tag toggle
  - Save Changes button
  - Delete Workout button with confirmation prompt
  - Export Single Run button (CSV / TCX share)
- [ ] **Step 2: Wire dialog to `HistoryScreen` click event**
  Make history list items clickable to launch `WorkoutDetailDialog`.
- [ ] **Step 3: Test compilation**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat compileDebugKotlin --console=plain`
- [ ] **Step 4: Commit changes**
  Commit message: `feat: create workout detail inspection, edit, and deletion dialog`

---

### Task 4: Build Workout Analytics Engine and Multi-Window UI (Week, Month, Quarter, All)

**Files:**
- Create: `app/src/main/kotlin/com/mafrunner/app/domain/WorkoutAnalyticsEngine.kt`
- Create: `app/src/test/java/com/mafrunner/app/domain/WorkoutAnalyticsEngineTest.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/ui/screens/HistoryScreen.kt`

- [ ] **Step 1: Write failing unit test for `WorkoutAnalyticsEngine`**
  Test time-window bucketing (Week, Month, Quarter, All-Time), compliance percentage calculation, and aerobic speed progression deltas.
- [ ] **Step 2: Run test to confirm failure**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat testDebugUnitTest --tests "com.mafrunner.app.domain.WorkoutAnalyticsEngineTest" --console=plain`
- [ ] **Step 3: Implement `WorkoutAnalyticsEngine`**
  Write pure domain class computing:
  - Total duration, total distance, session count
  - Aggregate MAF compliance rate
  - Aerobic pace progression on benchmark runs
  - Zone time aggregates
- [ ] **Step 4: Run unit tests to confirm pass**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat testDebugUnitTest --tests "com.mafrunner.app.domain.WorkoutAnalyticsEngineTest" --console=plain`
- [ ] **Step 5: Integrate Analytics UI into `HistoryScreen.kt`**
  Add period selection tabs `[Week] [Month] [Quarter] [All]` and rich stats cards:
  - Aerobic Base Compliance Bar
  - Total Training Volume & Distance
  - MAF Progression Trend Indicator
  - Tri-Zone Distribution Bar
- [ ] **Step 6: Commit changes**
  Commit message: `feat: add multi-period aerobic analytics engine and UI to history screen`

---

### Task 5: End-to-End Build Verification & APK Assembly

**Files:**
- Output: `MAFRunner.apk`
- Output: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: Run full unit test suite**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat test --console=plain`
- [ ] **Step 2: Assemble Debug APK**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat assembleDebug --console=plain`
- [ ] **Step 3: Copy and verify standalone `MAFRunner.apk`**
- [ ] **Step 4: Final commit**
  Commit message: `chore: assemble updated MAFRunner APK with cadence gain and history analytics`
