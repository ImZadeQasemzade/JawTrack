# JawTrack

A nightly bruxism (teeth-grinding) monitor that fuses on-device audio detection with Garmin
heart-rate/sleep data via Health Connect. Wellness self-tracking only — not a medical device,
no diagnosis or treatment claims. See `docs/JawTrackSpec.md` for the full technical spec this
build follows (phases, algorithms, data model, acceptance criteria).

## Status: Phase 0–3 built, not yet device-verified

The spec is explicit that later phases must not start before Phase 1 has survived multiple
real nights on the target phone (§9, §12): *"Phase 1 is the real risk phase... Everything
downstream is worthless if the recording stops at 2 a.m."* Phases 0–2 honored that gate before
moving on; Phase 3 was then built ahead of Phase 1/2's real-hardware checklists at explicit
request. See `docs/PHASE_STATUS.md` for exactly what's done, what's unverified, and what still
needs a real device.

**What works today:**
- Foreground recording service (`RecordingService`) using `AudioRecord` with `UNPROCESSED` ->
  `MIC` source fallback, NS/AGC explicitly disabled, 16 kHz mono capture on an urgent-priority
  reader thread with a zero-allocation hot loop.
- 30 s in-memory ring buffer (`AudioRingBuffer`, in `:core-logic`).
- Mic-loss detection and recovery with backoff, recorded as honest `Gap` rows.
- Charging requirement, low-battery-on-unplug graceful stop, 60 s heartbeat watchdog, and a
  "last night stopped early" banner computed from the watchdog on next launch.
- Per-manufacturer battery/autostart onboarding guidance (Samsung/Xiaomi/Huawei/Vivo/Oppo/
  OnePlus/realme) plus the standard battery-optimization exemption flow.
- Full Room schema for the entire spec (§7) so later phases don't need a migration to add
  tables — only `Session` and `Gap` are actively written to in Phase 1.
- Bed-partner consent notice, no-INTERNET-permission privacy guarantee (enforced by a test),
  `allowBackup=false`.
- Calibration night (§4.5): a from-scratch FFT + third-octave band analyzer measures the
  room's per-band noise floor across the night via reservoir sampling, without ever persisting
  raw audio. The first-ever session is forced into calibration mode; a "Recalibrate room"
  option is available afterward.
- The three-gate detection cascade + episode assembly (§4.7, §4.8): Gate 1 (energy, consumes
  the calibration `RoomProfile`), Gate 2's rejection policy, Gate 3's DSP heuristic (spectral
  flatness/centroid + snore-periodicity rejection), and `EpisodeAssembler`'s hysteresis/merge/
  duration-filter logic are all pure Kotlin in `:core-logic` and fully unit-tested. The Android
  wiring (`FrameWindower`, and a MediaPipe/YAMNet-backed `Gate2Classifier`) is written but
  **unverified** — no model asset is bundled, and the MediaPipe API surface hasn't been
  compiled against the real library. `RecordingService` catches a broken Gate 2 and degrades to
  Gate 1 + heuristic Gate 3 only rather than losing the night's recording over it.

**What's intentionally not built yet:** clip encryption, Health Connect sync, report screens,
correlation engine, PDF export, the trained classifier head. These are Phases 4–9.

## Repo layout

```
app/           Android application module (Kotlin, Jetpack Compose, Room, min SDK 29)
core-logic/    Pure-Kotlin/JVM module: ring buffer, coverage math, watchdog logic.
               No Android dependency — testable without an Android SDK.
docs/          Spec + phase status notes.
```

`app` depends on `core-logic` as a project dependency. Business logic that doesn't need
`android.*` APIs lives in `core-logic` specifically so it can be unit-tested fast, on any JVM.

## Building

Requires Android Studio (or the Android SDK + `ANDROID_HOME` set) because the Android Gradle
Plugin needs `android.jar`, which lives on `dl.google.com` and isn't available in every CI/
sandbox environment.

```
./gradlew :app:assembleDebug   # needs Android SDK
./gradlew :core-logic:test     # pure JVM, no SDK needed
```

CI (`.github/workflows/ci.yml`) runs `core-logic` tests on every push (no SDK required) and a
full `app` lint + unit test + assemble job using `android-actions/setup-android`.

## Before touching Phase 4+

Do the Phase 0 verification tasks from the spec (§2.1) on the actual target phone + Garmin
watch — none of this can be done from source code alone:

1. Install Health Connect, sync a real Garmin night, and dump what record types/cadence it
   actually writes (`SleepSessionRecord` stage sub-records? real `HeartRateRecord` interval?).
2. Confirm `AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` on the phone.
3. Run the app for several consecutive real nights and confirm the OEM battery-kill
   mitigations actually hold — this is Phase 1's own acceptance criterion (§9): *"records 8h
   with < 2% gap... survives two consecutive nights without OEM kill."*

`docs/PHASE_STATUS.md` has a checklist version of this.
