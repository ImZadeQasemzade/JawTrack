# JawTrack — Technical Specification v0.2 (Android)

**A nightly bruxism (teeth-grinding) monitor that fuses on-device audio detection with Garmin heart-rate and sleep data, and delivers an actionable morning report.**

This document is the build brief for the implementing coding agent. It defines scope, architecture, algorithms, data model, phasing, and acceptance criteria.

**Target: Android 10+ (API 29), Kotlin, Jetpack Compose, Health Connect, LiteRT/TFLite. Single-user, sideloaded.**

---

## 1. Product definition

### 1.1 What it does
1. Runs overnight on an Android phone placed near the bed (plugged in), listening on the microphone via a foreground service.
2. Detects candidate grinding events in real time using a lightweight on-device audio classifier.
3. Saves only a short encrypted audio clip around each event — never the full night.
4. Pulls heart rate, HRV, and sleep-stage data from the Garmin watch via Health Connect and joins it to the detected events.
5. In the morning, presents a report: how many episodes, when they happened, what sleep stage they fell in, what the heart rate did, and playable evidence clips.
6. Learns from the user's confirm/reject taps on those clips to improve its own detector.
7. Over weeks, correlates the nightly bruxism index against logged daily factors (caffeine, alcohol, stress, exercise, night guard use) and surfaces what actually moves the number.

### 1.2 Explicit non-goals
- **Not a medical device.** No diagnosis, no treatment claims, no apnea detection claims. Wellness/self-tracking framing only. Every report screen carries a disclaimer. Do not implement anything that outputs a clinical severity label ("you have severe bruxism").
- Not a cloud service in v1. No user accounts, no server, no audio ever leaving the device.
- Not a general sleep tracker — Garmin already does that. JawTrack consumes Garmin's sleep output rather than re-deriving it.
- Not multi-user. Single user, single device.
- Not Play Store distribution in v1. Sideload / internal testing track only (see §2, D4).

### 1.3 Why the two-sensor design matters
Audio alone is insufficient and the spec must be honest about this:
- **Grinding** (tooth-on-tooth) is audible → detectable by mic.
- **Clenching** (static isometric jaw load) is **silent** → invisible to a mic. It is a large fraction of real bruxism activity.

Clenching episodes still produce a sympathetic arousal: in the literature, rhythmic masticatory muscle activity is preceded by autonomic activation, an EEG arousal a few seconds prior, and a heart-rate rise essentially concurrent with onset. So heart-rate arousal is used as a **corroborating and gap-filling channel**, not decoration. This is the core reason the app is worth building rather than just being a sound recorder.

---

## 2. Phase 0 — decisions and verifications before coding

| # | Decision | Resolution |
|---|---|---|
| D1 | Platform | **Android, Kotlin, min API 29 (Android 10), target API 34+** |
| D2 | UI | **Jetpack Compose.** Charts: custom `Canvas` for the night timeline, [Vico](https://github.com/patrykandpatrick/vico) for trend charts |
| D3 | Garmin path (v1) | **Health Connect** (`androidx.health.connect:connect-client`). Garmin Connect writes into it. No API approval needed. |
| D4 | Distribution | Sideload via ADB / internal testing track. Not a public Play listing in v1 — a continuously-recording mic app faces heavy policy review. |
| D5 | ML runtime | **MediaPipe `AudioClassifier` in STREAM mode** wrapping YAMNet, or raw LiteRT with the published YAMNet `.tflite`. MediaPipe is the faster path; drop to raw LiteRT when the custom head is added in Phase 9. |
| D6 | Persistence | **Room** (SQLite). Clips as encrypted files in `context.filesDir`. |

### 2.1 Phase 0 verification tasks — do these before writing detection code
1. **Confirm what Garmin Connect actually writes to Health Connect on the user's specific watch.** Install Health Connect, enable Garmin sync, sleep one night, then dump every available record type and its sample cadence. Do not assume. Specifically check whether `SleepSessionRecord` includes **stage sub-records** (light/deep/REM) or only a session envelope, and check the real time resolution of `HeartRateRecord` samples. The whole enrichment design in §5 depends on this.
2. **Confirm the phone supports `MediaRecorder.AudioSource.UNPROCESSED`** — see §4.1. This materially affects detection quality.
3. **Confirm the phone's OEM battery-killer behaviour** — see §4.6. On Samsung/Xiaomi/OnePlus this can silently end the night.

---

## 3. System architecture

```
┌───────────────────────────────────────────────────────────────┐
│  Android phone (bedside, plugged in, screen off)              │
│                                                               │
│  RecordingService (foreground, type=microphone, wakelock)     │
│                                                               │
│  AudioRecord (16 kHz mono, UNPROCESSED) ──► reader thread     │
│           │                                                   │
│           ├──► Ring buffer (30 s rolling, in-memory)          │
│           │                                                   │
│           └──► Frame windower (0.96 s, 50 % overlap)          │
│                    │                                          │
│                    ├──► Gate 1: energy / noise-floor          │
│                    ├──► Gate 2: YAMNet class + embedding      │
│                    ├──► Gate 3: grinding head (custom)        │
│                    └──► EpisodeAssembler (temporal)           │
│                              │                                │
│                              ├──► ClipStore (Keystore AES-GCM)│
│                              └──► Room: Episode row           │
│                                                               │
│  WorkManager (morning) ──► HealthConnectClient                │
│                             HR, HRV, sleep stages, resp rate  │
│                                    │                          │
│                                    └──► EnrichmentWorker      │
│                                                               │
│  WorkManager (daily) ──► RetentionWorker (purge expired clips)│
│                                                               │
│  Compose UI ◄── Room ◄── CorrelationEngine                    │
└───────────────────────────────────────────────────────────────┘
```

### 3.1 Modules to build

| Module | Responsibility |
|---|---|
| `RecordingService` | Foreground service, wakelock, notification, lifecycle, interruption recovery |
| `AudioCapture` | `AudioRecord` setup, reader thread, ring buffer, source negotiation |
| `FeatureExtractor` | Framing, mel-spectrogram, RMS/ZCR/spectral-flatness/centroid |
| `Classifier` | MediaPipe/LiteRT inference → per-frame grinding score + rejected classes |
| `EpisodeAssembler` | Frame scores → discrete episodes, hysteresis, merge, duration filtering |
| `ClipStore` | Keystore-backed AES-GCM clip write, retention, deletion |
| `HealthConnectRepo` | Permission flow, record reads, mapping to internal types |
| `EnrichmentWorker` | Joins episodes to HR/sleep after morning sync; computes night metrics |
| `CorrelationEngine` | Rolling correlation of nightly index vs. logged factors |
| `ReportUi` | Timeline, episode list, clip playback, labeling |
| `LabelRepo` | User confirm/reject labels; export for retraining |
| `RetentionWorker` | Scheduled purge, key management |
| `DebugMetricsScreen` | Precision/recall/FP-per-hour against the gold set (§10) |

---

## 4. Audio pipeline (core detection)

### 4.1 Capture — Android specifics

```kotlin
AudioRecord(
    audioSource = MediaRecorder.AudioSource.UNPROCESSED,  // see below
    sampleRateInHz = 16_000,
    channelConfig = AudioFormat.CHANNEL_IN_MONO,
    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
    bufferSizeInBytes = minBufferSize * 4
)
```

**Audio source selection is critical and easy to get wrong.**
- Prefer `UNPROCESSED`. Gate on `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"`.
- Fall back to `MIC`.
- **Never use `VOICE_RECOGNITION` or `VOICE_COMMUNICATION`.** These apply automatic gain control, noise suppression, and echo cancellation tuned for speech. Noise suppression specifically attacks broadband non-speech signal — which is exactly what tooth grinding is. It will destroy the feature you are trying to detect.
- Explicitly disable `NoiseSuppressor` and `AutomaticGainControl` effects if the platform attached them (`NoiseSuppressor.isAvailable()` → create and `setEnabled(false)`, or verify none are attached).
- Log which source was actually granted into the `Session` row, since detection thresholds are not comparable across sources.

Do not record at 44.1 kHz — 8 kHz Nyquist covers all grinding energy and quarters the compute.

### 4.2 Foreground service and permissions

Manifest:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>

<service
    android:name=".RecordingService"
    android:foregroundServiceType="microphone"
    android:exported="false"/>
```

- On **API 34+**, a `microphone`-type foreground service **cannot be started from the background**. Start it from a visible activity — i.e. the user taps "Start night". This is fine for this app; design the flow around it and do not attempt scheduled auto-start.
- Hold a `PARTIAL_WAKE_LOCK` for the session duration. Release deterministically in `onDestroy` and on error paths.
- The persistent notification should show elapsed time and live episode count — it doubles as a "is it still running?" check.
- Android 12+ shows a **green microphone privacy indicator** all night. Expected; mention it in onboarding so it isn't alarming.

### 4.3 Interruption and recovery
Phone calls, Assistant activation, alarms, and other apps can preempt or share the mic. Monitor `AudioRecord.getRecordingState()` and `AudioManager.AudioRecordingCallback`. On loss, retry with backoff and **write a `gap` record**, so the night's coverage percentage is honest rather than silently wrong.

### 4.4 Ring buffer
Continuous 30-second in-memory ring buffer, so when an episode is detected the **10 s preceding it is still available** and the clip can include the onset. At 16 kHz/16-bit mono this is ~960 KB — trivial.

### 4.5 Calibration night
The first session runs in **calibration mode**: no detection, just log the room's noise floor per third-octave band across the night, plus the fan/HVAC/traffic signature. Store as `RoomProfile`. All subsequent thresholds are relative to this profile, not absolute dB. Re-run on user request (new room, travel).

### 4.6 Battery, Doze, and OEM killers — the biggest Android-specific risk

A foreground service survives Doze. It does **not** reliably survive aggressive OEM power management. Samsung ("Put unused apps to sleep", "Adaptive battery"), Xiaomi/MIUI (autostart permission), OnePlus/OPPO/realme, Huawei, and Vivo all kill long-running services on their own schedules. A silently-killed service means a night with no data and no error — the worst failure mode for this app.

**Required mitigations:**
1. Onboarding flow that walks the user through disabling battery optimisation for JawTrack (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and the OEM-specific setting for their manufacturer (`Build.MANUFACTURER` switch, with per-OEM instructions).
2. **Heartbeat watchdog:** the service writes a timestamp to Room every 60 s. On app launch, if the last session ended without a clean stop and the heartbeat trailed off early, show "last night's recording stopped at 02:14 — this is usually battery optimisation" with a link back to the settings flow.
3. Require charging. If the device unplugs and battery falls below 30 %, stop cleanly and notify rather than dying mid-night.
4. Optional foreground `AlarmManager` `setExactAndAllowWhileIdle` re-check every 30 min as a belt-and-braces liveness probe.

### 4.7 Three-gate cascade
Designed so the expensive stage runs rarely.

**Gate 1 — Energy gate (always, ~free)**
- Frame RMS must exceed `noiseFloor + 8 dB` for at least 200 ms.
- 95 %+ of night frames are silence; this skips everything downstream. Keep this in pure Kotlin/float math — no allocation in the hot loop.

**Gate 2 — YAMNet (on Gate-1 passes)**
- YAMNet via MediaPipe `AudioClassifier` (STREAM mode) or LiteRT directly, 0.96 s / 15,600-sample input at 16 kHz.
- Use the **XNNPACK** delegate. Test NNAPI but do not assume it helps — for a model this small it often adds overhead.
- Two uses:
  - **Rejection:** if top classes are `Speech`, `Snoring`, `Breathing`, `Cough`, `Music`, `Television`, `Dog`, `Vehicle` above threshold → reject. For `Speech`, **discard the buffer immediately without writing anything to disk** (§6).
  - **Feature source:** take the 1024-d embedding as input to Gate 3. (MediaPipe's classifier task exposes categories only — if you need embeddings, use `AudioEmbedder` or raw LiteRT and read the penultimate tensor.)

**Gate 3 — Grinding head (on Gate-2 survivors)**
- Small dense classifier (1024 → 128 → 1) on the YAMNet embedding.
- **v1 ships with a heuristic stand-in, not a trained model**, because there is no training data on day one:
  - Spectral flatness in 1–6 kHz above threshold (grinding is broadband/noisy, not tonal)
  - Sustained energy 0.4–5 s
  - Absence of the ~3–6 s respiratory periodicity that characterises snoring
  - Spectral centroid in 1.5–4 kHz
- After ~200 labeled clips (~2–3 weeks), train the dense head on the labeled set and swap it in. Keep the heuristic as fallback and cross-check.

### 4.8 Episode assembly
- Hysteresis thresholding: enter at score > 0.7, exit at < 0.4.
- Merge events separated by < 3 s.
- Discard episodes < 300 ms (a single click is not a grinding bout) or > 30 s (that is noise, not RMMA).
- Record: `onset`, `offset`, `peakScore`, `meanScore`, `peakDb`, `dominantBandHz`, `rejectedClasses`.

### 4.9 Known false-positive sources — handle explicitly
Snoring, coughing, sleep-talking, blanket and mattress rustle, partner movement, HVAC cycling, phone vibration against a hard surface, rain, plumbing, pets. **Snoring is the most important**: loud, frequent, and superficially broadband. Separable by respiratory periodicity and lower spectral centroid — build that test explicitly and unit-test it.

---

## 5. Garmin integration

### 5.1 v1 — Health Connect (build this)

`androidx.health.connect:connect-client`. Garmin Connect writes into Health Connect on sync.

Record types to read:
- `HeartRateRecord`
- `HeartRateVariabilityRmssdRecord`
- `SleepSessionRecord` (including `stages` if Garmin populates them — **verify in Phase 0**)
- `RespiratoryRateRecord`

Notes:
- Health Connect is built into **Android 14+**; on Android 10–13 it is a separate Play Store app the user must install. Handle `HealthConnectClient.getSdkStatus()` → prompt to install / update.
- Permissions are declared in the manifest and requested via `PermissionController.createRequestPermissionResultContract()`. Read-only scopes.
- A privacy-policy rationale activity (`ACTION_SHOW_PERMISSIONS_RATIONALE`) is required or permission requests are rejected.

**Critical honest constraint the agent must design around:** overnight HR from Garmin is typically stored at coarse resolution (roughly one sample per 1–2 minutes), not beat-to-beat. **This is too coarse to confirm a 5-second arousal.** Therefore in v1:
- HR is used at **night level and episode-cluster level** ("episodes clustered in your highest-HR window"), not per-episode.
- Sleep-stage joining works fine — stage intervals are minutes long.
- Do not build UI implying per-episode beat-level precision. Do not claim it.

### 5.2 v2 — Connect IQ companion (design for, do not build yet)
For real per-episode fusion:
- A small Connect IQ (Monkey C) watch app streaming 1 Hz HR — and RR intervals where exposed — to the phone via the **Connect IQ Mobile SDK for Android**.
- Watch accelerometer as a bonus channel: body movement co-occurring with an audio event is a useful arousal proxy.
- This unlocks **[Cool Feature] real-time biofeedback**: on confirmed episode, push a short gentle vibration to the watch. Contingent stimulation is the mechanism behind existing biofeedback approaches. Ship it **off by default**, with configurable intensity, a nightly cap on buzz count, and a mandatory warning that it may fragment sleep. Track index with the feature on vs. off — the app can A/B test whether it helps this specific user.

### 5.3 Enrichment
`EnrichmentWorker` (WorkManager, one-time, enqueued on app foreground in the morning):
1. Read Health Connect over last night's session window.
2. Join each episode to its sleep stage.
3. Compute night-level metrics (§7.1).
4. Mark session `enriched`. If the watch hasn't synced, show the report in `partial` state with a "waiting on Garmin sync" banner and re-enqueue with backoff.

---

## 6. Privacy model — hard requirements

This is a microphone in a bedroom. These are enforced in code, not just policy text.

1. **Nothing leaves the device.** No network calls carrying audio, features, embeddings, or labels. v1 ships with no `INTERNET` permission at all — this makes the guarantee structural rather than aspirational.
2. **No continuous recording is ever persisted.** The full-night stream exists only in the in-memory ring buffer. Only event clips reach disk.
3. **Clip length capped** at 12 s (10 s pre-onset + episode start), not the full episode if it runs long.
4. **Speech is destroyed, not stored.** If YAMNet flags `Speech` above threshold in a candidate window, the buffer segment is dropped immediately and no clip is written. Log only a counter (`speechRejectedCount`).
5. **Encryption at rest.** AES-256-GCM with a key generated in the **Android Keystore** (`setUserAuthenticationRequired(false)`, `StrongBox` where available). Use Tink or a thin Keystore wrapper — note `androidx.security:security-crypto` is in maintenance mode; do not build new work on it.
6. Clips in `context.filesDir` (app-private internal storage). Never external storage, never `MediaStore`, never a directory that a backup or gallery scanner can reach. Set `android:allowBackup="false"` and exclude clips from auto-backup.
7. **Auto-retention:** default 14 days, user-configurable 3–90. `RetentionWorker` runs daily and on launch. A "delete all audio" button that actually deletes, immediately, no undo.
8. **Bed-partner consent notice** on first run. If someone else sleeps in the room, they are being recorded. Say so plainly.
9. **Exports contain no raw audio** unless explicitly opted in per-export.

---

## 7. Data model (Room)

```sql
Session(id, startedAt, endedAt, state, roomProfileId,
        audioSourceUsed, coveragePct, gapSeconds,
        lastHeartbeatAt, cleanShutdown, calibrationOnly)

Episode(id, sessionId, onsetAt, offsetAt, durationMs,
        peakScore, meanScore, peakDb, dominantBandHz,
        classifierVersion, sleepStage, hrContext,
        userLabel)          -- null | confirmed | rejected | unsure

AudioClip(id, episodeId, path, durationMs, sampleRate,
          keyAlias, expiresAt)

HeartRateSample(sessionId, t, bpm, source)
HrvSample(sessionId, t, rmssd)
SleepStage(sessionId, startAt, endAt, stage)

NightMetrics(sessionId, episodeCount, episodesPerHour,
             totalGrindingSeconds, longestEpisodeMs,
             stageDistributionJson, snoreIndex,
             confidenceGrade)

DailyFactors(date, caffeineMg, caffeineLastTime, alcoholUnits,
             stress1to5, exerciseMinutes, lastMealTime,
             screenTimeBeforeBedMin, nightGuardWorn,
             nightGuardId, notes)

MorningCheckin(date, jawSoreness1to5, headache, sleepQuality1to5,
               toothSensitivity1to5)

Label(id, episodeId, label, labeledAt, classifierVersion)
RoomProfile(id, createdAt, bandNoiseFloorJson)
Gap(sessionId, startAt, endAt, reason)
```

### 7.1 Night-level metrics
- **Episode count** and **episodes/hour of sleep** — the *JawTrack Index*. Do not label it with clinical severity terms.
- Total grinding seconds, longest episode.
- Distribution across sleep stages.
- **Confidence grade (A/B/C)** from: coverage %, room noise floor, speech-rejection count, proportion of episodes labeled, and whether the service ran cleanly. A noisy night with 60 % coverage must not present its number with the same authority as a clean one. **This is important and often skipped — build it.**
- **Snore index** — falls out of the same pipeline for free, since YAMNet already classifies snoring. Report neutrally. No apnea inference.

---

## 8. Morning report — UX spec

**Screen 1 — Last night**
- Big number: JawTrack Index (episodes/hr) + confidence grade badge.
- Delta vs. 7-night rolling average.
- Plain-language summary: "9 episodes, mostly between 2:10 and 3:40, clustered in light sleep."
- If the service died early, this screen leads with that, not with a misleadingly low number.

**Screen 2 — Timeline**
- Compose `Canvas`, horizontal, full night. Layers:
  - Sleep stages as a colored band (Health Connect)
  - Heart rate line
  - Episode markers as vertical ticks, height = peak score
  - Grey hatching over gaps — **always show what you missed**
- Tap a tick → episode detail. Pinch to zoom.

**Screen 3 — Episode detail + labeling**
- Waveform + spectrogram of the clip. (Spectrogram matters: grinding shows a broadband smear vs. snoring's harmonic stack. It makes labeling fast and the app feel credible.)
- Play button (`AudioTrack` from decrypted buffer — decrypt to memory, never to a temp file).
- Three big buttons: **Grinding / Not grinding / Not sure**.
- Swipe to next unlabeled episode — ten clips should take under a minute. This loop is what makes the personalised model possible; optimise it hard.

**Screen 4 — Morning check-in**
- Jaw soreness, headache, tooth sensitivity, sleep quality. Four taps.
- **[Cool Feature]** This is the app's real ground truth. If detected index rises and jaw soreness rises with it over weeks, the detector is measuring something real. Show that correlation explicitly — it validates the app in a way no accuracy claim can.

**Screen 5 — Trends & insights**
- Index over time, 7/30/90 day (Vico).
- **[Cool Feature] Correlation engine:** after ≥ 21 nights with logged factors, run rank correlation / regularised regression of nightly index against each factor. Surface only correlations passing a significance threshold, phrased as observations not causation: "Nights after ≥ 2 alcohol units: index averaged 6.1 vs. 3.4 otherwise (14 nights)." Always show n. Never surface a spurious insight from 4 data points.
- **Night guard A/B:** index with guard vs. without. A guard protects enamel but does not necessarily reduce grinding — the app can show which is happening here.

**Screen 6 — Dentist export**
- One-tap PDF (`PdfDocument`, or render Compose to `Canvas` → PDF): index trend, stage distribution, episode duration histogram, sample spectrograms, symptom trend, methodology note, and a clear "self-tracking data, not a diagnostic assessment" statement.
- Share via `FileProvider`.
- **[Cool Feature]** Highest-value output of the whole app. Most people describe bruxism to a dentist from vague memory. Design this screen properly.

---

## 9. Build phases

| Phase | Deliverable | Done when |
|---|---|---|
| **0** | D1–D6 locked; §2.1 verifications done; repo, CI, test harness | Health Connect dump confirms available record types and cadence |
| **1** | Foreground service + `AudioRecord` + ring buffer + OEM battery flow | Records 8 h with < 2 % gap on the actual phone, survives two consecutive nights without OEM kill |
| **2** | Calibration night + room profile | Produces a per-band noise floor from a real night |
| **3** | Gates 1–2 + heuristic Gate 3 + episode assembly | > 80 % recall on a synthetic set of injected grinding clips |
| **4** | ClipStore + Keystore encryption + retention + speech destruction | Tests prove no clip persists past `expiresAt`, no speech-flagged buffer is written, `INTERNET` permission absent |
| **5** | Health Connect sync + enrichment | Episodes correctly joined to sleep stages for a real night |
| **6** | Report screens 1–3 + labeling loop | A full night's episodes labelable in < 3 min |
| **7** | Check-in, trends, correlation engine | Insights only fire at n ≥ 21 and pass significance |
| **8** | Dentist PDF export | Renders a real 30-night report |
| **9** | Trained classifier head swapped in | Beats heuristic on held-out labeled set; both versions logged per episode |
| **10** | *(optional)* Connect IQ companion + biofeedback | 1 Hz HR streaming; buzz on episode, off by default |

**Phase 1 is the real risk phase.** Do not proceed to detection work until the service has demonstrably survived multiple full nights on this specific phone. Everything downstream is worthless if the recording stops at 2 a.m.

---

## 10. Validation plan

The agent must build this, not assume the detector works.

1. **Gold nights:** three nights where the full audio *is* retained (behind an explicit consent flow), hand-labeled by the user. This is the ground-truth set.
2. **Debug metrics screen:** precision, recall, F1 against the gold set, plus **false positives per hour**. FP/hour determines whether the app is livable — 20 false positives a night makes the labeling loop unusable.
3. **Synthetic injection tests:** mix known grinding recordings into recorded room noise at varying SNR; assert recall degrades gracefully rather than cliff-edging.
4. **Negative test suite:** a night of snoring, a night with a TV on, a night with sleep-talking. Assert near-zero episode output.
5. **Cross-check:** correlation between nightly index and self-reported jaw soreness over 30 nights. Weak but independent evidence the thing is real.

---

## 11. Risks and limitations to state in-app

- Clenching is silent and will be under-counted. Say so on the trends screen.
- Phone position matters enormously. Fix the position; log if it moves; consider a mount.
- A bed partner's grinding is indistinguishable from the user's on a single mic. Acknowledge it.
- OEM battery management can end a session silently. The watchdog (§4.6) exists so this is visible, not invisible.
- Absolute episode counts are not comparable to polysomnography numbers. **Trends within JawTrack are meaningful; the absolute value is not clinically calibrated.** This framing keeps the app honest and keeps it a wellness tool rather than a medical device.
- Audio-source processing differences mean thresholds are not portable across devices. Recalibrate on any hardware change.

---

## 12. Instructions to the implementing agent

- Work phase by phase. Do not start Phase 3 before Phase 1 has survived multiple real nights on the target device.
- No allocation in the audio hot loop. Preallocate buffers; run the reader thread at `THREAD_PRIORITY_URGENT_AUDIO`.
- Every module gets unit tests; the privacy rules in §6 get tests that fail loudly.
- Log `classifierVersion` and `audioSourceUsed` on every episode so old data stays interpretable after model or hardware changes.
- **Do not add the `INTERNET` permission.** If a dependency requires it, flag it rather than adding it.
- Where a design choice is ambiguous, prefer the option producing fewer, higher-confidence detections. A monitor that cries wolf gets uninstalled.
- Produce a short methodology document at the end of Phase 9 covering signal chain, thresholds, and measured performance.
