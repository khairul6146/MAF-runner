# Heart Rate Strap Battery Level & Pre-Run Warning Design

## Intent & The Why
Heart rate chest straps (e.g. Coospo H9Z, Polar H10, Garmin HRM-Pro/Dual) operate on coin-cell (CR2032) or rechargeable batteries. Runners typically don't know the strap battery is dying until it abruptly drops Bluetooth connection mid-run—which is catastrophic during a benchmark 30-minute MAF test. 
Exposing live battery percentage and providing proactive pre-run alerts protects the integrity of MAF workouts by ensuring the strap has sufficient charge before the runner steps on the treadmill.

---

## Goals
1. **Query Standard Bluetooth SIG Battery Service:** Support Bluetooth GATT standard Battery Service (`0x180F`) and Characteristic (`0x2A19`) across all compatible BLE heart rate straps.
2. **Pre-Run Battery Visibility & Safeguard:** Display live strap battery percentage on the Setup screen's sensor card, with an inline amber warning banner when battery is $< 20\%$ to indicate strap battery low.
3. **Live Workout Battery Telemetry:** Display battery percentage and a glanceable Nordic battery indicator directly in the live running console top bar alongside device status.
4. **Resilient BLE Lifecycle Handling:** Gracefully handle straps that do not advertise the battery service (display `--` without crashing) and update the battery value whenever the strap sends notification updates.

## Non-Goals & Anti-Goals
- **Non-Goals:** Storing historical per-second battery drain curves in SQLite; sending push notifications when the app is closed; battery forecasting or machine-learning discharge predictions.
- **Anti-Goals:** Blocking or disallowing the runner from starting a workout when battery is low (runners must always retain full control to start even with low battery); cluttering the console with flashing full-screen alarms during high-intensity MAF testing.

---

## Constraints
- **Nordic Athletic OLED Design:** Pitch black `#000000`, card `#111111`, border `#222222`, tabular monospace numerals.
- **Battery Status Color Tiers:**
  - $\ge 50\%$: Neon Green (`#00E676`)
  - $20\% - 49\%$: Nordic Amber / Warning (`#FFB800`)
  - $< 20\%$: Nordic Crimson / Critical (`#FF3B30`)
- **Standard BLE Specifications:** Use `0000180F-0000-1000-8000-00805F9B34FB` (Service) and `00002A19-0000-1000-8000-00805F9B34FB` (Characteristic). Value is single-byte unsigned integer ($0 - 100$).
- **Zero Third-Party Dependencies:** Native Android Bluetooth GATT and Jetpack Compose.

---

## Approaches Considered

### Option A: Integrated Nordic Status Pill & Inline Pre-Run Warning (Selected)
- **Concept:** Battery percentage and color-coded mini battery glyph are integrated directly into the existing sensor card on Setup and the status pill on the Console screen (`[ ● COOSPO H9Z | 85% 🔋 ]`). When battery is $< 20\%$, a clean warning indicator appears on the Setup card showing the battery is low.
- **Tradeoffs:** High cohesion, zero UI clutter, fits seamlessly into Nordic minimalist aesthetic, informs without obstructing.
- **Decision:** Selected by user.

### Option B: Dedicated Telemetry Badge & Modal Intercept
- **Concept:** Disjoint badge for battery telemetry and an interruptive popup modal on tapping "START RUN" that forces confirmation before proceeding if battery $< 20\%$.
- **Tradeoffs:** Adds unnecessary modal clicks before a run and splits device connectivity into multiple disconnected UI elements. Rejected in favor of Option A.

---

## Detailed Architecture & Implementation

### 1. BLE Layer (`BleHeartRateManager.kt`)
- Define constants:
  ```kotlin
  val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
  val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
  ```
- Add StateFlow:
  ```kotlin
  private val _batteryLevelFlow = MutableStateFlow<Int?>(null)
  val batteryLevelFlow: StateFlow<Int?> = _batteryLevelFlow.asStateFlow()
  ```
- In `onServicesDiscovered(gatt, status)`:
  - After enabling HR measurement notification, check `gatt.getService(BATTERY_SERVICE_UUID)`.
  - If present, retrieve `BATTERY_LEVEL_UUID` characteristic.
  - Read characteristic immediately: `gatt.readCharacteristic(batteryChar)`.
  - Also enable notifications/indications on `BATTERY_LEVEL_UUID` (and descriptor `0x2902`) if supported.
- In `onCharacteristicRead` and `onCharacteristicChanged`:
  - When characteristic UUID matches `BATTERY_LEVEL_UUID`:
    - Read byte 0 as unsigned int: `val level = value[0].toInt() and 0xFF` (bounded to $0..100$).
    - Update `_batteryLevelFlow.value = level`.
- In `disconnect()` / connection lost:
  - Reset `_batteryLevelFlow.value = null`.

### 2. Service & Session State Layer (`WorkoutSessionState.kt` & `WorkoutTrackingService.kt`)
- `WorkoutSessionState`:
  - Add property: `val strapBatteryLevel: Int? = null`.
- `WorkoutTrackingService`:
  - Collect `bleManager.batteryLevelFlow` and update `_sessionState.value = _sessionState.value.copy(strapBatteryLevel = it)`.

### 3. Setup Screen (`SetupScreen.kt`)
- In `Bluetooth Strap Card`:
  - Accept `strapBatteryLevel: Int?`.
  - When connected and `strapBatteryLevel != null`:
    - Display compact battery indicator: e.g. `85%` with color-coded mini battery icon.
  - When `strapBatteryLevel != null && strapBatteryLevel < 20`:
    - Display inline warning chip or line inside the strap card:
      `⚠️ Strap battery low (XX%)`

### 4. Console Screen (`ConsoleScreen.kt`)
- In `Status Top Bar`:
  - Expand device pill to include battery:
    - Dot (`#00E676` or gray), Device Name (`COOSPO H9Z`), separator `|`, and battery string (`85% 🔋` or dynamic battery icon).
    - If `strapBatteryLevel < 20`, battery text/icon turns `#FFB800` or `#FF3B30` to alert runner quietly.
    - If `strapBatteryLevel == null`, cleanly omit or display `COOSPO H9Z` without breaking layout.

---

## What is Left to the Builder
- Precise pixel padding and icon drawing (canvas or unicode glyphs `🔋` / custom mini vector) adhering to Nordic Athletic standards.
- Handling older Android API 31/32 vs API 33+ overloads for `BluetoothGattCallback.onCharacteristicRead` with `@Suppress("DEPRECATION")`.
