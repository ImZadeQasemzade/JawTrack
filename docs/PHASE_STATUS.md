# Phase status

Tracking against `JawTrackSpec.md` §9's phase table. The spec's own rule (§12): *"Do not
start Phase 3 before Phase 1 has survived multiple real nights on the target device."* Phases
0–2 were built in that order, honoring the rule. Phase 3 was then explicitly requested before
Phase 1/2's real-hardware checklists were run — that's a deliberate choice to keep building
ahead of validation, not an accident. The "not done: the acceptance criterion" callouts below
are how that tradeoff stays visible rather than silently skipped.

## Phase 0 — decisions and verifications

| Item | Status |
|---|---|
| D1–D6 locked | Done — see `build.gradle.kts` files, min SDK 29 / target 34, Compose, Room. |
| §2.1.1 Health Connect record dump on real Garmin sync | **Not done — needs a physical phone + Garmin watch.** |
| §2.1.2 Confirm `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` on target phone | **Not done — needs the physical phone.** Code handles both outcomes (`AudioCapture` falls back to `MIC` automatically) but which one actually gets used on your hardware is unverified. |
| §2.1.3 Confirm OEM battery-killer behavior | **Not done.** `OemBatteryAdvisor` has guidance for Samsung/Xiaomi/Huawei/Vivo/Oppo/OnePlus/realme, but none of it has been exercised against a real OEM ROM. |
| Repo, CI, test harness | Done — `:core-logic` unit tests run in CI without an Android SDK; `:app` build/lint/test job uses `android-actions/setup-android`. |

## Phase 1 — foreground service + AudioRecord + ring buffer + OEM battery flow

Code complete: `RecordingService`, `AudioCapture`, `AudioRingBuffer`, heartbeat watchdog,
charging requirement, OEM onboarding, gap recording, "stopped early" banner.

**Not done: the acceptance criterion itself.** §9 requires *"Records 8h with < 2% gap on the
actual phone, survives two consecutive nights without OEM kill."* That can only be verified by
installing the APK on the real target phone and actually running it overnight, repeatedly.
This is the one thing this codebase cannot do for you — the spec calls it out explicitly as
"the real risk phase" for exactly this reason.

### Before calling Phase 1 done, on the real device:

- [ ] Grant mic + notification permissions, disable battery optimization, apply OEM-specific
      guidance from onboarding.
- [ ] Start a night, let the phone sleep untouched, screen off, on the charger.
- [ ] Confirm the persistent notification's elapsed time is still counting the next morning.
- [ ] Check `sessions.coveragePct` / `gaps` in the Room DB — should be ≥ 98%.
- [ ] Repeat for a second consecutive night.
- [ ] Deliberately reproduce a mic interruption (e.g. a phone call) mid-session and confirm a
      `Gap` row is written and recording resumes afterward.
- [ ] Force-kill the app via the OEM's own task manager (not just `adb shell am force-stop`,
      which isn't representative) and confirm the next launch shows the "stopped early" banner.

## Phase 2 — calibration night + room profile

Code complete, ahead of its own real-hardware verification (same caveat as Phase 1: this was
authored in a sandbox with no Android SDK access).

- `core-logic/.../dsp`: a from-scratch radix-2 FFT + Hann window + third-octave band bucketing
  (`Fft`, `ThirdOctaveBands`, `BandEnergyAnalyzer`), verified against a brute-force DFT and
  known sinusoids.
- `core-logic/.../calibration`: `NoiseFloorTracker` (reservoir-sampled per-band percentile
  accumulator — bounded memory across a whole night without ever storing the night's audio,
  per §6.2) and `RoomProfileJson` (tiny hand-rolled codec, no JSON library needed for a flat
  numeric map).
- `CalibrationSampler` polls the Phase 1 ring buffer every 2s (deliberately *not* hooked into
  the audio reader's hot loop — FFT is too heavy for the urgent-priority thread) and feeds the
  tracker. `RecordingService` runs it only when `calibrationOnly`, and on clean stop finalizes
  a `RoomProfile` row and links it to the session.
- UI: the first-ever night is forced into calibration mode (no `RoomProfile` yet → the only
  button offered is "Start calibration night"); once one exists, a "Recalibrate room" option
  stays available per §4.5 ("re-run on user request").

**Not done: the acceptance criterion itself.** §9 requires *"Produces a per-band noise floor
from a real night."* That needs an actual calibration night on the actual phone — the DSP math
is unit-tested on synthetic tones, not on a real room's HVAC/traffic signature.

### Before calling Phase 2 done, on the real device

- [ ] Run a full calibration night, then inspect the `RoomProfile` row's `bandNoiseFloorJson` —
      sanity-check it against what the room actually sounded like (e.g. a band around any
      running HVAC/fan frequency should read elevated relative to a quiet band).
- [ ] Confirm calibration mode never writes raw audio to disk — only the finalized profile.
- [ ] Re-run calibration in a different room and confirm the profile changes accordingly.

## Phase 3 — Gates 1–2 + heuristic Gate 3 + episode assembly

Split cleanly by how verifiable each half is.

**Fully unit-tested, verified directly in this sandbox (pure Kotlin, no Android/ML dependency):**

- `core-logic/.../dsp`: `SpectralFeatures` (flatness, centroid) and `PeriodicityDetector`
  (autocorrelation-based ~3–6s snore-rhythm detection — §4.9's "build that test explicitly and
  unit-test it", done: `PeriodicityDetectorTest`).
- `core-logic/.../detection`:
  - `EnergyGate` — Gate 1's sustained-`noiseFloor+8dB` debounce, plus `forRoomProfile()` which
    is the thing that actually *consumes* Phase 2's `RoomProfile` output (via the new
    `RoomProfileMath.broadbandFloorDb`, since Gate 1 needs one scalar and calibration produces
    a per-band map).
  - `Gate2RejectionPolicy` — pure decision logic over `(label, score)` pairs (Speech →
    `MUST_DESTROY`, the other seven §4.7 reject classes → `REJECT`), tested without needing an
    actual classifier.
  - `GrindingHeuristicScorer` — Gate 3's v1 heuristic stand-in (§4.7: flatness in 1–6kHz,
    centroid in 1.5–4kHz, absence of snore-like periodicity, sustained-duration check),
    combining the above into a 0–1 score shaped like the eventual trained head's output.
  - `EpisodeAssembler` — hysteresis (enter >0.7/exit <0.4), <3s merge, <300ms/>30s duration
    filter, peak/mean/dominant-band aggregation (§4.8). The most heavily tested file in the
    repo — streaming mid-emission and merge-chain behavior included.

**Written but unverified (needs Android SDK, a real YAMNet `.tflite` asset, and a device):**

- `ml/Gate2Classifier` (interface) + `ml/YamnetGate2Classifier` (MediaPipe `AudioClassifier`
  implementation). See the large warning comment at the top of that file — the API surface is
  a best reconstruction, not compiled-and-checked, and **no model asset is bundled**
  (`app/src/main/assets/yamnet.tflite` doesn't exist in this repo). It also uses AUDIO_CLIPS
  mode rather than the spec's STREAM mode, because `FrameWindower` polls the ring buffer for
  discrete windows rather than pushing a continuous stream — documented in the same comment.
- `detection/FrameWindower` — polls the ring buffer (same pattern as Phase 2's
  `CalibrationSampler`) to run Gate 1 every ~200ms and Gates 2/3 once Gate 1 sustains, feeding
  `EpisodeAssembler` and persisting completed episodes via `SessionRepository.saveEpisode`.
- `RecordingService` now builds a `Gate2Classifier` at session start and **catches any failure**
  (missing asset, bad API call, anything) — a broken Gate 2 degrades to "Gate 1 + heuristic
  Gate 3 only" rather than taking the whole night's recording down. This is untested because
  there's no way to make Gate 2 actually fail or succeed without a real model in this sandbox.
- Episode persistence added a field the original §7 schema table omitted:
  `Episode.rejectedClasses`, which §4.8's prose explicitly calls for recording. Safe to add —
  no released schema/migration exists yet.

**Not done, and can't be done without real audio:** §9's acceptance criterion (*">80% recall on
a synthetic set of injected grinding clips"*) and §10's whole validation plan (gold nights,
synthetic injection at varying SNR, the snoring/TV/sleep-talking negative test suite). All of
that needs actual recorded audio — grinding samples, room noise, snoring — which doesn't exist
in this repo and can't be fetched in this sandbox.

### Before calling Phase 3 done, on the real device

- [ ] Bundle a real YAMNet `.tflite` and verify `YamnetGate2Classifier` against the actual
      `com.google.mediapipe:tasks-audio` API (method names, builder pattern, result shape) —
      fix whatever doesn't match; it was written from memory, not compiled.
- [ ] Confirm the MediaPipe dependency version in `app/build.gradle.kts` resolves — pin
      whatever's actually current on Google's Maven repo.
- [ ] Run §10's synthetic injection tests and negative test suite (snoring night, TV night,
      sleep-talking night) and check recall/false-positives-per-hour — this is the actual gate
      for whether Phase 3 is usable, not just "does it compile."
- [ ] Sanity-check `GrindingHeuristicScorer`'s four thresholds against real clips; they're
      currently the literal numbers from §4.7 with no empirical tuning.
- [ ] Confirm a Speech-flagged window really never reaches disk (§6.4) — this needs a real
      device test, not just the `MUST_DESTROY` unit-level logic check.

## Phase 4+ — not started

ClipStore + Keystore encryption, Health Connect sync, report UI, labeling loop, correlation
engine, PDF export, trained classifier swap — all pending the Phase 3 checklist above on real
hardware with real audio.

The full Room schema for these phases (§7) is already in place (`app/.../data/db/entities`)
so adding them later won't require a destructive migration, but their DAOs are unused CRUD
stubs until each phase actually wires a pipeline into them — see the per-phase comment on each
`@Dao` in `data/db/dao/FutureDaos.kt`.
