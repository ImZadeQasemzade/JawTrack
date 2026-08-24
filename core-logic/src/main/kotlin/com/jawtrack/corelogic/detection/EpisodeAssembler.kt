package com.jawtrack.corelogic.detection

/** One frame's worth of gate output, in increasing timestamp order. */
data class FrameResult(
    val timestampMillis: Long,
    /** Gate 3 confidence, 0..1 (heuristic today, a trained head later — same shape either way). */
    val score: Double,
    val db: Double,
    val dominantBandHz: Double? = null,
    val rejectedClasses: List<String> = emptyList()
)

/** A finished, filtered episode ready to become an `Episode` row (JawTrackSpec §7, §4.8). */
data class EpisodeCandidate(
    val onsetMillis: Long,
    val offsetMillis: Long,
    val peakScore: Double,
    val meanScore: Double,
    val peakDb: Double,
    val dominantBandHz: Double?,
    val rejectedClasses: List<String>
)

/**
 * Turns a stream of per-frame Gate 3 scores into discrete episodes (§4.8):
 * - Hysteresis: a run enters at score > [enterThreshold], stays open down to [exitThreshold],
 *   and only ends once score drops below that.
 * - Two runs separated by less than [mergeGapMillis] are merged into one episode.
 * - Episodes shorter than [minDurationMillis] (a single click) or longer than
 *   [maxDurationMillis] (noise, not a grinding bout) are discarded.
 *
 * Streaming and stateful: call [process] with frames in timestamp order, then [flush] once at
 * the end of the night to finalize whatever was still open or pending a possible merge.
 */
class EpisodeAssembler(
    private val enterThreshold: Double = DEFAULT_ENTER_THRESHOLD,
    private val exitThreshold: Double = DEFAULT_EXIT_THRESHOLD,
    private val mergeGapMillis: Long = DEFAULT_MERGE_GAP_MILLIS,
    private val minDurationMillis: Long = DEFAULT_MIN_DURATION_MILLIS,
    private val maxDurationMillis: Long = DEFAULT_MAX_DURATION_MILLIS
) {
    private var active: RunBuilder? = null
    private var pending: RawEpisode? = null

    fun process(frame: FrameResult): List<EpisodeCandidate> {
        val output = mutableListOf<EpisodeCandidate>()

        // A pending episode that nothing merged into within the gap window is done waiting.
        val heldPending = pending
        if (active == null && heldPending != null && frame.timestampMillis - heldPending.offsetMillis >= mergeGapMillis) {
            heldPending.toCandidateOrNull(minDurationMillis, maxDurationMillis)?.let(output::add)
            pending = null
        }

        val currentActive = active
        when {
            currentActive == null && frame.score > enterThreshold -> {
                active = RunBuilder(frame.timestampMillis).apply { absorb(frame) }
            }
            currentActive != null && frame.score >= exitThreshold -> {
                currentActive.absorb(frame)
            }
            currentActive != null -> {
                active = null
                output.addAll(closeRun(currentActive.build()))
            }
        }

        return output
    }

    /** Finalizes any in-progress or pending episode. Call once at the end of the session. */
    fun flush(): List<EpisodeCandidate> {
        val output = mutableListOf<EpisodeCandidate>()

        active?.let { output.addAll(closeRun(it.build())) }
        active = null

        pending?.toCandidateOrNull(minDurationMillis, maxDurationMillis)?.let(output::add)
        pending = null

        return output
    }

    /** A hysteresis run just ended; merge it into the pending episode or emit the old one and start a new pending. */
    private fun closeRun(finished: RawEpisode): List<EpisodeCandidate> {
        val output = mutableListOf<EpisodeCandidate>()
        val heldPending = pending

        if (heldPending != null && finished.onsetMillis - heldPending.offsetMillis < mergeGapMillis) {
            pending = heldPending.mergedWith(finished)
        } else {
            heldPending?.toCandidateOrNull(minDurationMillis, maxDurationMillis)?.let(output::add)
            pending = finished
        }
        return output
    }

    private class RunBuilder(private val onsetMillis: Long) {
        private var offsetMillis = onsetMillis
        private var peakScore = Double.NEGATIVE_INFINITY
        private var sumScore = 0.0
        private var frameCount = 0
        private var peakDb = Double.NEGATIVE_INFINITY
        private val dominantBandCounts = mutableMapOf<Double, Int>()
        private val rejectedClasses = mutableSetOf<String>()

        fun absorb(frame: FrameResult) {
            offsetMillis = frame.timestampMillis
            peakScore = maxOf(peakScore, frame.score)
            sumScore += frame.score
            frameCount++
            peakDb = maxOf(peakDb, frame.db)
            frame.dominantBandHz?.let { hz -> dominantBandCounts[hz] = (dominantBandCounts[hz] ?: 0) + 1 }
            rejectedClasses.addAll(frame.rejectedClasses)
        }

        fun build(): RawEpisode = RawEpisode(
            onsetMillis = onsetMillis,
            offsetMillis = offsetMillis,
            peakScore = peakScore,
            sumScore = sumScore,
            frameCount = frameCount,
            peakDb = peakDb,
            dominantBandCounts = dominantBandCounts.toMap(),
            rejectedClasses = rejectedClasses.toSet()
        )
    }

    private data class RawEpisode(
        val onsetMillis: Long,
        val offsetMillis: Long,
        val peakScore: Double,
        val sumScore: Double,
        val frameCount: Int,
        val peakDb: Double,
        val dominantBandCounts: Map<Double, Int>,
        val rejectedClasses: Set<String>
    ) {
        private val meanScore: Double get() = if (frameCount > 0) sumScore / frameCount else 0.0
        private val dominantBandHz: Double? get() = dominantBandCounts.maxByOrNull { it.value }?.key

        fun mergedWith(other: RawEpisode): RawEpisode = RawEpisode(
            onsetMillis = minOf(onsetMillis, other.onsetMillis),
            offsetMillis = maxOf(offsetMillis, other.offsetMillis),
            peakScore = maxOf(peakScore, other.peakScore),
            sumScore = sumScore + other.sumScore,
            frameCount = frameCount + other.frameCount,
            peakDb = maxOf(peakDb, other.peakDb),
            dominantBandCounts = (dominantBandCounts.keys + other.dominantBandCounts.keys).associateWith { hz ->
                (dominantBandCounts[hz] ?: 0) + (other.dominantBandCounts[hz] ?: 0)
            },
            rejectedClasses = rejectedClasses + other.rejectedClasses
        )

        fun toCandidateOrNull(minDurationMillis: Long, maxDurationMillis: Long): EpisodeCandidate? {
            val duration = offsetMillis - onsetMillis
            if (duration < minDurationMillis || duration > maxDurationMillis) return null
            return EpisodeCandidate(
                onsetMillis = onsetMillis,
                offsetMillis = offsetMillis,
                peakScore = peakScore,
                meanScore = meanScore,
                peakDb = peakDb,
                dominantBandHz = dominantBandHz,
                rejectedClasses = rejectedClasses.toList()
            )
        }
    }

    companion object {
        const val DEFAULT_ENTER_THRESHOLD = 0.7
        const val DEFAULT_EXIT_THRESHOLD = 0.4
        const val DEFAULT_MERGE_GAP_MILLIS = 3_000L
        const val DEFAULT_MIN_DURATION_MILLIS = 300L
        const val DEFAULT_MAX_DURATION_MILLIS = 30_000L
    }
}
