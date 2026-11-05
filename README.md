# MAF Runner

> **Nordic Athletic OLED Heart Rate & Treadmill Cadence Coach for Android**

[![Platform](https://img.shields.io/badge/Platform-Android_12%2B-00E676?style=flat-square&logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-00D2FF?style=flat-square)](LICENSE)
[![Design](https://img.shields.io/badge/Design-Nordic_OLED-000000?style=flat-square)](#design-system)

MAF Runner is a local, privacy-first Android application designed for aerobic base building using Dr. Phil Maffetone's **MAF 180 Formula**. It pairs with Bluetooth Low Energy (BLE) chest straps, analyzes footstrike cadence directly through the device microphone while placed on a treadmill console, guides you through structured MAF tests, and assesses post-run cardiac recovery.

---

## Key Features

### 1. 180-Formula Aerobic Heart Rate Zones
- **Automated Zone Calculation:** Calculates your aerobic maximum (`180 - Age`), aerobic floor (`MAF - 10 bpm`), and recovery floor based on Maffetone health and training categories.
- **Visual OLED Alerts:** Pure black (`#000000`) high-contrast Nordic aesthetic with instant color feedback:
  - Cyan (`#00D2FF`): Below floor (warmup / under target).
  - Neon Green (`#00E676`): MAF optimal sweet spot.
  - Pulsing Crimson (`#FF3B30`): Over ceiling alert.

### 2. BLE Chest Strap Integration & Battery Monitoring
- **Universal BLE Compatibility:** Pairs with standard Bluetooth SIG Heart Rate Service (`0x180D`) chest straps, including Coospo H9Z, Polar H10, Garmin HRM-Pro/Dual, and Wahoo TICKR.
- **Live Battery Telemetry:** Reads standard Bluetooth SIG Battery Service (`0x180F`) and Level Characteristic (`0x2A19`).
- **Pre-Run Battery Warning:** Displays live percentage on the setup screen and surfaces an inline amber warning (`⚠️ Strap battery low (XX%)`) when battery is below 20% to prevent unexpected disconnects during a test.
- **In-Run Status Pill:** Seamlessly integrates strap name and live battery status into the console status bar (`[ ● COOSPO H9Z | 85% 🔋 ]`).

### 3. Acoustic Treadmill Cadence Detection
- **Zero Wearables Required:** Uses the phone's internal microphone while resting flat on the treadmill console to listen to footstrike impacts on the running belt.
- **Digital Signal Processing:**
  - 16 kHz PCM acoustic capture with a 400–800 Hz Butterworth bandpass filter isolating mechanical belt thuds from motor hum and music.
  - Dual-window energy ratio peak detector with dynamic refractory blanking (220 ms) to eliminate double-triggering.
- **Real-Time Cadence VU Meter:** Multi-segment dynamic LED meter on the live console providing instant visual cadence feedback (160–190 SPM).

### 4. Guided MAF Test Protocol & Benchmark Metric Isolation
- **Phased Execution:**
  - **Warmup:** Gradual heart rate ramp-up. Tap `SKIP TO TEST` when ready.
  - **Steady Test:** Timed steady-state test phase with customizable duration (5 to 180 minutes) and live `+5m` / `-5m` adjustments.
  - **2-Minute Recovery:** Automatic transition to post-test cardiac recovery sampling.
- **Benchmark Metric Isolation:** Automatically isolates `avgHr`, `avgCadence`, and `inZonePercentage` exclusively to the steady test phase, preventing low-cadence warmup or 0-SPM recovery from skewing monthly benchmark progress.

### 5. Automated 2-Minute Cardiac Recovery
- **Clinical Vagal Reactivation:** Runs inside a foreground service with a partial wake lock, sampling exact heart rate at $T = 60\text{ s}$ and $T = 120\text{ s}$ even when the screen is locked.
- **Heart Rate Recovery Drops:** Computes $\Delta HR_{60\text{s}}$ and $\Delta HR_{120\text{s}}$ with clinical vagal tone ratings (*Exceptional*, *Healthy*, *Slow*, *Impaired*).
- **Longitudinal Period Analytics:** Summarizes rolling 1-minute and 2-minute recovery averages across workout history.

### 6. Voice Coaching (Text-To-Speech)
- **Hands-Free Audio Guidance:** Verbal voice cues announce phase boundaries and pace adjustments when drifting outside target zones.
- **20-Second Cooldown:** Smart audio rate-limiting prevents repetitive cue spamming while you adjust pace or incline.

### 7. 100% Offline, Privacy-First & Standard Exports
- **Local SQLite Storage:** All workouts and second-by-second heart rate samples stay strictly on your device. Zero external cloud servers, zero analytics, zero trackers.
- **Standard Open Formats:** Export completed runs to **TCX** (compatible with Garmin Connect, Strava, and Intervals.icu) and **CSV** for spreadsheet analysis.

---

## Hardware Compatibility

| Device | Connection | Heart Rate (`0x180D`) | Battery (`0x180F`) | Cadence Support |
| :--- | :--- | :---: | :---: | :---: |
| **Coospo H9Z / H808S** | BLE 5.0 | Yes | Yes | Via Acoustic Engine |
| **Polar H10 / H9** | BLE 5.0 | Yes | Yes | Via Acoustic Engine |
| **Garmin HRM-Pro / Dual** | BLE / ANT+ | Yes | Yes | Via Acoustic Engine |
| **Wahoo TICKR / TICKR X** | BLE 4.2+ | Yes | Yes | Via Acoustic Engine |
| **Magene H64 / H303** | BLE 4.0+ | Yes | Yes | Via Acoustic Engine |

---

## Tech Stack & Architecture

- **Language:** Kotlin 2.1
- **UI Framework:** Jetpack Compose + Material 3 (Nordic Athletic OLED palette)
- **Concurrency & Streams:** Kotlin Coroutines & StateFlow
- **Background Execution:** Android Foreground Service with Partial WakeLock
- **Audio DSP:** Android `AudioRecord` (16 kHz PCM) with custom Butterworth IIR bandpass filtering
- **Speech Engine:** Android `TextToSpeech` with US locale fallback and audio ducking
- **Database:** Native Android `SQLiteOpenHelper` with automated schema migrations
- **Build System:** Gradle (Kotlin DSL) with version catalog (`libs.versions.toml`)

---

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17 or JDK 21
- Android SDK 35 (Android 15) with platform tools
- Physical Android device with Android 12+ (API 31+) recommended for BLE and microphone testing

### Build from Source

```bash
# Clone the repository
git clone https://github.com/khairul6146/MAF-runner.git
cd MAF-runner

# Run unit test suite
./gradlew test

# Assemble standalone debug APK
./gradlew assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Android Permissions

MAF Runner requests only the permissions required for core fitness tracking:

| Permission | Purpose |
| :--- | :--- |
| `BLUETOOTH_SCAN` | Discover nearby Bluetooth Low Energy heart rate chest straps. |
| `BLUETOOTH_CONNECT` | Connect and receive GATT heart rate and battery measurements. |
| `RECORD_AUDIO` | Sample treadmill belt acoustics to calculate step cadence (audio is processed in memory in real time and is never stored or transmitted). |
| `FOREGROUND_SERVICE` | Maintain continuous metric tracking, audio alerts, and cardiac recovery timers with the screen off. |
| `POST_NOTIFICATIONS` | Display workout progress and recovery completion alerts in the notification tray. |

---

## License

This project is licensed under the [MIT License](LICENSE).
