# Phase status

Tracking against `JawTrackSpec.md` §9's phase table. The spec's own rule (§12): *"Do not
start Phase 3 before Phase 1 has survived multiple real nights on the target device."* This
build honors that — Phase 2+ has not been started.

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

## Phase 3+ — not started

Detection gates, episode assembly, clip encryption, Health Connect sync, report UI, labeling
loop, correlation engine, PDF export, trained classifier — all pending Phase 1 (and now Phase
2) clearing their checklists above on the actual target phone. Gate 1's `noiseFloor + 8dB`
energy threshold (§4.7) and Gate 3's spectral-flatness/centroid heuristics (§4.7) are the first
things that will consume the `RoomProfile` this phase produces.

The full Room schema for these phases (§7) is already in place (`app/.../data/db/entities`)
so adding them later won't require a destructive migration, but their DAOs are unused CRUD
stubs until each phase actually wires a pipeline into them — see the per-phase comment on each
`@Dao` in `data/db/dao/FutureDaos.kt`.
