# Heart Rate Strap Battery Level & Pre-Run Warning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Bluetooth Low Energy (BLE) Battery Service (`0x180F`) discovery, live battery level parsing (`0x2A19`), pre-run battery warning indicators on the Setup screen, and live status pill battery monitoring during workouts on the Console screen.

**Architecture:** `BleHeartRateManager` discovers the standard Bluetooth SIG Battery Service (`0x180F`) and queries the Battery Level characteristic (`0x2A19`), streaming values via `batteryLevelFlow`. `BleBatteryParser` parses raw GATT bytes into 0–100% integers. `WorkoutTrackingService` feeds the battery percentage into `WorkoutSessionState`. Compose UI surfaces an OLED battery badge and pre-run low-battery warning on `SetupScreen`, and integrates live battery telemetry into the status bar on `ConsoleScreen`.

**Tech Stack:** Kotlin 2.1, Android Bluetooth GATT (API 31+), Jetpack Compose, Coroutines StateFlow, JUnit 4.

**Spec:** [`docs/superpowers/specs/2026-11-01-strap-battery-level-design.md`](file:///C:/Users/khair/MyProjects/maf-runner-android/docs/superpowers/specs/2026-11-01-strap-battery-level-design.md)

## Global Constraints
- Target package directory: `app/src/main/kotlin/com/mafrunner/app/`
- Target test directory: `app/src/test/java/com/mafrunner/app/`
- Design system: Nordic Athletic OLED (`#000000` pitch black, `#00E676` neon green, `#00D2FF` cyan, `#FF3B30` red, `#FFB800` amber, `#111111` card background, `#222222` border).
- Color tiers for battery: $\ge 50\%$ Neon Green, $20-49\%$ Nordic Amber, $< 20\%$ Nordic Crimson.
- Pre-run warning copy: `⚠️ Strap battery low (XX%)` without blocking run start.
- Standard Bluetooth SIG UUIDs: Battery Service `0000180F-0000-1000-8000-00805F9B34FB`, Characteristic `00002A19-0000-1000-8000-00805F9B34FB`.
- Zero external dependencies beyond existing Android SDK & Compose libraries.

## Review Focus
1. **Missing Battery Service Graceful Fallback:** If a connected BLE strap does not support or advertise Battery Service `0x180F`, battery level must remain `null` without crashing or dropping connection.
2. **Bounds Clamping ($0-100\%$):** Stray or non-standard byte values ($> 100$ or negative signed bytes) must clamp safely to $0..100$.
3. **BluetoothGattCallback API Overloads:** Ensure compatibility with both Android API 31/32 and API 33+ `onCharacteristicRead` / `onCharacteristicChanged` overloads.
4. **Disconnect Reset:** When the strap disconnects or is manually disconnected, `batteryLevelFlow` and `strapBatteryLevel` must immediately reset to `null`.
5. **Non-Blocking Warning:** The pre-run warning banner on the Setup screen must inform the user when battery is $< 20\%$ without disabling the "START RUN" button.

---

### Task 1: BLE Battery Parser & Unit Tests

**Files:**
- Create: `app/src/main/kotlin/com/mafrunner/app/ble/BleBatteryParser.kt`
- Create: `app/src/test/java/com/mafrunner/app/ble/BleBatteryParserTest.kt`

**Interfaces:**
- Consumes: Raw GATT characteristic byte array (`ByteArray?`).
- Produces: `BleBatteryParser.parse(bytes: ByteArray?): Int?` returning integer in range $0..100$ or `null`.

- [ ] **Step 1: Write failing unit tests in `BleBatteryParserTest.kt`**
  Assert:
  - Standard percentage (e.g. `0x55` -> 85).
  - Minimum percentage (`0x00` -> 0).
  - Maximum percentage (`0x64` -> 100).
  - Clamping of out-of-range values (`0xFF` -> 100).
  - Null or empty input returns `null`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.ble.BleBatteryParserTest --console=plain`
  Expected: FAIL (class `BleBatteryParser` not found).

- [ ] **Step 3: Implement `BleBatteryParser.kt`**
  ```kotlin
  package com.mafrunner.app.ble

  class BleBatteryParser {
      fun parse(bytes: ByteArray?): Int? {
          if (bytes == null || bytes.isEmpty()) return null
          val raw = bytes[0].toInt() and 0xFF
          return raw.coerceIn(0, 100)
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.ble.BleBatteryParserTest --console=plain`
  Expected: PASS.

- [ ] **Step 5: Commit changes**
  ```bash
  git add app/src/main/kotlin/com/mafrunner/app/ble/BleBatteryParser.kt app/src/test/java/com/mafrunner/app/ble/BleBatteryParserTest.kt
  git commit -m "feat(ble): add BleBatteryParser with 0-100% clamping and unit tests"
  ```

---

### Task 2: Battery Service Discovery & Flow in `BleHeartRateManager`

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/ble/BleHeartRateManager.kt`

**Interfaces:**
- Consumes: `BleBatteryParser`, Android `BluetoothGatt`.
- Produces: `val batteryLevelFlow: StateFlow<Int?>` exposed on `BleHeartRateManager`.

- [ ] **Step 1: Add UUID constants and `batteryLevelFlow` in `BleHeartRateManager.kt`**
  Add:
  - `BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")`
  - `BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")`
  - `private val batteryParser = BleBatteryParser()`
  - `private val _batteryLevelFlow = MutableStateFlow<Int?>(null)`
  - `val batteryLevelFlow: StateFlow<Int?> = _batteryLevelFlow.asStateFlow()`

- [ ] **Step 2: Query Battery Service in `onServicesDiscovered`**
  In `onServicesDiscovered(gatt, status)`:
  - If status is `GATT_SUCCESS`:
    - After setting up HR notifications, check `val batteryService = gatt.getService(BATTERY_SERVICE_UUID)`.
    - If present, find `val batteryChar = batteryService.getCharacteristic(BATTERY_LEVEL_UUID)`.
    - If `batteryChar != null`:
      - Call `gatt.readCharacteristic(batteryChar)`.
      - Enable notifications if supported: `gatt.setCharacteristicNotification(batteryChar, true)`.

- [ ] **Step 3: Handle `onCharacteristicRead` and `onCharacteristicChanged`**
  - Implement/update `onCharacteristicRead`:
    - Check if `characteristic.uuid == BATTERY_LEVEL_UUID`.
    - Parse using `batteryParser.parse(value)` and assign to `_batteryLevelFlow.value`.
  - In `onCharacteristicChanged`:
    - If `characteristic.uuid == BATTERY_LEVEL_UUID`, parse and assign to `_batteryLevelFlow.value`.
  - In `disconnect()` and `onConnectionStateChange` (`DISCONNECTED`):
    - Reset `_batteryLevelFlow.value = null`.

- [ ] **Step 4: Verify Compilation & Existing Tests**
  Run: `.\gradlew.bat testDebugUnitTest --console=plain`
  Expected: BUILD SUCCESSFUL (all tests pass).

- [ ] **Step 5: Commit changes**
  ```bash
  git add app/src/main/kotlin/com/mafrunner/app/ble/BleHeartRateManager.kt
  git commit -m "feat(ble): add battery service discovery and live batteryLevelFlow"
  ```

---

### Task 3: Session State & Service Plumbing

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/service/WorkoutSessionState.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/service/WorkoutTrackingService.kt`
- Modify: `app/src/test/java/com/mafrunner/app/service/WorkoutSessionStateTest.kt`

**Interfaces:**
- Consumes: `BleHeartRateManager.batteryLevelFlow`.
- Produces: `WorkoutSessionState.strapBatteryLevel: Int?`.

- [ ] **Step 1: Write failing unit test in `WorkoutSessionStateTest.kt`**
  In `WorkoutSessionStateTest.kt`, add `testStrapBatteryLevelField()`:
  - Initialize `WorkoutSessionState(strapBatteryLevel = 85)`.
  - Assert `assertEquals(85, state.strapBatteryLevel)`.
  - Assert default value is `null`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.service.WorkoutSessionStateTest --console=plain`
  Expected: FAIL (`strapBatteryLevel` unresolved).

- [ ] **Step 3: Update `WorkoutSessionState.kt` and `WorkoutTrackingService.kt`**
  - In `WorkoutSessionState.kt`, add parameter:
    `val strapBatteryLevel: Int? = null`
  - In `WorkoutTrackingService.kt`, in `onCreate()` or coroutine launch:
    - Launch collection of `bleManager.batteryLevelFlow`:
      ```kotlin
      serviceScope.launch {
          bleManager.batteryLevelFlow.collect { battery ->
              _sessionState.value = _sessionState.value.copy(strapBatteryLevel = battery)
          }
      }
      ```

- [ ] **Step 4: Run test to verify it passes**
  Run: `.\gradlew.bat testDebugUnitTest --tests com.mafrunner.app.service.WorkoutSessionStateTest --console=plain`
  Expected: PASS.

- [ ] **Step 5: Commit changes**
  ```bash
  git add app/src/main/kotlin/com/mafrunner/app/service/
  git commit -m "feat(service): wire strap battery level to WorkoutSessionState"
  ```

---

### Task 4: Setup Screen Battery Badge & Pre-Run Warning

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/ui/screens/SetupScreen.kt`
- Modify: `app/src/main/kotlin/com/mafrunner/app/MainActivity.kt`

**Interfaces:**
- Consumes: `strapBatteryLevel: Int?` passed from `MainActivity`.
- Produces: Setup screen Bluetooth Strap Card with battery percentage badge and pre-run low-battery warning.

- [ ] **Step 1: Update `SetupScreen.kt` parameters**
  Add parameter:
  `strapBatteryLevel: Int? = null`

- [ ] **Step 2: Update Bluetooth Strap Card in `SetupScreen.kt`**
  - Next to MAC address or in top-right of card, if `bleState == BleConnectionState.CONNECTED && strapBatteryLevel != null`:
    - Display battery pill: e.g. `85%` with colored battery icon:
      - Neon Green (`#00E676`) when $\ge 50\%$.
      - Nordic Amber (`#FFB800`) when $20..49\%$.
      - Nordic Crimson (`#FF3B30`) when $< 20\%$.
  - When `bleState == BleConnectionState.CONNECTED && strapBatteryLevel != null && strapBatteryLevel < 20`:
    - Render a compact warning chip inside the card:
      `⚠️ Strap battery low (XX%)` with amber text and subtle `#2A1E00` background.

- [ ] **Step 3: Wire in `MainActivity.kt`**
  - Collect `bleBattery by workoutService?.bleManager?.batteryLevelFlow?.collectAsState() ?: remember { mutableStateOf(null) }`.
  - Pass `strapBatteryLevel = bleBattery` into `SetupScreen(...)`.

- [ ] **Step 4: Verify Compilation**
  Run: `.\gradlew.bat compileDebugKotlin --console=plain`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit changes**
  ```bash
  git add app/src/main/kotlin/com/mafrunner/app/ui/screens/SetupScreen.kt app/src/main/kotlin/com/mafrunner/app/MainActivity.kt
  git commit -m "feat(ui): add battery pill and pre-run low battery warning to SetupScreen"
  ```

---

### Task 5: Live Console Top Bar Battery Pill

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/ui/screens/ConsoleScreen.kt`

**Interfaces:**
- Consumes: `state.strapBatteryLevel` from `WorkoutSessionState`.
- Produces: Live status bar chip displaying device name and strap battery percentage.

- [ ] **Step 1: Update Status Top Bar in `ConsoleScreen.kt`**
  In the top status bar:
  - If `state.strapBatteryLevel != null`:
    - Render integrated pill:
      - Green/gray dot
      - Device text: `"COOSPO H9Z"`
      - Divider: `" | "`
      - Battery percentage & icon: `"${state.strapBatteryLevel}% 🔋"`
      - Color battery text:
        - `#00E676` if $\ge 50\%$.
        - `#FFB800` if in $20..49\%$.
        - `#FF3B30` if $< 20\%$.
  - If `state.strapBatteryLevel == null`:
    - Retain existing clean `"COOSPO H9Z"` without broken dividers.

- [ ] **Step 2: Verify Compilation & Run Tests**
  Run: `.\gradlew.bat compileDebugKotlin --console=plain`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit changes**
  ```bash
  git add app/src/main/kotlin/com/mafrunner/app/ui/screens/ConsoleScreen.kt
  git commit -m "feat(ui): display live strap battery percentage in ConsoleScreen status pill"
  ```

---

### Task 6: End-to-End Build & APK Verification

**Files:**
- Output: `MAFRunner.apk` (project root)

- [ ] **Step 1: Run full unit test suite**
  Run: `.\gradlew.bat test --console=plain`
  Expected: BUILD SUCCESSFUL (all unit test classes pass).

- [ ] **Step 2: Assemble Debug APK**
  Run: `.\gradlew.bat assembleDebug --console=plain`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Copy fresh binary to root `MAFRunner.apk`**
  Run: `Copy-Item "app/build/outputs/apk/debug/app-debug.apk" -Destination "MAFRunner.apk" -Force`

- [ ] **Step 4: Verify APK timestamp and size**
  Run: `Get-Item MAFRunner.apk | Select-Object Name, Length, LastWriteTime`
  Expected: Size ~31MB, current timestamp.

- [ ] **Step 5: Commit plan progress and updates**
  ```bash
  git add docs/superpowers/plans/2026-11-01-strap-battery-level.md
  git commit -m "chore: complete strap battery level and pre-run warning implementation plan"
  ```
