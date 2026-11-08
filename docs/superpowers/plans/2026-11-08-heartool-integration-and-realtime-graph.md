# Heartool Integration & Real-Time Heart Rate Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate Heartool / Fitcare BLE protocol capabilities (real-time ECG streaming, RR-interval HRV calculation, and strap hardware alert triggers) reverse-engineered from `cn.fitcare.heartool`, and build a high-performance Compose Canvas real-time heart rate graph on the Console Screen.

**Architecture:** 
1. Build `FitcareProtocolEngine` to parse proprietary Fitcare ECG packets (`0xA2`/`0xA3` 16-bit signed Big-Endian samples with checksum) and send hardware threshold commands (`0xEC0A` / `0xFCA2`).
2. Extend `BleHeartRateParser` and `BleHeartRateManager` to calculate real-time RMSSD / HRV from standard `0x2A37` RR intervals (`rrRaw / 1.024`).
3. Add a rolling telemetry buffer in `WorkoutTrackingService` and `WorkoutSessionState` to supply smooth 1Hz time-series data.
4. Construct `RealtimeHeartRateGraph` in Jetpack Compose Canvas featuring cubic Bézier smoothed line rendering, MAF ceiling/target/floor zone threshold bands, gradient area fill, and a live pulsing head indicator.
5. Embed the real-time graph into `ConsoleScreen` with an optional ECG/BPM graph toggle.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, Android BLE (BluetoothGatt), StateFlow / Coroutines.

**Spec:** Reverse Engineering findings in `C:\Users\khair\MyProjects\Heartool\decompiled\REVERSE_ENGINEERING_REPORT.md` and `fitcare_ble_reader.py`.

## Global Constraints

- Android API level 26+ (compatible up to Android 15 / API 35).
- Zero frame drops: Canvas graph drawing must allocate zero objects during `drawBehind` / `onDraw` frame ticks.
- Retain backwards compatibility for non-Fitcare standard BLE heart rate straps (e.g. Polar, Garmin, Wahoo).
- Conventional commits format (`feat: ...`, `fix: ...`).

## Review Focus

1. **Non-Fitcare straps:** When connected to standard straps lacking `0xEC00` ECG service, the app must fall back seamlessly to standard `0x2A37` without errors.
2. **Empty / Startup buffer:** When the workout just starts (<2 samples), the graph must render a flat baseline cleanly without division-by-zero or NaN coordinates.
3. **ECG Checksum rejection:** Corrupted Fitcare ECG packets whose modulo-256 sum does not match the trailing byte must be discarded without crashing.
4. **Dynamic Scaling:** When heart rate spikes well above ceiling (e.g. 175 BPM) or drops low (e.g. 50 BPM), the graph Y-axis must dynamically scale while keeping the MAF target zone visibly anchored.
5. **Memory leak prevention:** Rolling sample list must be bounded (e.g. fixed window of 60-120 data points for live view) to prevent infinite memory growth during multi-hour runs.

---

### Task 1: Fitcare Protocol & RR-Interval HRV Engine

**Files:**
- Create: `app/src/main/kotlin/com/mafrunner/app/ble/FitcareProtocolEngine.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/ble/BleHeartRateParser.kt`
- Test: `app/src/test/java/com/mafrunner/app/ble/FitcareProtocolEngineTest.kt`

**Interfaces:**
- Produces: 
  - `FitcareProtocolEngine.parseEcgPacket(data: ByteArray): List<Int>?`
  - `FitcareProtocolEngine.calculateRmssd(rrIntervalsMs: List<Double>): Double?`
  - `FitcareProtocolEngine.buildArmbandThresholdCommand(maxHr: Int): ByteArray`
  - `HeartRateMeasurement.rrIntervalsMs: List<Double>`

- [x] **Step 1: Write unit tests for Fitcare ECG parsing, checksum verification, and RR/HRV calculations**

Test cases:
- Frame `0xA2` valid checksum parses 16-bit Big-Endian samples.
- Frame `0xA3` sequence stream parses valid samples.
- Frame with invalid checksum returns `null`.
- RR-interval raw conversion: `raw / 1.024`.
- RMSSD calculation for consecutive RR interval differences.

- [x] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.ble.FitcareProtocolEngineTest`
Expected: FAIL (class not found)

- [x] **Step 3: Implement `FitcareProtocolEngine.kt` and enhance `BleHeartRateParser.kt`**

Implement ECG frame header check (`0xA2`, `0xA3`), modulo-256 checksum validation, signed 16-bit Big-Endian conversion, and RR-interval float scaling (`/ 1.024`).

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests com.mafrunner.app.ble.FitcareProtocolEngineTest`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mafrunner/app/ble/FitcareProtocolEngine.kt app/src/main/kotlin/com/mafrunner/app/ble/BleHeartRateParser.kt app/src/test/java/com/mafrunner/app/ble/FitcareProtocolEngineTest.kt
git commit -m "feat(ble): implement Fitcare ECG protocol and RR-interval HRV engine"
```

---

### Task 2: BLE GATT Subscription for Fitcare ECG & Strap Commands

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/ble/BleHeartRateManager.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/service/WorkoutSessionState.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/service/WorkoutTrackingService.kt`

**Interfaces:**
- Consumes: `FitcareProtocolEngine`
- Produces:
  - `BleHeartRateManager.ecgSamplesFlow: StateFlow<List<Int>>`
  - `BleHeartRateManager.currentHrvRmssd: StateFlow<Double?>`
  - `WorkoutSessionState.currentHrvRmssd: Double?`
  - `WorkoutSessionState.recentHrHistory: List<Int>`

- [x] **Step 1: Extend BleHeartRateManager with Fitcare GATT UUIDs**

Add UUIDs:
- `ECG_SERVICE_UUID = "0000ec00-0000-1000-8000-00805f9b34fb"`
- `ECG_NOTIFY_UUID = "0000ec09-0000-1000-8000-00805f9b34fb"`
- `ECG_WRITE_UUID = "0000ec0a-0000-1000-8000-00805f9b34fb"`
In `onServicesDiscovered`, discover `0xEC00` if present and subscribe to `0xEC09`.
In `onCharacteristicChanged`, dispatch ECG samples to `_ecgSamplesFlow`.

- [x] **Step 2: Add rolling HR history and HRV to WorkoutSessionState & WorkoutTrackingService**

In `WorkoutTrackingService`, maintain a rolling 90-sample buffer (`recentHrHistory`) updated every second and emit it in `sessionState`.

- [x] **Step 3: Verify compilation and existing unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS

- [x] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/mafrunner/app/ble/BleHeartRateManager.kt app/src/main/kotlin/com/mafrunner/app/service/WorkoutSessionState.kt app/src/main/kotlin/com/mafrunner/app/service/WorkoutTrackingService.kt
git commit -m "feat(service): wire Fitcare ECG notifications and rolling HR telemetry buffer"
```

---

### Task 3: Real-Time Heart Rate Graph Component

**Files:**
- Create: `app/src/main/kotlin/com/mafrunner/app/ui/components/RealtimeHeartRateGraph.kt`
- Test: `app/src/test/java/com/mafrunner/app/ui/RealtimeGraphScalingTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun RealtimeHeartRateGraph(modifier: Modifier, hrHistory: List<Int>, zones: MafZones, activeHr: Int?)`
  - `RealtimeGraphScaling.calculateYRange(samples: List<Int>, zones: MafZones): Pair<Float, Float>`

- [x] **Step 1: Write unit test for Y-axis dynamic range calculator**

Verify Y-axis bounds always encompass `zones.floor - 10` and `zones.ceiling + 10`, while auto-expanding if current HR goes higher or lower.

- [x] **Step 2: Implement RealtimeHeartRateGraph Jetpack Compose Canvas**

Implement:
- Horizontal threshold guide lines for Ceiling (Crimson), Target Min (Emerald), and Floor (Cyan).
- Cubic Bézier path smoothing connecting samples across width.
- Dynamic Brush gradient filling the area under the curve to zero alpha.
- Glowing indicator dot at the latest sample point with animated pulse ring.
- Min/Max BPM legend labels in monospace font.

- [x] **Step 3: Verify compilation and tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS

- [x] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/mafrunner/app/ui/components/RealtimeHeartRateGraph.kt app/src/test/java/com/mafrunner/app/ui/RealtimeGraphScalingTest.kt
git commit -m "feat(ui): add real-time heart rate Canvas waveform graph"
```

---

### Task 4: Console Screen Integration & Live ECG Waveform Toggle

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/ui/screens/ConsoleScreen.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/MainActivity.kt`

**Interfaces:**
- Embeds `RealtimeHeartRateGraph` into the active workout HUD on `ConsoleScreen`.
- Connects live ECG samples flow and HRV RMSSD score display.

- [x] **Step 1: Embed RealtimeHeartRateGraph in ConsoleScreen**

Position the real-time graph cleanly between the Giant BPM reading and the Secondary metrics cards, with full reactive state updates.
Display live RMSSD / HRV badge when RR intervals are present.

- [x] **Step 2: Verify full build and unit tests**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/mafrunner/app/ui/screens/ConsoleScreen.kt app/src/main/kotlin/com/mafrunner/app/MainActivity.kt
git commit -m "feat(ui): embed real-time HR graph and HRV telemetry into ConsoleScreen"
```
