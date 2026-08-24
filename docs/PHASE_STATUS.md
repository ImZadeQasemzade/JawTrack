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

## Phase 4 — ClipStore + Keystore encryption + retention + speech destruction

Built ahead of Phase 3's own real-hardware checklist, same as Phase 3 was ahead of Phase 1/2's.

**Fully unit-tested (pure Kotlin):**

- `core-logic/.../clips/ClipCapping` — the §6.3 12-second clip cap, pulled out of `ClipStore`
  specifically so it's testable outside Android: truncates to the *most recent* N samples
  (verified it's the tail, not the head, that survives), passes shorter audio through
  unchanged. `ClipStore` calls this rather than re-implementing truncation itself.
- `core-logic/.../retention/RetentionPolicy` — `computeExpiresAt` (clamped to the 3–90 day
  range §6.7 specifies, default 14) and `isExpired`'s exact boundary (`now >= expiresAt`,
  tested at expiry-minus-one/exactly-at/expiry-plus-one).
- Gate 2's `MUST_DESTROY` verdict for Speech (§6.4) was already tested in Phase 3 — Phase 4
  is what actually acts on it: `FrameWindower` returns immediately on that verdict, before
  `EpisodeAssembler.process()` is ever called, so no clip-capture payload is ever captured for
  that window, let alone written. That control-flow guarantee itself is Android code and can't
  be unit-tested here (see below), but the decision it depends on is.

**Written but unverified (needs Android Keystore + a real device — this sandbox has neither):**

- `clips/ClipStore` — AES-256-GCM via a hand-rolled `javax.crypto`/`java.security.KeyStore`
  wrapper (not `androidx.security:security-crypto`, which §6.5 says is in maintenance mode; not
  Tink, to avoid another dependency this sandbox can't verify resolves). One app-wide Keystore
  key, StrongBox-backed where available with a fallback if not, a fresh random IV per clip
  (stored length-prefixed alongside the ciphertext). Unlike `YamnetGate2Classifier`, everything
  here is standard JDK/Android framework API rather than a third-party library, so the risk
  profile is lower — but Android Keystore genuinely cannot be exercised outside a device/
  emulator, so none of it has actually run.
- Clip capture required extending `EpisodeAssembler` itself: `process()` now takes an optional
  `capturePayload` lambda, invoked exactly once, synchronously, at a run's true onset — the
  ring-buffer snapshot has to happen *then* (up to 12s trailing, matching "10s pre-onset +
  episode start") because by the time a long episode finishes, that audio may have already
  rolled out of the 30s ring buffer. The payload rides through merges via the same
  earliest-wins logic the episode stats already use, rather than a second parallel state
  machine in `FrameWindower` that could drift out of sync — this was a deliberate design
  choice, not the obvious first draft. 3 new tests cover it (capture-once, merge keeps the
  earlier payload, no-lambda-supplied stays null); all 15 pre-existing `EpisodeAssembler` tests
  still pass unchanged since the parameter is optional.
- `clips/RetentionWorker` (WorkManager `CoroutineWorker`) — daily periodic + one-shot on every
  launch, per §6.7. Untested: needs `WorkManager.getInstance()`, which needs a real Context.
- "Delete all audio" button on `StartNightScreen`, with a confirmation dialog (destructive, no
  undo) — `ClipRepository.deleteAllClips()` wipes every clip file and DB row immediately.

**Not done, and can't be done without real audio:** actually confirming a clip decrypts back to
the original audio, that StrongBox key generation and its non-StrongBox fallback both work on
real hardware, and that the 14-day default retention actually purges a real clip file from disk
via WorkManager. All of this is standard, well-understood Android API usage — the code risk
here is much lower than Phase 3's MediaPipe integration — but "should work" isn't "verified."

### Before calling Phase 4 done, on the real device

- [ ] Write a clip, kill and restart the app, read it back, confirm the decrypted audio matches.
- [ ] Confirm `KeyGenParameterSpec.Builder.setIsStrongBoxBacked(true)` actually succeeds on a
      StrongBox-capable device, and that the non-StrongBox fallback path works on one without.
- [ ] Set a clip's `expiresAt` to the past directly in the DB (or wait out a short retention
      window), trigger `RetentionWorker`, and confirm both the file and the DB row are gone.
- [ ] Confirm the daily periodic work actually survives Doze/reboot per WorkManager's normal
      guarantees — nothing JawTrack-specific here, but worth a real check.
- [ ] Tap "Delete all audio" mid-development with a few real clips saved and confirm every file
      under `filesDir/clips/` is actually gone, not just the DB rows.
- [ ] Re-run §9's Phase 3 checklist alongside this one — Phase 4 only writes a clip when Phase
      3's detection pipeline actually produces a valid episode, so they're only truly testable
      together on a real night.

## Phase 5 — Health Connect sync + enrichment

Built ahead of Phase 3/4's own real-hardware checklists, same tradeoff as each prior phase.

**Fully unit-tested (pure Kotlin, 114 core-logic tests total now):**

- `core-logic/.../enrichment/SleepStageJoiner` — joins an episode to whichever sleep stage it
  overlaps most by duration (§5.3 step 2), and sums non-`AWAKE`/`OUT_OF_BED` stage time for the
  episodes-per-hour-of-sleep denominator. §5.1's own framing ("stage intervals are minutes
  long") is exactly why this join is trustworthy where per-episode heart rate isn't.
- `core-logic/.../enrichment/NightMetricsCalculator` — episode count, the JawTrack Index
  (episodes/hour *of sleep*, not of time-in-bed), total grinding seconds, longest episode,
  stage distribution (episode counts per stage, not time spent), snore index (§7.1).
- `core-logic/.../enrichment/ConfidenceGrader` — the A/B/C grade §7.1 calls "important and
  often skipped." The spec names five inputs (coverage %, room noise floor, speech-rejection
  count, labeled proportion, clean shutdown) but not exact weights or thresholds; the weights,
  the speech-rejections-per-hour cap, and the quiet/noisy floor reference points in this file
  are a documented, defensible interpretation, not values taken from the spec — flagged as
  such in the file's own doc comment, and worth revisiting once real nights exist to compare
  grades against.
- `core-logic/.../enrichment/StageDistributionJson` — same hand-rolled flat-object codec
  pattern as `RoomProfileJson`, for `NightMetrics.stageDistributionJson`.

**Written but unverified (needs a real Health Connect provider with actual Garmin-synced
data — this sandbox has neither):**

- `health/HealthConnectRepo` — reads `HeartRateRecord`, `HeartRateVariabilityRmssdRecord`,
  `SleepSessionRecord` (mapping its `stages` to plain string labels), and
  `RespiratoryRateRecord` over a session's window. `androidx.health.connect` is a stable,
  long-documented Jetpack library rather than a fast-moving third-party one like the MediaPipe
  integration, so confidence in the API shape is higher than `YamnetGate2Classifier`'s — but
  nothing here has compiled or run either.
- `health/HealthConnectRationaleActivity` + the manifest's rationale activity/activity-alias —
  Health Connect refuses to show its permission dialog without one (§5.1). JawTrack has no
  hosted privacy policy (no server exists), so this states the same in-app commitments the
  rest of the app already makes, rather than linking to a page that doesn't exist. The exact
  manifest incantation Health Connect expects has shifted as it moved from a standalone app
  into AOSP across `connect-client` versions — flagged unverified in both files.
- `health/EnrichmentWorker` — reads the window, joins episodes to stages, computes metrics via
  the tested core-logic pieces above, and marks the session `PARTIAL` + retries with backoff
  if Health Connect returned literally nothing (§5.3's "watch hasn't synced" case), or
  `ENRICHED` once real data came back. Enqueued from `MainActivity.onResume()` — "on app
  foreground in the morning," per the spec.
- Onboarding gained an optional "Connect Health Connect" step using
  `PermissionController.createRequestPermissionResultContract()`. Deliberately non-blocking —
  recording works fully without Health Connect; enrichment just never runs.
- `Session` gained `speechRejectedCount`, `snoringRejectedCount` (both counters, wired from
  `FrameWindower`'s Gate 2 verdicts — snoring feeds the night's snore index for free, exactly
  as §7.1 says it should) and `enrichmentState` (`PENDING`/`PARTIAL`/`ENRICHED`, independent of
  the recording lifecycle's own `SessionState`).

**Not done, and can't be done here:** there's no "waiting on Garmin sync" banner yet, because
there's no report screen for it to live on — that's Phase 6. `EnrichmentState`/`NightMetrics`
are ready for Phase 6 to read; the UI for them is deliberately not built early.

### Before calling Phase 5 done, on the real device

- [ ] Complete §2.1.1 for real: install Health Connect, sync a real Garmin night, and confirm
      `SleepSessionRecord.stages` is actually populated (vs. an envelope-only session) and what
      `HeartRateRecord`'s real sample cadence is. This was always the first Phase 0 task and
      still hasn't happened — everything in this phase is built against the *documented* shape
      of these records, not a verified one.
- [ ] Fix whatever's wrong in the manifest's rationale activity/activity-alias wiring so the
      Health Connect permission dialog actually appears — check against current docs, not this
      repo's guess.
- [ ] Confirm a real `EnrichmentWorker` run against synced data produces a sensible
      `NightMetrics` row, and that the `PARTIAL`/retry path actually triggers when run before
      the watch has synced.
- [ ] Sanity-check `ConfidenceGrader`'s weights against a few real nights of known quality
      (e.g. a night you know had bad coverage should grade lower than a clean one) — they're
      currently reasoned-through defaults, not tuned.
- [ ] Confirm `RespiratoryRateRecord` data (read but not yet used by any metric) is actually
      worth surfacing once Phase 6 builds report screens, or drop it if Garmin never populates it.

## Phase 6+ — not started

Report UI, labeling loop, correlation engine, PDF export, trained classifier swap — all pending
the Phase 3/4/5 checklists above on real hardware with real audio and a real Garmin sync.

The full Room schema for these phases (§7) is already in place (`app/.../data/db/entities`)
so adding them later won't require a destructive migration, but their DAOs are unused CRUD
stubs until each phase actually wires a pipeline into them — see the per-phase comment on each
`@Dao` in `data/db/dao/FutureDaos.kt`.
