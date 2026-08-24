package com.jawtrack.app.detection

import android.util.Log
import com.jawtrack.app.clips.ClipStore
import com.jawtrack.app.ml.Gate2Classifier
import com.jawtrack.app.ml.Gate2Result
import com.jawtrack.app.recording.AudioConfig
import com.jawtrack.corelogic.audio.AudioRingBuffer
import com.jawtrack.corelogic.detection.EnergyGate
import com.jawtrack.corelogic.detection.EpisodeAssembler
import com.jawtrack.corelogic.detection.EpisodeCandidate
import com.jawtrack.corelogic.detection.FrameResult
import com.jawtrack.corelogic.detection.Gate2RejectionPolicy
import com.jawtrack.corelogic.detection.Gate2Verdict
import com.jawtrack.corelogic.detection.Gate3Input
import com.jawtrack.corelogic.detection.GrindingHeuristicScorer
import com.jawtrack.corelogic.dsp.BandEnergyAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Drives the three-gate cascade (JawTrackSpec §4.7) and episode assembly (§4.8) for one
 * recording session. Like [com.jawtrack.app.recording.CalibrationSampler], this polls the
 * ring buffer on its own coroutine rather than hooking the audio reader's hot loop — Gate 2/3
 * are far too heavy to run at urgent-audio thread priority.
 *
 * Gate 1 runs every tick on a short frame (cheap, matches §4.7's "always, ~free"). Gate 2/3
 * only run once Gate 1 has sustained past threshold, on a separate, larger window sized to
 * match YAMNet's expected input.
 *
 * @param gate2Classifier null runs the pipeline with Gate 2 skipped entirely (Gate 1 survivors
 *   go straight to Gate 3) — the deliberate degrade-gracefully path when no model is available;
 *   see [com.jawtrack.app.ml.YamnetGate2Classifier]'s unverified-integration warning.
 */
class FrameWindower(
    private val ringBuffer: AudioRingBuffer,
    private val energyGate: EnergyGate,
    private val gate2Classifier: Gate2Classifier?,
    private val onEpisode: (EpisodeCandidate) -> Unit,
    private val pollIntervalMillis: Long = SMALL_FRAME_POLL_INTERVAL_MILLIS
) {
    private val bandAnalyzer = BandEnergyAnalyzer(AudioConfig.SAMPLE_RATE_HZ, GATE3_FRAME_SAMPLES)
    private val episodeAssembler = EpisodeAssembler()
    private val energyEnvelope = ArrayDeque<Double>()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        check(job == null) { "FrameWindower already started" }
        job = scope.launch {
            while (isActive) {
                delay(pollIntervalMillis)
                tick()
            }
        }
    }

    /** Stops polling and flushes any in-progress or pending episode (§4.8). */
    fun stop() {
        job?.cancel()
        job = null
        episodeAssembler.flush().forEach(onEpisode)
    }

    private fun tick() {
        val now = System.currentTimeMillis()

        val smallFrame = ringBuffer.snapshotLast(SMALL_FRAME_SAMPLES)
        if (smallFrame.size < SMALL_FRAME_SAMPLES) return

        val rmsDb = rmsDb(smallFrame)
        energyEnvelope.addLast(rmsDb)
        while (energyEnvelope.size > ENVELOPE_CAPACITY) energyEnvelope.removeFirst()

        if (!energyGate.evaluate(now, rmsDb)) return // Gate 1 not sustained yet

        val classifierWindow = ringBuffer.snapshotLast(CLASSIFIER_WINDOW_SAMPLES)
        if (classifierWindow.size < CLASSIFIER_WINDOW_SAMPLES) return

        var rejectedClasses: List<String> = emptyList()
        if (gate2Classifier != null) {
            val gate2Result = runGate2(classifierWindow)
            if (gate2Result != null) {
                rejectedClasses = gate2Result.classifications.map { it.label }
                when (Gate2RejectionPolicy.evaluate(gate2Result.classifications)) {
                    Gate2Verdict.MUST_DESTROY -> return // speech: buffer touched nothing, not even a scored frame (§6.4)
                    Gate2Verdict.REJECT -> {
                        episodeAssembler.process(FrameResult(now, 0.0, rmsDb, rejectedClasses = rejectedClasses))
                            .forEach(onEpisode)
                        return
                    }
                    Gate2Verdict.PASS -> Unit
                }
            }
            // gate2Result == null means classification failed (see runGate2) -- fall through to Gate 3 unfiltered
        }

        // most recent slice of the window, not the oldest -- Gate 3 should look at what's
        // happening right now, not what happened ~0.9s ago at the start of the classifier window
        val gate3Frame = classifierWindow.copyOfRange(classifierWindow.size - GATE3_FRAME_SAMPLES, classifierWindow.size)
        val bandDb = bandAnalyzer.analyze(BandEnergyAnalyzer.normalize(gate3Frame))
        val score = GrindingHeuristicScorer.score(
            Gate3Input(
                bandDb = bandDb,
                energyEnvelope = energyEnvelope.toDoubleArray(),
                energyEnvelopeSampleIntervalMillis = pollIntervalMillis,
                sustainedDurationMillis = energyGate.sustainedMillisAsOf(now)
            )
        )
        val dominantBandHz = bandDb.filterKeys { it in GRINDING_BAND_RANGE_HZ }.maxByOrNull { it.value }?.key

        // Snapshotting the ring buffer here (only invoked by EpisodeAssembler if this frame is
        // the episode's true onset) gives up to 12s trailing right up to this instant -- the
        // "10s pre-onset + episode start" clip §6.3 wants, captured before it can roll out of
        // the 30s ring buffer even if this episode ends up running long.
        episodeAssembler.process(FrameResult(now, score, rmsDb, dominantBandHz, rejectedClasses)) {
            ringBuffer.snapshotLast(CLIP_CAPTURE_SAMPLES)
        }.forEach(onEpisode)
    }

    private fun runGate2(window: ShortArray): Gate2Result? = try {
        gate2Classifier?.classify(FloatArray(window.size) { window[it] / 32_768f })
    } catch (e: Exception) {
        Log.w(TAG, "Gate 2 classification failed for this window; treating as unfiltered", e)
        null
    }

    private fun rmsDb(samples: ShortArray): Double {
        var sumSquares = 0.0
        for (s in samples) {
            val normalized = s / 32_768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / samples.size)
        return 20.0 * log10(rms.coerceAtLeast(MIN_RMS_FOR_DB))
    }

    companion object {
        private const val TAG = "FrameWindower"
        private val GRINDING_BAND_RANGE_HZ = 1_000.0..6_000.0
        private const val MIN_RMS_FOR_DB = 1e-9

        const val SMALL_FRAME_POLL_INTERVAL_MILLIS = 200L
        val SMALL_FRAME_SAMPLES = AudioConfig.SAMPLE_RATE_HZ / 5 // ~200ms, for Gate 1's RMS
        const val CLASSIFIER_WINDOW_SAMPLES = 15_600 // 0.96s @ 16kHz -- YAMNet's fixed input (§4.7)
        const val GATE3_FRAME_SAMPLES = 1_024 // power-of-two, for BandEnergyAnalyzer's FFT
        val CLIP_CAPTURE_SAMPLES = ClipStore.DEFAULT_MAX_CLIP_SAMPLES // same 12s cap ClipStore itself enforces (§6.3)
        const val ENVELOPE_CAPACITY = 30 // 6s of history at 200ms cadence, matching the periodicity search window
    }
}
