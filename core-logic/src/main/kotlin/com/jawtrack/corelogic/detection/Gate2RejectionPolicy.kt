package com.jawtrack.corelogic.detection

/** One classifier output category and its confidence, e.g. from a YAMNet inference. */
data class Gate2Classification(val label: String, val score: Float)

enum class Gate2Verdict {
    /** Neither a reject class nor Speech was seen above threshold — proceed to Gate 3. */
    PASS,

    /** A non-speech reject class (Snoring, Cough, ...) was seen — drop this candidate. */
    REJECT,

    /** Speech was seen — the buffer must be destroyed immediately, not just dropped (§6.4). */
    MUST_DESTROY
}

/**
 * Pure decision logic for Gate 2's rejection step (JawTrackSpec §4.7): given whatever a
 * classifier (YAMNet or otherwise) reported for a window, decide whether to continue to
 * Gate 3, silently drop the candidate, or treat it as speech that must never reach disk.
 * Deliberately decoupled from any specific classifier implementation so it's testable without
 * a real model.
 */
object Gate2RejectionPolicy {

    val REJECT_LABELS: Set<String> = setOf(
        "Speech", "Snoring", "Breathing", "Cough", "Music", "Television", "Dog", "Vehicle"
    )

    const val DEFAULT_THRESHOLD = 0.5f

    fun evaluate(classifications: List<Gate2Classification>, threshold: Float = DEFAULT_THRESHOLD): Gate2Verdict {
        val aboveThreshold = classifications.filter { it.score >= threshold }.map { it.label }

        if ("Speech" in aboveThreshold) return Gate2Verdict.MUST_DESTROY
        if (aboveThreshold.any { it in REJECT_LABELS }) return Gate2Verdict.REJECT
        return Gate2Verdict.PASS
    }
}
