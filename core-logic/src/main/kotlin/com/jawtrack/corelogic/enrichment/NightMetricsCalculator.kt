package com.jawtrack.corelogic.enrichment

/** One episode's shape as needed for night-level aggregation — deliberately narrower than the full `Episode` entity. */
data class EpisodeSummary(val onsetMillis: Long, val offsetMillis: Long, val sleepStage: String?)

/** Night-level rollup (§7.1) — the JawTrack Index and its supporting numbers. */
data class NightMetrics(
    val episodeCount: Int,
    /** The JawTrack Index. Never labeled with clinical severity terms (§7.1). */
    val episodesPerHour: Double,
    val totalGrindingSeconds: Long,
    val longestEpisodeMs: Long,
    /** Stage name -> how many episodes fell in it (not time spent — where the grinding happened). */
    val stageDistribution: Map<String, Int>,
    /** Snoring-flagged Gate 2 detections per hour of session. Reported neutrally, no apnea inference (§7.1). */
    val snoreIndex: Double
)

object NightMetricsCalculator {

    fun compute(
        episodes: List<EpisodeSummary>,
        sleepDurationHours: Double,
        snoringRejectedCount: Int,
        sessionDurationHours: Double
    ): NightMetrics {
        val durationsMs = episodes.map { it.offsetMillis - it.onsetMillis }

        return NightMetrics(
            episodeCount = episodes.size,
            episodesPerHour = if (sleepDurationHours > 0) episodes.size / sleepDurationHours else 0.0,
            totalGrindingSeconds = durationsMs.sum() / 1000,
            longestEpisodeMs = durationsMs.maxOrNull() ?: 0L,
            stageDistribution = episodes.mapNotNull { it.sleepStage }.groupingBy { it }.eachCount(),
            snoreIndex = if (sessionDurationHours > 0) snoringRejectedCount / sessionDurationHours else 0.0
        )
    }
}
