# Fix Heart Rate Ceiling Alert Crash Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate application crash/force-close when heart rate reaches the MAF ceiling limit by granting the required `VIBRATE` permission and guarding haptic/audio alert dispatch against uncaught exceptions.

**Architecture:** Add `android.permission.VIBRATE` to `AndroidManifest.xml`. Harden `AudioAlertEngine.playCeilingAlert()` to inspect `hasVibrator()` and swallow any unexpected platform `SecurityException` / `Throwable`. Guard alert invocation inside `WorkoutTrackingService` with `runCatching` to guarantee the background tracking loop survives any audio/haptic subsystem failures.

**Tech Stack:** Kotlin, Android Jetpack, Android AudioManager/Vibrator APIs, Gradle.

**Spec:** Brainstorming discussion diagnosing missing `android.permission.VIBRATE` triggering `java.lang.SecurityException` inside `AudioAlertEngine.playCeilingAlert()` called from `WorkoutTrackingService`.

## Global Constraints

- Android API compatibility: minSdk 26, targetSdk 35.
- Zero regression on existing BLE tracking, sound pool alerts, or cadence algorithms.
- Conventional commits format (`fix: ...`).

## Review Focus

1. Device has no vibration actuator: `hasVibrator()` is false; ensure vibration is safely skipped.
2. OEM-specific security restriction or revocation: ensure `playCeilingAlert()` catches `SecurityException` / `Exception` without rethrowing.
3. Audio focus failure during ceiling alert: ensure transient audio focus failure does not bubble up to crash the service.
4. Concurrent alert evaluation: ensure timestamp updates and state mutations remain intact even if alert playback fails.
5. SoundPool playback error: ensure ceiling tone generation and SoundPool calls remain intact.

---

### Task 1: Declare VIBRATE permission in AndroidManifest.xml

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: Platform vibration capability
- Produces: `android.permission.VIBRATE` granted to application process

- [x] **Step 1: Inspect AndroidManifest.xml and add VIBRATE permission**

Add `<uses-permission android:name="android.permission.VIBRATE" />` alongside existing hardware/system permissions in `app/src/main/AndroidManifest.xml`.

- [x] **Step 2: Verify manifest compilation**

Run: `./gradlew processDebugMainManifest`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "fix(security): declare VIBRATE permission in AndroidManifest"
```

---

### Task 2: Harden Haptic Feedback in AudioAlertEngine

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/audio/AudioAlertEngine.kt`

**Interfaces:**
- Consumes: `vibrator: Vibrator?`
- Produces: `fun playCeilingAlert()` safe against any haptic or audio focus exceptions

- [x] **Step 1: Wrap vibration call with hardware check and safe exception boundary**

In `AudioAlertEngine.playCeilingAlert()`:
Check `vibrator?.hasVibrator() == true`. Wrap the `vibrator?.vibrate(...)` block in `try-catch (e: Exception)` to safely log or swallow any security/hardware exceptions without propagating to the caller.

- [x] **Step 2: Verify compilation and existing unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/mafrunner/app/audio/AudioAlertEngine.kt
git commit -m "fix(audio): guard haptic vibration with hardware check and exception safety net"
```

---

### Task 3: Guard Alert Dispatches in WorkoutTrackingService

**Files:**
- Modify: `app/src/main/kotlin/com/mafrunner/app/service/WorkoutTrackingService.kt`

**Interfaces:**
- Consumes: `alertEngine: AudioAlertEngine`
- Produces: Resilient heart rate alert triggers inside BLE collector loop

- [x] **Step 1: Wrap alert engine triggers in WorkoutTrackingService**

In `WorkoutTrackingService.kt` around line 97-104:
Wrap `alertEngine.playCeilingAlert()` and `alertEngine.playFloorAlert()` inside a `runCatching { ... }.onFailure { e -> Log.e("WorkoutTrackingService", "Failed to play alert", e) }` block so background tracking continues uninterrupted.

- [x] **Step 2: Verify full debug build and unit tests**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/mafrunner/app/service/WorkoutTrackingService.kt
git commit -m "fix(service): protect workout tracking coroutine from alert dispatch failures"
```
