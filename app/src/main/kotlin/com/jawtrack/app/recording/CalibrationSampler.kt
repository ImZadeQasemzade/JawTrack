package com.jawtrack.app.recording

import com.jawtrack.corelogic.audio.AudioRingBuffer
import com.jawtrack.corelogic.calibration.NoiseFloorTracker
import com.jawtrack.corelogic.dsp.BandEnergyAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives calibration-night noise floor measurement (JawTrackSpec §4.5) by periodically
 * pulling the most recent frame out of the ring buffer that Phase 1 already maintains,
 * rather than hooking into the audio reader's hot loop — FFT analysis is too heavy to run
 * on the urgent-priority reader thread, and a night's worth of 2 s samples is already far
 * more than enough for a stable per-band floor estimate.
 */
class CalibrationSampler(
    private val ringBuffer: AudioRingBuffer,
    sampleRateHz: Int = AudioConfig.SAMPLE_RATE_HZ,
    private val frameSize: Int = FRAME_SIZE,
    private val samplingIntervalMillis: Long = 2_000
) {
    private val analyzer = BandEnergyAnalyzer(sampleRateHz, frameSize)
    private val tracker = NoiseFloorTracker()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        check(job == null) { "CalibrationSampler already started" }
        job = scope.launch {
            while (isActive) {
                delay(samplingIntervalMillis)
                val snapshot = ringBuffer.snapshotLast(frameSize)
                if (snapshot.size == frameSize) {
                    tracker.addSample(analyzer.analyze(BandEnergyAnalyzer.normalize(snapshot)))
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Null if the session ended before a single full frame was ever sampled. */
    fun finalizeProfile(percentile: Double = NoiseFloorTracker.DEFAULT_FLOOR_PERCENTILE): Map<Double, Double>? {
        val floors = tracker.computeFloors(percentile)
        return floors.ifEmpty { null }
    }

    companion object {
        const val FRAME_SIZE = 1024 // 64ms at 16kHz
    }
}
