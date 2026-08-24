package com.jawtrack.app.ml

import com.jawtrack.corelogic.detection.Gate2Classification

/** One classification pass over a Gate-1-surviving window (JawTrackSpec §4.7). */
data class Gate2Result(
    val classifications: List<Gate2Classification>,
    /**
     * The 1024-d embedding Gate 3's trained head will eventually consume (Phase 9). Null in
     * v1 — MediaPipe's `AudioClassifier` task exposes categories only; getting the embedding
     * would mean switching to `AudioEmbedder` or reading the penultimate tensor from raw
     * LiteRT (§4.7's own note). Not needed yet since Gate 3 is still the DSP heuristic, which
     * doesn't consume it.
     */
    val embedding: FloatArray?
)

/**
 * Seam between the detection pipeline and whatever ML runtime backs Gate 2. Kept as an
 * interface (rather than the pipeline depending on MediaPipe directly) so [Gate2RejectionPolicy]
 * and the pipeline wiring stay testable without a real model, and so a missing/broken model
 * asset degrades to "Gate 2 skipped" rather than taking the whole recording service down —
 * see [com.jawtrack.app.detection.FrameWindower].
 */
interface Gate2Classifier {
    /** @param pcmMono16kHz normalized [-1, 1] mono samples at 16 kHz, one classifier window long. */
    fun classify(pcmMono16kHz: FloatArray): Gate2Result

    /** Releases the underlying model/interpreter. Safe to call more than once. */
    fun close()
}
