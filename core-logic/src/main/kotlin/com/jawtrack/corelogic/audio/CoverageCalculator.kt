package com.jawtrack.corelogic.audio

/** A period during the night when audio was not being captured (mic loss, call, gap on restart). */
data class GapInterval(val startAtMillis: Long, val endAtMillis: Long) {
    init {
        require(endAtMillis >= startAtMillis) { "gap end must not precede start" }
    }

    val durationMillis: Long get() = endAtMillis - startAtMillis
}

data class CoverageResult(val coveragePct: Double, val totalGapSeconds: Long)

/**
 * Turns the night's planned duration and recorded [GapInterval]s into the honest coverage
 * number the report screens are required to show (JawTrackSpec §4.3, §8: "always show what
 * you missed" — a low-coverage night must not present its index with full confidence).
 */
object CoverageCalculator {

    fun compute(sessionStartAtMillis: Long, sessionEndAtMillis: Long, gaps: List<GapInterval>): CoverageResult {
        val totalDurationMillis = sessionEndAtMillis - sessionStartAtMillis
        if (totalDurationMillis <= 0) return CoverageResult(coveragePct = 0.0, totalGapSeconds = 0)

        val clampedGapMillis = mergeIntervals(gaps, sessionStartAtMillis, sessionEndAtMillis)
            .sumOf { it.durationMillis }

        val coveredMillis = (totalDurationMillis - clampedGapMillis).coerceAtLeast(0)
        val coveragePct = (coveredMillis.toDouble() / totalDurationMillis.toDouble()) * 100.0

        return CoverageResult(
            coveragePct = coveragePct,
            totalGapSeconds = clampedGapMillis / 1000
        )
    }

    /** Clamps each gap to the session window, then merges overlapping/adjacent ones so total gap time is a true union, not a naive sum. */
    private fun mergeIntervals(
        gaps: List<GapInterval>,
        sessionStartAtMillis: Long,
        sessionEndAtMillis: Long
    ): List<GapInterval> {
        val clamped = gaps
            .map { gap ->
                gap.startAtMillis.coerceIn(sessionStartAtMillis, sessionEndAtMillis) to
                    gap.endAtMillis.coerceIn(sessionStartAtMillis, sessionEndAtMillis)
            }
            .filter { (start, end) -> end > start }
            .sortedBy { it.first }

        val merged = mutableListOf<GapInterval>()
        for ((start, end) in clamped) {
            val last = merged.lastOrNull()
            if (last != null && start <= last.endAtMillis) {
                if (end > last.endAtMillis) {
                    merged[merged.lastIndex] = last.copy(endAtMillis = end)
                }
            } else {
                merged.add(GapInterval(start, end))
            }
        }
        return merged
    }
}
