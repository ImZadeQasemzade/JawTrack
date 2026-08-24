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

## Phase 2+ — not started

Calibration night, detection gates, episode assembly, clip encryption, Health Connect sync,
report UI, labeling loop, correlation engine, PDF export, trained classifier — all pending
Phase 1 clearing the checklist above on the actual target phone.

The full Room schema for these phases (§7) is already in place (`app/.../data/db/entities`)
so adding them later won't require a destructive migration, but their DAOs are unused CRUD
stubs until each phase actually wires a pipeline into them — see the per-phase comment on each
`@Dao` in `data/db/dao/FutureDaos.kt`.
