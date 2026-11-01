# Cadence Real-Time Level Visualizer & Nordic Athletic App Icon Spec

**Date:** 2026-11-01  
**Project:** MAF Runner Android  
**Target:** Real-time acoustic VU meter calibration & custom brand app icon  

---

## 1. Problem Statement
1. **Blind Sensitivity Adjustment:** On the setup screen, the user can adjust cadence sensitivity (1–10), but cannot see the ambient treadmill motor hum or verify deck footstrike impacts before starting a run.
2. **Generic App Icon:** The app currently uses the default Android robot launcher icon, lacking identity on the phone's home screen.

---

## 2. Requirements & Architecture

### 2.1 Acoustic Cadence Telemetry (`AcousticCadenceDetector.kt`, `FootstrikePeakDetector.kt`)
* Expose `liveRmsEnergy: StateFlow<Float>` (0.0 to 1.0) updated every 32ms buffer frame (~30 FPS).
* Expose `currentThreshold: StateFlow<Float>` reflecting the current sensitivity and noise floor.
* Expose `lastStrikeTimeMs: StateFlow<Long>` (timestamp of most recent valid footstrike).
* Support standalone calibration mode: allow `AcousticCadenceDetector.start()` / `stop()` on the Setup Screen when `cadenceEnabled` is toggled on, independent of the background workout service.

### 2.2 Setup Screen Calibration UI (`SetupScreen.kt`)
* When `cadenceEnabled` is on:
  * Display a horizontal high-contrast VU meter bar directly under the Sensitivity slider.
  * Render a distinct vertical notch at `currentThreshold`.
  * Bar fill: Nordic Dark Cyan (`#00D2FF`) when below threshold; flashes Laser Emerald (`#00E676`) with glowing stroke when crossing the threshold.
  * Below the bar: show live numerical values `Level: XX% | Threshold: YY%` and a reactive `"● STEP DETECTED"` badge that pulses on impact.

### 2.3 Running Console UI (`ConsoleScreen.kt`)
* Next to the Cadence label and SPM numeral:
  * Render a clean 10dp pulse dot that flashes Laser Emerald on each registered footstrike and fades back to Muted Gray (`#48484A`) after 200ms.

### 2.4 Nordic Athletic App Icon (`res/drawable`, `res/mipmap`)
* Pitch black background (`#000000`).
* Central emblem: An athletic ECG cardiogram pulse in Laser Emerald (`#00E676`) flowing into an aerodynamic forward runner silhouette in Laser Cyan (`#00D2FF`).
* Standard adaptive icon (`ic_launcher.xml`, `ic_launcher_round.xml`, `ic_launcher_background.xml`, `ic_launcher_foreground.xml`) compatible with Android 8.0+ (API 26 to 36).

---

## 3. Review Focus
1. **Mic Lifecycle:** Calibration mode on Setup Screen must release `AudioRecord` when leaving the screen or unchecking the toggle.
2. **Smooth Level Decaying:** Audio VU meter must not stutter or crash Compose UI thread.
3. **Threshold Synchronization:** Moving the slider must immediately reposition the threshold marker on the meter.
4. **Adaptive Icon Centering:** The foreground icon artwork must remain within the 66dp safe zone (108dp total canvas) so it is never clipped on circular or squircle launcher masks.
