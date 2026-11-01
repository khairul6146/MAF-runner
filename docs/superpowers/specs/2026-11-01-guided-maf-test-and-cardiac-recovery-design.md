# Design Document: Guided MAF Test Protocol & Cardiac Recovery Engine

- **Date:** 2026-11-01
- **Status:** Draft / Reviewing
- **Target Repository:** `C:\Users\khair\MyProjects\maf-runner-android`
- **Topic:** Guided MAF Test with Phased Transitions, Custom Duration Controls, Out-of-Zone Voice Alerts, and Post-Run 2-Minute Cardiac Recovery Tracking

---

## 1. Intent & Why

Endurance runners practicing the Maffetone (MAF) method periodically conduct a standardized MAF Test on the treadmill to objectively assess aerobic efficiency progress over time. Currently, MAF Runner functions as an unconstrained recorder where the user manually watches the heart rate and tags a workout as a benchmark after the fact.

This feature introduces a **Guided MAF Test Protocol** acting as an active coach:
1. It automates the structured test sequence: Warmup &rarr; Steady Zone-Locked Test Countdown &rarr; Stand-Still Recovery.
2. It provides hands-free spoken coaching via Android Text-To-Speech (TTS), strictly notifying the runner only when their heart rate drifts out of the target zone or when phases transition.
3. It automatically samples and calculates Heart Rate Recovery drops ($\Delta HR_{60s}$ and $\Delta HR_{120s}$), providing an immediate assessment of autonomic nervous system reactivation and aerobic fitness.
4. It grants full duration flexibility: custom pre-run target duration, live on-the-fly duration adjustments, and post-run duration editing in workout history.

---

## 2. Goals, Non-Goals & Anti-Goals

### Goals
- **Structured Phased State Machine:** Support 3 clear workout phases (`WARMUP`, `TEST_STEADY`, `RECOVERY`) managed in the Android foreground service so screen-off or backgrounding never interrupts the test.
- **Custom Duration Flexibility:**
  - Pre-run presets (20m, 30m standard, 45m, 60m) and fine-grained stepper (`-5m`, `-1m`, `+1m`, `+5m`) or direct entry for any custom test duration.
  - Warmup duration selector (Skip, 5 min, 10 min).
  - In-run on-the-fly adjustment (`+5 min` / `-5 min`) on the live console.
  - Post-run history editing of workout duration in `WorkoutDetailDialog`.
- **Targeted Audio Coaching (TTS):**
  - Announce phase transitions clearly through headphones.
  - Speak coaching alerts **only** when heart rate drifts out of the target zone (above ceiling or below floor), rate-limited to avoid audio fatigue.
- **Automated Cardiac Recovery Engine:**
  - When the steady test countdown finishes, automatically transition to a 2-minute recovery phase.
  - Prompt the runner to stand still on the treadmill side rails.
  - Record heart rate at $T=0s$, $T=60s$, and $T=120s$, computing $\Delta HR_{60}$ and $\Delta HR_{120}$.
  - Store recovery metrics in the Room database and display them in history.

### Non-Goals
- Automatic treadmill hardware speed sync over Bluetooth FTMS (manual belt speed control is assumed).
- Wear OS / smartwatch companion app (stays Android smartphone focused).
- Complex voice recognition commands from the runner to the phone.

### Anti-Goals
- **Chatty AI slop:** The audio coach must not chatter constantly every 30 seconds or recite generic motivation. It must remain silent while the runner is locked in the green zone, speaking only on phase changes or when out-of-zone correction is required.
- **Fragile UI-bound timers:** Timers must not live solely in Compose or ViewModel coroutines that freeze when Android OS locks the screen or enters Doze mode.

---

## 3. Constraints & Design Tokens
- **Design System:** Nordic Athletic OLED (`#000000`, card surfaces `#0D0D0D` / `#111111`, card borders `#222222`, primary neon green `#00E676`, cyan `#00D2FF`, amber `#FFB800`, red `#FF3B30`).
- **Typography:** Tabular monospace numerals for all timers, BPM, SPM, and delta metrics.
- **Audio Output:** Android `TextToSpeech` engine mixed cleanly with existing tone beeps and haptic buzzes.

---

## 4. Architectural Approaches

### Approaches Considered

1. **Approach 1: Foreground Service State Machine (Selected)**
   - *How it works:* Manage `TestProtocolState` (`IDLE`, `WARMUP`, `TEST_STEADY`, `RECOVERY`, `COMPLETE`) directly within `WorkoutTrackingService`. Service manages phase countdowns, triggers TTS out-of-zone warnings, and captures recovery samples at exact timestamps. Room schema is extended with recovery fields.
   - *Tradeoffs:* Requires service extension and Room database migration, but delivers 100% reliability with the screen off or app backgrounded.
   - *Outcome:* **Selected by user.**

2. **Approach 2: UI-Layer Test Controller (ViewModel/Compose)**
   - *How it works:* Keep `WorkoutTrackingService` unphased; run phase timers in ViewModel coroutines and call TTS from Activity.
   - *Tradeoffs:* Easy to implement, but fails when the phone screen turns off or enters battery-saving Doze mode on the treadmill rack.
   - *Outcome:* **Rejected.**

---

## 5. Detailed Specifications

### 5.1 Protocol State Machine & Metrics Partitioning
Managed in `WorkoutTrackingService.kt`:
```kotlin
enum class TestPhase {
    FREE_RUN,
    WARMUP,
    TEST_STEADY,
    RECOVERY,
    COMPLETE
}
```
* **WARMUP:** Countdown from configured warmup minutes (5 or 10 min, or skipped). Spoken prompt: *"Warmup started. Gradually bring your heart rate up to your MAF zone."*
* **TEST_STEADY:** Countdown from target test duration (e.g. 35 min). Spoken prompt: *"Warmup complete. Beginning [X] minute MAF test. Keep heart rate locked at [target] BPM."*
* **RECOVERY:** 120-second countdown. Spoken prompt: *"Test complete! Stand still on the treadmill side rails for 2 minutes to measure cardiac recovery."* At $T=60s$, samples $HR_{60}$. At $T=120s$, samples $HR_{120}$, computes deltas, and automatically finishes workout.

#### Metrics Partitioning for Benchmark Purity
To maintain true scientific benchmark comparability across weeks and months:
* **Primary Benchmark Metrics (`avgHeartRate`, `avgCadence`, `inZonePercentage`, tri-zone distribution):** Calculated **exclusively** from the `TEST_STEADY` phase. Low-cadence warmup jogging and 0-SPM standing recovery are strictly excluded from these benchmark metrics so they do not artificially depress zone compliance or cadence.
* **Whole-Session Volume:** `totalDurationSeconds` preserves the entire time-on-feet (Warmup + Test + Recovery). Raw `hr_samples` records retain all data points with phase tagging.

### 5.2 Audio Coach Rules (TTS & Tone)
* **Tone/Haptic Priority:** Immediate tone beep & vibration when crossing zone boundary.
* **TTS Coaching Prompt:**
  - Above Zone ($HR > \text{MAF Ceiling}$): *"Heart rate high at [HR] BPM. Ease back your pace."*
  - Below Zone ($HR < \text{MAF Floor}$): *"Heart rate low at [HR] BPM. Pick up your pace."*
  - **Rate Limiting:** Minimum 20 seconds between out-of-zone TTS announcements to avoid spam.
  - **In-Zone Silence:** Zero speech when running in the green zone.

### 5.3 Cardiac Recovery Calculation
* $\text{Peak } HR = HR \text{ at } T=0s$ (moment test completes)
* $\Delta HR_{60} = \text{Peak } HR - HR_{60s}$
* $\Delta HR_{120} = \text{Peak } HR - HR_{120s}$
* Clinical assessment classification:
  - $\Delta HR_{60} \ge 25\text{ bpm}$: Excellent vagal reactivation / cardiovascular recovery.
  - $15 \le \Delta HR_{60} < 25\text{ bpm}$: Normal healthy recovery.
  - $\Delta HR_{60} < 12\text{ bpm}$: Elevated autonomic fatigue or delayed parasympathetic reactivation.

### 5.4 Database Schema Updates (`WorkoutDatabaseHelper`)
The persistence layer uses native Android `SQLiteOpenHelper`. Bumping `DATABASE_VERSION` from `1` to `2` with `ALTER TABLE` statements in `onUpgrade()`:
- `is_guided_test INTEGER NOT NULL DEFAULT 0`
- `target_duration_sec INTEGER NOT NULL DEFAULT 0`
- `recovery_hr_peak INTEGER DEFAULT NULL`
- `recovery_hr_60 INTEGER DEFAULT NULL`
- `recovery_hr_120 INTEGER DEFAULT NULL`
- `recovery_drop_60 INTEGER DEFAULT NULL`
- `recovery_drop_120 INTEGER DEFAULT NULL`

### 5.5 In-Run State Transitions & Completion Lifecycle (Builder Decisions)
- **Warmup Skip:** A "Skip to Test" button is available during `WARMUP` if the runner reaches target zone early.
- **Early Finish during Test:** Tapping "Finish" during `TEST_STEADY` prompts: "Proceed to 2m Recovery" or "Save & Exit Immediately".
- **Recovery Continuity:** Recovery is strictly a continuous 120-second clinical measurement. The pause button is disabled during `RECOVERY`.
- **Post-Recovery Summary Notification:** When the 2-minute recovery finishes in the background, the service plays a completion chime and posts a high-priority "Test Complete — Tap to record treadmill speed & distance" notification. If `MainActivity` is already foregrounded, `PostRunSummaryDialog` opens immediately.
- **Duration Editing in History:** Modifying `durationSec` in `WorkoutDetailDialog` updates the workout header metadata and recalculates average pace, while preserving the raw sensor points in `hr_samples`.

### 5.5 UI Components
1. **Pre-Run Setup Dialog / BottomSheet:**
   - Free Run vs Guided MAF Test toggle.
   - Warmup selector: `Skip`, `5 min`, `10 min`.
   - Target duration selector: `20m`, `30m`, `45m`, `60m` presets + `[-]` / `[+]` minute stepper and direct entry.
2. **In-Run Phased Header on `ConsoleScreen`:**
   - Stepper bar: `[WARMUP]` &rarr; `[MAF TEST (countdown)]` &rarr; `[RECOVERY 2:00]`.
   - `+5m` and `-5m` quick duration adjust buttons during `TEST_STEADY` phase.
3. **Workout History (`WorkoutDetailDialog`):**
   - Editable Duration input (minutes:seconds) alongside speed and distance.
   - Cardiac Recovery display card showing $\Delta HR_{60}$ and $\Delta HR_{120}$ with visual rating.

---

## 6. What's Left to the Builder
- Implementation of `WorkoutDatabaseHelper.onUpgrade()` adding the new columns via `ALTER TABLE`.
- Precise Android TTS initialization, language fallback (`Locale.US`), and audio ducking over media playback.
- Unit tests for `TestProtocolStateMachine` verifying transitions, countdowns, recovery delta calculations, and steady-phase metric isolation.
