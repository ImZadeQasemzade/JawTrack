package com.jawtrack.corelogic.report

import com.jawtrack.corelogic.enrichment.EpisodeSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NightSummaryTextTest {

    private val identityFormatTime: (Long) -> String = { it.toString() }
    private val identityStageName: (String) -> String = { it.lowercase() }

    @Test
    fun `no episodes produces a null-safe empty summary and a neutral sentence`() {
        val summary = NightSummaryText.summarize(emptyList())

        assertEquals(0, summary.episodeCount)
        assertNull(summary.earliestOnsetMillis)
        assertNull(summary.dominantStage)
        assertEquals("No grinding episodes detected.", NightSummaryText.formatSentence(summary, identityFormatTime, identityStageName))
    }

    @Test
    fun `singular episode count uses singular wording`() {
        val summary = NightSummaryText.summarize(listOf(EpisodeSummary(1000, 2000, "LIGHT")))

        val sentence = NightSummaryText.formatSentence(summary, identityFormatTime, identityStageName)

        assertEquals("1 episode, mostly between 1000 and 2000, clustered in light.", sentence)
    }

    @Test
    fun `plural episode count and the spec's example shape`() {
        val episodes = listOf(
            EpisodeSummary(1000, 1500, "LIGHT"),
            EpisodeSummary(2000, 2500, "LIGHT"),
            EpisodeSummary(3000, 3500, "DEEP")
        )
        val summary = NightSummaryText.summarize(episodes)

        assertEquals(3, summary.episodeCount)
        assertEquals(1000L, summary.earliestOnsetMillis)
        assertEquals(3500L, summary.latestOffsetMillis)
        assertEquals("LIGHT", summary.dominantStage) // 2 of 3 episodes

        val sentence = NightSummaryText.formatSentence(summary, identityFormatTime, identityStageName)
        assertEquals("3 episodes, mostly between 1000 and 3500, clustered in light.", sentence)
    }

    @Test
    fun `episodes with no joined sleep stage data omit the stage clause`() {
        val summary = NightSummaryText.summarize(listOf(EpisodeSummary(1000, 2000, sleepStage = null)))

        val sentence = NightSummaryText.formatSentence(summary, identityFormatTime, identityStageName)

        assertEquals("1 episode, mostly between 1000 and 2000.", sentence)
        assertNull(summary.dominantStage)
    }
}
