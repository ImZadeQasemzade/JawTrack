package com.jawtrack.corelogic.report

import com.jawtrack.corelogic.enrichment.EpisodeSummary

/**
 * Builds Screen 1's plain-language summary (§8): "9 episodes, mostly between 2:10 and 3:40,
 * clustered in light sleep." Clock-time formatting is a locale/timezone concern left to the
 * UI layer — this produces the structured facts and assembles the sentence shape around
 * already-formatted strings, so the sentence logic itself stays testable without pinning a
 * timezone in tests.
 */
object NightSummaryText {

    data class Summary(
        val episodeCount: Int,
        val earliestOnsetMillis: Long?,
        val latestOffsetMillis: Long?,
        /** The stage the most episodes fell in. "Mostly" in the sentence refers to the time span across all of them, not a filtered subset. */
        val dominantStage: String?
    )

    fun summarize(episodes: List<EpisodeSummary>): Summary {
        if (episodes.isEmpty()) return Summary(0, null, null, null)

        val dominantStage = episodes
            .mapNotNull { it.sleepStage }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return Summary(
            episodeCount = episodes.size,
            earliestOnsetMillis = episodes.minOf { it.onsetMillis },
            latestOffsetMillis = episodes.maxOf { it.offsetMillis },
            dominantStage = dominantStage
        )
    }

    fun formatSentence(
        summary: Summary,
        formatTime: (Long) -> String,
        stageDisplayName: (String) -> String
    ): String {
        if (summary.episodeCount == 0) return "No grinding episodes detected."

        val countPhrase = "${summary.episodeCount} episode" + if (summary.episodeCount == 1) "" else "s"

        val timeRangePhrase = if (summary.earliestOnsetMillis != null && summary.latestOffsetMillis != null) {
            ", mostly between ${formatTime(summary.earliestOnsetMillis)} and ${formatTime(summary.latestOffsetMillis)}"
        } else {
            ""
        }

        val stagePhrase = summary.dominantStage?.let { ", clustered in ${stageDisplayName(it)}" } ?: ""

        return "$countPhrase$timeRangePhrase$stagePhrase."
    }
}
