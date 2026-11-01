# Cadence Audio Gain Calibration & History Analytics Specification

**Date:** 2026-11-01  
**Project:** MAF Runner Android  
**Target:** Acoustic sensitivity overhaul & comprehensive workout analytics  

---

## 1. Problem Statement
1. **Acoustic Sensitivity Too Low:** The microphone audio capture cannot detect footstrikes even at maximum sensitivity because `AudioSource.MIC` activates OS speech noise suppression, linear RMS lacks digital amplification, and the threshold formula had an unreachable `0.20` minimum.
2. **Immutable History:** Workouts cannot be viewed in detail, edited (speed, distance, benchmark tag), or deleted after finishing.
3. **Missing Longitudinal Analytics:** The runner cannot track aerobic base progress over time across weekly, monthly, and quarterly intervals.

---

## 2. Requirements & Architecture

### 2.1 Acoustic Audio Pipeline Overhaul (`AcousticCadenceDetector.kt`, `FootstrikePeakDetector.kt`)
* **Unprocessed Audio Input:** Use `MediaRecorder.AudioSource.VOICE_RECOGNITION` to disable hardware ambient noise suppression.
* **12x Digital Pre-Gain:** Scale raw PCM audio by 12.0 before RMS calculation to map acoustic deck thuds into 40%–85% meter range.
* **Floating Ambient Noise Floor:** Continuously track ambient motor noise floor.
* **Dynamic Sensitivity Clearance:**
  * Sensitivity slider (1 to 10) sets clearance margin above noise floor:
    `minClearance = 0.05f + (10 - sensitivity) * 0.04f`
    `threshold = (noiseFloor + minClearance).coerceIn(0.08f, 0.95f)`
  * Sensitivity 10 allows light deck taps to trigger; Sensitivity 1 requires forceful strikes.

### 2.2 Workout Record Detail, Edit & Delete (`WorkoutDetailDialog.kt`, `WorkoutRepository.kt`)
* **Inspection Sheet:** Tapping any history card opens a modal showing:
  * Full metrics: Duration, Avg/Max HR, Avg Cadence, total estimated steps.
  * Zone time breakdown: Under, In Zone, Over seconds and percentages.
  * Single-workout CSV & TCX export button.
* **Editing & Deletion:**
  * Editable text inputs for Treadmill Speed (km/h) and Distance (km).
  * Toggle for MAF Benchmark Run.
  * Delete Run button with confirmation dialog.
  * `WorkoutRepository.deleteWorkout(id)` removes record and cascaded samples.

### 2.3 Time-Windowed Analytics (`WorkoutAnalyticsEngine.kt`, `HistoryScreen.kt`)
* **Time Range Filter:** Segmented control: `[Week] [Month] [Quarter] [All]`.
* **Aggregated Metrics:**
  * **Total Time & Distance:** Sum of run durations and km logged.
  * **MAF Compliance Rate:** Aggregate in-zone percentage across all runs in period.
  * **Aerobic Speed Progression:** Speed delta between earliest and latest benchmark runs in the period.
  * **Tri-Zone Distribution:** Visual stacked bar for Under, In Zone, and Over ceiling hours/minutes.
  * **Run Count:** Number of completed sessions.

---

## 3. Review Focus
1. **Audio Clipping:** High-gain amplification must not saturate or cause audio buffer overflows.
2. **Database Cascade Deletion:** Deleting a workout must delete associated heart rate samples from SQLite.
3. **Empty Analytics State:** Selecting a time window with zero runs must show a clean fallback UI without divide-by-zero crashes.
4. **Dialog Dismissal & Refresh:** Saving or deleting in `WorkoutDetailDialog` must immediately refresh the history list and active analytics metrics.
