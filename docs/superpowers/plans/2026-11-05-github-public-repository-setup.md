# Public GitHub Repository Setup & Safe Push Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cleanly configure, document, sanitize, and push the MAF Runner Android project to the public GitHub repository at `https://github.com/khairul6146/MAF-runner`.

**Architecture:** Purge ephemeral brainstorming and local execution state (`.superpowers/`) from git tracking and add to `.gitignore`. Author an MIT `LICENSE` and a comprehensive, publication-ready `README.md` documenting the MAF 180 method, acoustic treadmill cadence detection, BLE heart rate/battery monitoring, guided MAF test protocol, and 2-minute cardiac recovery. Configure `origin` remote and safely push the `main` branch with upstream tracking.

**Tech Stack:** Git, Markdown, MIT License, Android/Gradle project standards.

**Spec:** User request: "push to git https://github.com/khairul6146/MAF-runner the repo is set as public, setup accordingly and safely"

## Global Constraints
- Remote repository URL: `https://github.com/khairul6146/MAF-runner.git`
- Branch name: `main`
- Public-safe sanitization: Zero API tokens, private keys, local SDK paths (`local.properties`), or ephemeral execution artifacts committed.
- Documentation standards: Strict markdown linter compliance (MD022 heading spacing, MD060 padded tables, MD030/MD032 list spacing).
- Deliverables: All source code, Gradle configurations, tests, docs, MIT License, and comprehensive README published on GitHub.

## Review Focus
1. **Accidental Secret or Local Path Leakage:** Confirm that neither `local.properties` nor any local machine paths (`C:\Users\khair\...`) in ephemeral files are committed to git.
2. **Ephemeral Brainstorm Artifacts:** Ensure `.superpowers/` directory (containing local server PIDs, ports, and tokens) is untracked and gitignored.
3. **Clean Git Remote Configuration:** Verify remote `origin` points precisely to `https://github.com/khairul6146/MAF-runner.git` and test connectivity before pushing.
4. **Non-Destructive Git Push:** Ensure standard push with upstream tracking (`git push -u origin main`), avoiding `--force` or destructive resets.
5. **Verified Public README:** Ensure all links, setup instructions, hardware compatibility notes, and permissions disclosures in `README.md` are accurate and complete.

---

### Task 1: Git Hygiene & Ephemeral State Sanitization

**Files:**
- Modify: `.gitignore`
- Untrack: `.superpowers/`

**Interfaces:**
- Consumes: Existing git index and `.gitignore`.
- Produces: Sanitized git index with `.superpowers/` untracked and excluded from future commits.

- [x] **Step 1: Untrack `.superpowers/` from git index**
  Run: `git rm -r --cached .superpowers/`
  Expected: All files under `.superpowers/` removed from index while preserved on disk.

- [x] **Step 2: Add `.superpowers/` to `.gitignore`**
  Append `.superpowers/` to `.gitignore`.

- [x] **Step 3: Verify no sensitive files or local properties are tracked**
  Run: `git status`
  Expected: Deleted tracked files under `.superpowers/`, `.gitignore` modified. `local.properties` remains untracked.

- [x] **Step 4: Commit git hygiene changes**
  Run:
  ```bash
  git add .gitignore
  git commit -m "chore(git): untrack ephemeral superpowers state and add to .gitignore"
  ```

---

### Task 2: Open Source License Setup (MIT)

**Files:**
- Create: `LICENSE`

**Interfaces:**
- Consumes: Copyright details (Year 2026, khairul6146).
- Produces: Standard MIT License file.

- [x] **Step 1: Create `LICENSE` file**
  Write standard MIT License text with Copyright (c) 2026 khairul6146.

- [x] **Step 2: Verify `LICENSE` file existence and formatting**
  Verify file content matches standard OSI MIT license.

- [x] **Step 3: Commit `LICENSE`**
  Run:
  ```bash
  git add LICENSE
  git commit -m "chore: add MIT License"
  ```

---

### Task 3: Comprehensive Public README & Documentation

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: Architecture, feature specifications, hardware requirements, and build instructions.
- Produces: Full documentation of MAF Runner Android app for public GitHub release.

- [x] **Step 1: Draft `README.md`**
  Include:
  - Project Title, Tagline, and Status Badges.
  - What is MAF Runner? (180 Formula aerobic base training, OLED Nordic UI).
  - Key Features:
    - 180 Formula Zone Calculation (Floor, Ceiling, Target).
    - BLE Heart Rate & Battery Telemetry (Standard SIG 0x180D, 0x180F, 0x2A19 with low-battery warning).
    - Acoustic Treadmill Cadence Detection (Phone microphone detects belt footstrikes, 400-800Hz bandpass filter, real-time VU meter).
    - Guided MAF Test Protocol (Warmup, Steady MAF Test, 2-Minute Cardiac Recovery with $\Delta HR_{60s}$, $\Delta HR_{120s}$ and clinical rating).
    - Audio TTS Coaching Alerts (20s rate-limited spoken voice guidance on zone drift).
    - Session History, Analytics & Garmin/Strava Export (TCX & CSV).
    - 100% Offline & Privacy-First (Local SQLite database, zero tracking).
  - Architecture & Tech Stack (Kotlin 2.1, Jetpack Compose, Material 3 Nordic OLED, Android Foreground Service, Coroutines/Flow).
  - Permissions Disclosure & Rationale.
  - Hardware Compatibility (Coospo H9Z, Polar H10, Garmin HRM-Pro/Dual, Wahoo TICKR).
  - Building & Installation Guide (`./gradlew assembleDebug`).

- [x] **Step 2: Verify Markdown formatting and file structure**
  Verify no syntax issues, proper heading padding, and clean link references.

- [x] **Step 3: Commit `README.md`**
  Run:
  ```bash
  git add README.md
  git commit -m "docs: add comprehensive project README for public release"
  ```

---

### Task 4: Git Remote Configuration & Safe Push

**Files:**
- Output: Remote GitHub repository synchronized at `https://github.com/khairul6146/MAF-runner`.

**Interfaces:**
- Consumes: Local `main` branch, Remote URL `https://github.com/khairul6146/MAF-runner.git`.
- Produces: Upstream-tracked `origin/main` branch on GitHub.

- [x] **Step 1: Configure git remote origin**
  Run: `git remote add origin https://github.com/khairul6146/MAF-runner.git`
  Expected: Remote `origin` configured.

- [x] **Step 2: Verify remote configuration**
  Run: `git remote -v`
  Expected: Both `fetch` and `push` point to `https://github.com/khairul6146/MAF-runner.git`.

- [x] **Step 3: Verify current branch is `main`**
  Run: `git branch --show-current`
  Expected: `main`.

- [x] **Step 4: Push to GitHub with upstream tracking**
  Run: `git push -u origin main`
  Expected: Successful push with objects written and `main -> main (forced-update: false)`.

- [x] **Step 5: Verify git status and branch tracking**
  Run:
  - `git status`
  - `git branch -vv`
  Expected: "Your branch is up to date with 'origin/main'", clean working tree.
