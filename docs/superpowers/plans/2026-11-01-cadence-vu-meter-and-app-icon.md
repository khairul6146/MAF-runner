# Cadence VU Meter & Nordic Athletic App Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide real-time audio level detection and threshold visualizer for cadence calibration on both setup and console screens, and create a custom Nordic Athletic OLED app launcher icon.

**Architecture:** Extend `FootstrikePeakDetector` and `AcousticCadenceDetector` with `StateFlow` channels for live RMS energy, threshold, and strike timestamps. Build an interactive VU meter component in Jetpack Compose on `SetupScreen` with deck-thud step feedback, add a pulse indicator in `ConsoleScreen`, and replace default Android icons with an adaptive vector "Heart-to-Stride" icon.

**Tech Stack:** Kotlin 2.1+, Jetpack Compose (Material3), Android AudioRecord (16kHz PCM), Android Adaptive Icons XML Vector Drawables, Gradle 9.

**Spec:** `docs/superpowers/specs/2026-11-01-cadence-vu-meter-and-app-icon.md`

## Global Constraints
- Maintain Nordic Athletic OLED aesthetic (`#000000` pitch black, `#00E676` emerald, `#00D2FF` cyan, tabular monospace numerals).
- AudioRecord must only be active during calibration or active workout sessions; immediately release on stop.
- Icon foreground artwork must stay within the 66dp central safe zone of the 108dp adaptive canvas.
- All unit tests must pass before final APK assembly.

## Review Focus
1. **Calibration AudioRecord Lifecycle:** Toggling cadence off on SetupScreen or switching tabs must immediately stop audio recording and release mic.
2. **VU Meter Frame Rate & Smoothness:** Streaming RMS updates at ~30 FPS must not trigger heavy Compose recompositions outside the VU meter.
3. **Threshold Alignment:** Moving the sensitivity slider (1–10) must dynamically update the vertical threshold notch on the bar in real time.
4. **Console Beat Dot Visibility:** Footstrike detection in `ConsoleScreen` must pulse visibly (at least 150ms hold) without flickering imperceptibly.
5. **Adaptive Icon Bounds:** Icon must scale cleanly across circle, rounded square, and squircle mask shapes without clipping the runner or pulse line.

---

### Task 1: Expose Real-Time Audio Energy & Step Events in Cadence Engine

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/cadence/FootstrikePeakDetector.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/cadence/AcousticCadenceDetector.kt`
- Modify: `app/src/test/java/com/mafrunner/app/cadence/FootstrikePeakDetectorTest.kt`

- [x] **Step 1: Write failing test for live RMS energy and threshold flows**
  Add unit tests in `FootstrikePeakDetectorTest.kt` verifying that `processSample()` emits `liveEnergyFlow`, `thresholdFlow`, and `lastStrikeTimestampMsFlow`.
- [x] **Step 2: Run test to confirm failure**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat test --tests "com.mafrunner.app.cadence.FootstrikePeakDetectorTest" --console=plain`
- [x] **Step 3: Implement telemetry StateFlows in `FootstrikePeakDetector` & `AcousticCadenceDetector`**
  Add `liveEnergy`, `currentThreshold`, and `lastStrikeTimestampMs` `StateFlow` properties.
- [x] **Step 4: Run tests to verify pass**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat test --tests "com.mafrunner.app.cadence.FootstrikePeakDetectorTest" --console=plain`
- [x] **Step 5: Commit changes**
  Commit message: `feat: expose live audio energy and threshold streams in cadence engine`

---

### Task 2: Build Interactive Real-Time Cadence VU Meter on SetupScreen

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/ui/screens/SetupScreen.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/MainActivity.kt`

- [x] **Step 1: Implement `CadenceVuMeter` Composable component**
  Create a smooth horizontal level bar with:
  - Background track with tick marks
  - Live animated energy level (0%–100%)
  - Vertical threshold marker corresponding to the slider setting
  - Color shift to `#00E676` and animated `"● STEP DETECTED"` badge on impact.
- [x] **Step 2: Connect SetupScreen to live audio detector in preview mode**
  In `MainActivity.kt` and `SetupScreen.kt`, allow live calibration streaming when `cadenceEnabled` is active on the setup screen. Ensure `DisposableEffect` stops preview when leaving SetupScreen.
- [x] **Step 3: Test compilation**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat compileDebugKotlin --console=plain`
- [x] **Step 4: Commit changes**
  Commit message: `feat: add real-time acoustic cadence VU calibration meter to setup screen`

---

### Task 3: Add Live Footstrike Beat Dot Indicator to Running Console

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/service/WorkoutSessionState.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/service/WorkoutTrackingService.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/ui/screens/ConsoleScreen.kt`

- [x] **Step 1: Update session state to track footstrike timestamp**
  Add `lastStrikeTimestampMs: Long = 0L` to `WorkoutSessionState`.
- [x] **Step 2: Forward strike events in `WorkoutTrackingService`**
  Collect `lastStrikeTimestampMs` from `cadenceDetector` and update `_sessionState`.
- [x] **Step 3: Render animated pulse dot in `ConsoleScreen`**
  Add a responsive Laser Emerald beat dot adjacent to the Cadence display that lights up upon step registration.
- [x] **Step 4: Run unit tests**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat test --console=plain`
- [x] **Step 5: Commit changes**
  Commit message: `feat: add live cadence footstrike beat indicator to console screen`

---

### Task 4: Design and Deploy Custom Nordic Athletic App Icon

**Files:**
- Create/Modify: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create/Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Remove legacy default webp drawables if needed.

- [x] **Step 1: Create `ic_launcher_background.xml`**
  Deep `#000000` Nordic black with subtle aerodynamic angular speed lines.
- [x] **Step 2: Create `ic_launcher_foreground.xml`**
  Nordic "Heart-to-Stride" vector: precision ECG heartbeat in Laser Emerald (`#00E676`) sweeping into a dynamic forward runner silhouette in Laser Cyan (`#00D2FF`), properly scaled within the 66dp safe zone (108dp canvas).
- [x] **Step 3: Verify adaptive launcher XML references**
  Ensure standard and round mipmap XML files reference the new background and foreground vectors.
- [x] **Step 4: Commit changes**
  Commit message: `feat: create custom nordic athletic heart-to-stride app launcher icon`

---

### Task 5: End-to-End Build Verification & APK Assembly

**Files:**
- Output: `MAFRunner.apk`
- Output: `app/build/outputs/apk/debug/app-debug.apk`

- [x] **Step 1: Execute full test suite**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat test --console=plain`
- [x] **Step 2: Assemble Debug APK**
  Run: `Set-Location "C:\Users\khair\MyProjects\maf-runner-android"; .\gradlew.bat assembleDebug --console=plain`
- [x] **Step 3: Copy and verify standalone `MAFRunner.apk`**
  Verify file size and timestamp.
- [x] **Step 4: Final commit**
  Commit message: `chore: update MAFRunner APK with cadence visualizer and custom app icon`
