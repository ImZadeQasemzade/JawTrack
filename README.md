# JawTrack

A nightly bruxism (teeth-grinding) monitor that fuses on-device audio detection with Garmin
heart-rate/sleep data via Health Connect. Wellness self-tracking only — not a medical device,
no diagnosis or treatment claims. See `docs/JawTrackSpec.md` for the full technical spec this
build follows (phases, algorithms, data model, acceptance criteria).

## Status: Phase 0–6 built, not yet device-verified

The spec is explicit that later phases must not start before Phase 1 has survived multiple
real nights on the target phone (§9, §12): *"Phase 1 is the real risk phase... Everything
downstream is worthless if the recording stops at 2 a.m."* Phases 0–2 honored that gate before
moving on; Phases 3 through 6 were then each built ahead of the prior phase's real-hardware
checklist, at explicit request. See `docs/PHASE_STATUS.md` for exactly what's done, what's
unverified, and what still needs a real device.

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
- Clip encryption + retention (§6.3, §6.5–§6.7): a hand-rolled AES-256-GCM `ClipStore` backed
  by the Android Keystore (StrongBox where available), never `androidx.security:security-crypto`
  or a third-party crypto library. Clip capture required extending `EpisodeAssembler` with an
  optional onset-payload hook so a clip is anchored to the episode's *true* onset — captured
  before that pre-onset audio can roll out of the 30s ring buffer — rather than reimplementing
  the assembler's merge logic a second time just for audio bytes. `RetentionWorker` purges
  expired clips daily and on launch; a "Delete all audio" button does it immediately, no undo.
  The 12s clip-length cap and the retention-expiry math are pure Kotlin and unit-tested; the
  actual Keystore/file I/O needs a device to verify, same as Phase 3's ML wiring.
- Health Connect sync + enrichment (§5, §7.1): `SleepStageJoiner` (episode-to-sleep-stage by
  overlap), `NightMetricsCalculator` (the JawTrack Index, total grinding time, snore index),
  and `ConfidenceGrader` (the A/B/C grade §7.1 calls "important and often skipped" — the spec
  names five inputs but not exact weights, so this is a documented, defensible interpretation,
  not spec'd values) are pure Kotlin and unit-tested. `HealthConnectRepo` and `EnrichmentWorker`
  are written against `androidx.health.connect`'s documented (stable, long-established) API but
  unverified — no real Health Connect provider or Garmin-synced data exists in this sandbox to
  exercise them against. A session left `PARTIAL` (watch hasn't synced) retries with backoff;
  there's no "waiting on sync" banner yet since there's no report screen for it to live on —
  that's Phase 6.

- Report screens + labeling loop (§8): Screen 1 (`LastNightScreen`) shows the JawTrack Index,
  A/B/C confidence grade, delta vs. the 7-night rolling average, and a plain-language summary
  sentence — leading with a "stopped early" warning instead of the index when the service didn't
  shut down cleanly, so an incomplete night never reads as reassuringly low. Screen 2
  (`TimelineScreen`) is a pinch-to-zoom Canvas sharing one `TimelineLayout`-derived x-axis across
  sleep-stage bands, HR line, gap hatching, and episode ticks (height = peak score); tapping a
  tick opens Screen 3. Screen 3 (`EpisodeDetailScreen`) decrypts a clip to memory only (never a
  temp file), renders its waveform + spectrogram, plays it back, and the three labeling buttons
  (Grinding/Not grinding/Not sure) each write `Episode.userLabel` + an append-only `Label` row
  and advance to the next unlabeled episode in the session — a swipe does the same advance
  without ever silently recording a label. The x-axis math, summary sentence, trend delta,
  waveform downsampling, and spectrogram generation are pure Kotlin and unit-tested; the Canvas
  rendering, gestures, and `AudioTrack` playback are Android-only and unverified.

**What's intentionally not built yet:** correlation engine, PDF export, the trained classifier
head. These are Phases 7–9.

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

## Before touching Phase 7+

Do the Phase 0 verification tasks from the spec (§2.1) on the actual target phone + Garmin
watch — none of this can be done from source code alone:

1. Install Health Connect, sync a real Garmin night, and dump what record types/cadence it
   actually writes (`SleepSessionRecord` stage sub-records? real `HeartRateRecord` interval?).
2. Confirm `AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` on the phone.
3. Run the app for several consecutive real nights and confirm the OEM battery-kill
   mitigations actually hold — this is Phase 1's own acceptance criterion (§9): *"records 8h
   with < 2% gap... survives two consecutive nights without OEM kill."*

`docs/PHASE_STATUS.md` has a checklist version of this.
