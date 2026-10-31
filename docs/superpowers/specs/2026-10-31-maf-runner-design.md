# MAF Runner Android App - Design Document

## 1. Intent & The Why
The goal of this project is to build a personal Android application (`.apk`) for runner practicing Dr. Phil Maffetone's Maximum Aerobic Function (MAF) method. 

MAF training requires running strictly at or below an aerobic heart rate ceiling (determined by the 180-formula) to build an efficient aerobic fat-burning engine while avoiding anaerobic stress and chronic fatigue. When running on a treadmill, small changes in pacing or fatigue cause heart rate to drift upward before the runner feels out of breath. The user needs an immediate, zero-latency feedback system paired with their Coospo H9Z chest strap, alongside an acoustic cadence tracker, to keep their training strictly inside the aerobic zone.

Existing fitness apps and the official Coospo app are bloated, do not provide instantaneous zero-latency threshold alerts, and lack native MAF-specific features like 180-formula category adjustments and acoustic treadmill footstrike cadence detection.

---

## 2. Goals, Non-Goals & Anti-Goals

### Goals
* **Direct BLE Connectivity:** Pair seamlessly with the Coospo H9Z heart rate monitor using the standard Bluetooth Low Energy Heart Rate Service (`0x180D`), with auto-reconnection.
* **Instantaneous Real-Time Alerting:** Trigger immediate audio cues (under 5ms from packet reception via `SoundPool`) and high-contrast visual warnings when heart rate hits or exceeds the MAF ceiling.
* **Maffetone 180-Formula Engine:** Calculate target zones based on age and the four official health/fitness adjustments (−10 bpm, −5 bpm, 0 bpm, +5 bpm), with manual override capability.
* **Acoustic Footstrike Cadence Detection:** Use the phone's microphone to detect rhythmic shoe-to-treadmill-deck impact thuds while resting on the treadmill console, calculating steps per minute (SPM).
* **High-Contrast Treadmill Dashboard:** Giant, legible numbers and full-screen color states (Blue: Under floor, Green: In zone, Flashing Red: Over ceiling) readable from 3+ feet away.
* **Local Workout History & Progression:** Persist sessions locally in SQLite (Room) tracking duration, time-in-zone breakdown, average/peak heart rate, and cadence, with MAF test benchmarking and CSV/TCX export.
* **Android Foreground Service:** Run as an uninterrupted foreground service with a persistent notification to prevent OS battery optimization from killing background BLE tracking.

### Non-Goals
* No cloud backend, user accounts, or login required (100% offline and private).
* No GPS tracking or map routing (designed specifically for treadmill workouts).
* No social media feeds or gamification badges.

### Anti-Goals
* **No delayed or rolling-average alert smoothing:** MAF training requires instant notification the second the threshold is breached, not 5 to 10 seconds later.
* **No complex multi-tap setup before workouts:** Getting on the treadmill and starting a run must take 1 tap once configured.

---

## 3. Constraints
* **Platform:** Android 10+ (API level 29+, targeting modern Android 14/15/API 34/35).
* **Hardware:** Coospo H9Z chest strap (standard BLE 0x180D HRM) and Android smartphone resting on a treadmill display rack.
* **Deployment:** Standalone installable `.apk` built cleanly via standard Gradle wrapper.

---

## 4. Approaches Considered

### Approach 1: Native Android (Kotlin + Jetpack Compose) — [SELECTED]
* **Architecture:** Kotlin 2.x, Jetpack Compose, native `BluetoothGatt`, native `AudioRecord` PCM buffer streaming, `SoundPool` low-latency audio, Room (SQLite) database, and Android Foreground Service.
* **Tradeoffs:**
  * *Pros:* Direct hardware access with zero bridging overhead; sub-5ms alert response times; direct raw audio DSP for treadmill acoustic cadence filtering; maximum background service reliability; lean APK size (~6–10 MB).
  * *Cons:* Pure Android codebase (cannot compile for iOS without rewrite).
* **Decision:** Selected because immediate zero-latency alerting and raw microphone audio buffer analysis are non-negotiable core requirements.

### Approach 2: Cross-Platform (Flutter / Dart)
* **Architecture:** Flutter with `flutter_blue_plus`, `sqflite`, and community audio stream plugins.
* **Tradeoffs:**
  * *Pros:* Single-codebase cross-platform UI.
  * *Cons:* Streaming high-frequency raw audio buffers for acoustic peak detection across the Dart/native bridge incurs performance and GC overhead; background service lifecycle on Android 14+ is brittle; bloated APK size (~35–50 MB).
* **Decision:** Rejected due to audio streaming latency and background service fragility.

---

## 5. System Architecture & Detailed Behavior

### 5.1 MAF 180-Formula & Zone Engine
* **Base Formula:** `Base MAF = 180 - Age`.
* **Category Adjustments:**
  * Category 1 (Illness / major rehab / chronic fatigue): `Base MAF - 10 bpm`
  * Category 2 (Beginner / recovering from injury / inconsistent base): `Base MAF - 5 bpm`
  * Category 3 (Consistent base training for 1–2+ years without illness/injury): `Base MAF + 0 bpm`
  * Category 4 (Advanced / competitive athlete with sustained progress): `Base MAF + 5 bpm`
* **Zones:**
  * **MAF Ceiling (Max):** Calculated MAF bpm (or custom manual override).
  * **MAF Target Range:** `[MAF Ceiling - 5 bpm, MAF Ceiling]`.
  * **Aerobic Floor (Lower):** `MAF Ceiling - 10 bpm`.
  * **Under Zone:** `< Aerobic Floor`.

### 5.2 Bluetooth Low Energy (BLE) Engine
* **Service:** Standard Bluetooth SIG Heart Rate Service `0000180D-0000-1000-8000-00805F9B34FB`.
* **Characteristic:** Heart Rate Measurement `00002A37-0000-1000-8000-00805F9B34FB`.
* **Behavior:**
  * Scans for BLE devices advertising the HRM service or matching name prefix "H9Z" / "Coospo".
  * Remembers paired device MAC address in `SharedPreferences` / `DataStore` for instant auto-connect on app launch.
  * Auto-reconnect loop with exponential backoff on disconnect.
  * Decodes raw byte payload (Flags byte -> 8-bit or 16-bit HR, energy expended, and RR intervals).
  * Emits live heart rate directly via Kotlin `StateFlow<Int>`.

### 5.3 Acoustic Footstrike Cadence Engine
* **Capture:** `AudioRecord` streaming 16-bit PCM mono at 16,000 Hz or 44,100 Hz.
* **Digital Signal Processing (DSP):**
  * Bandpass filter (40 Hz – 200 Hz) to isolate low-frequency deck impact thuds and suppress motor hum and high-pitch noise.
  * Energy envelope computation (moving RMS / short-term energy).
  * Adaptive dynamic peak thresholding with a manual sensitivity adjustment multiplier.
  * Refractory period: Minimum 220 ms between consecutive detected steps (enforces physiological cadence ceiling of ~270 SPM to reject bounce reverberations).
* **Output:** Steps per Minute (SPM) calculated via rolling average of last 6 detected footstrikes.
* **Toggle:** Can be enabled or disabled in settings.

### 5.4 Audio Alert Engine
* **Audio Engine:** `SoundPool` with pre-loaded alert sound assets (low-latency audio buffers).
* **Alert States:**
  * **Ceiling Breach (HR >= MAF Ceiling):** Immediate urgent pulse chime repeats every 2 seconds until heart rate drops below the ceiling.
  * **Floor Breach (HR < Aerobic Floor):** Subtle reminder tone (optional, plays once every 30 seconds if persistently under).
* **Audio Focus:** Requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` so running music ducks volume smoothly while the alert sounds.

### 5.5 Workout Tracking & Foreground Service
* **Service:** `WorkoutTrackingService` started in foreground with `startForeground()` and notification channel showing live BPM, zone status, and elapsed time.
* **Wake Lock:** `PARTIAL_WAKE_LOCK` held during active workouts.
* **Data Logging:**
  * Sample recording every second: timestamp, heart rate, cadence, active zone.
  * Workout summary entity: start time, end time, total duration, time in Under zone, time in MAF zone, time in Over zone, average HR, peak HR, average cadence, optional manual treadmill speed/distance note, and MAF benchmark flag.

### 5.6 User Interface (3-Screen Flow)
1. **Setup / Dashboard Screen:**
   * Maffetone formula calculator card (Age input, category chips, computed MAF ceiling readout).
   * Coospo H9Z connection card (status indicator, 1-tap "Connect" button, signal RSSI).
   * Cadence audio toggle & sensitivity slider.
   * Prominent "Start Treadmill Run" button.
2. **Active Run Screen (Treadmill Console Mode):**
   * Designed for 3+ feet readability.
   * Full-screen background or prominent border dynamic color:
     * Dark/Blue when below floor.
     * Vibrant Green when inside MAF target zone.
     * Flashing Bold Red when at or above MAF ceiling.
   * Massive center BPM counter.
   * Secondary indicators: Cadence (SPM), Elapsed Time (`MM:SS`), Zone distribution bar.
   * Large "Pause" and "Finish Run" controls.
3. **History & Progression Screen:**
   * Post-run summary dialog with immediate time-in-zone % and stats, plus treadmill speed/distance input.
   * Chronological workout history list with MAF benchmark indicator.
   * Export button: Exports all workouts or selected workout to standard `.csv` and `.tcx`.

---

## 6. Engineering Decisions & Architecture Details (Builder Calls)
The following technical decisions were vetted and settled for the implementation:

1. **Acoustic Cadence Lifecycle (Screen-On Console Mode):**
   * Treadmill running holds `FLAG_KEEP_SCREEN_ON` during active workouts so the console metrics stay visible.
   * Acoustic cadence `AudioRecord` processing runs strictly while the run is active in foreground console mode. If the phone screen is locked or app minimized, the microphone pauses to conserve battery and avoid Android 14 background mic restrictions, while BLE heart rate tracking continues uninterrupted.

2. **BLE Device Discovery & Auto-Pairing:**
   * On first launch (or when tapping "Change Sensor"), a bottom sheet displays discovered BLE heart rate monitors with RSSI signal bars, auto-highlighting any device named "H9Z" or "Coospo".
   * Once selected, the MAC address is permanently cached in local preferences. Future app opens automatically scan and connect to this saved MAC in the background with zero user taps.

3. **Pause State Semantics:**
   * Tapping "Pause" halts the workout timer, suspends sample logging to the database, and silences cadence DSP and ceiling audio alarms (allowing quiet rest/hydration breaks).
   * Live heart rate continues to display on screen so the runner can visually observe their aerobic recovery rate while paused.

4. **TCX Distance Synthesis for Strava/Garmin Compatibility:**
   * When exporting to `.tcx`, if the runner entered a treadmill distance at the end of the session, the exporter synthetically distributes cumulative distance across the active run trackpoints. This ensures third-party platforms (Strava, Garmin Connect) correctly display treadmill mileage and pace instead of flagging the activity as stationary.

5. **MAF Benchmark Flagging:**
   * Implemented as an optional "MAF Test Run" toggle in the post-run dialog. Flagged runs receive a gold MAF badge in the history view and are highlighted in trend charts to make progress comparisons effortless.
