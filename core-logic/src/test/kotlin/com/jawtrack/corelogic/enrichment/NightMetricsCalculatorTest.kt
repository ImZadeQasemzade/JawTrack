package com.jawtrack.corelogic.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NightMetricsCalculatorTest {

    @Test
    fun `empty night has zeroed metrics, not NaN or a crash`() {
        val metrics = NightMetricsCalculator.compute(
            episodes = emptyList(),
            sleepDurationHours = 8.0,
            snoringRejectedCount = 0,
            sessionDurationHours = 8.0
        )

        assertEquals(0, metrics.episodeCount)
        assertEquals(0.0, metrics.episodesPerHour)
        assertEquals(0L, metrics.totalGrindingSeconds)
        assertEquals(0L, metrics.longestEpisodeMs)
        assertEquals(emptyMap<String, Int>(), metrics.stageDistribution)
        assertEquals(0.0, metrics.snoreIndex)
    }

    @Test
    fun `episodesPerHour divides by hours of sleep, not session duration`() {
        val episodes = List(8) { EpisodeSummary(it * 1000L, it * 1000L + 500, "LIGHT") }

        val metrics = NightMetricsCalculator.compute(
            episodes = episodes,
            sleepDurationHours = 4.0,
            snoringRejectedCount = 0,
            sessionDurationHours = 8.0
        )

        assertEquals(2.0, metrics.episodesPerHour, 0.0001) // 8 episodes / 4h asleep, not /8h in bed
    }

    @Test
    fun `totalGrindingSeconds sums durations and longestEpisodeMs picks the max`() {
        val episodes = listOf(
            EpisodeSummary(0, 2_000, "LIGHT"),   // 2s
            EpisodeSummary(10_000, 15_000, "DEEP"), // 5s
            EpisodeSummary(20_000, 20_800, "REM")   // 0.8s
        )

        val metrics = NightMetricsCalculator.compute(episodes, 8.0, 0, 8.0)

        assertEquals(7L, metrics.totalGrindingSeconds) // 2 + 5 + 0.8 -> integer ms division
        assertEquals(5_000L, metrics.longestEpisodeMs)
    }

    @Test
    fun `stageDistribution counts episodes per stage, not time spent`() {
        val episodes = listOf(
            EpisodeSummary(0, 1_000, "LIGHT"),
            EpisodeSummary(2_000, 3_000, "LIGHT"),
            EpisodeSummary(4_000, 20_000, "DEEP") // long duration shouldn't inflate its count beyond 1
        )

        val metrics = NightMetricsCalculator.compute(episodes, 8.0, 0, 8.0)

        assertEquals(mapOf("LIGHT" to 2, "DEEP" to 1), metrics.stageDistribution)
    }

    @Test
    fun `episodes with no joined sleep stage are excluded from the distribution but still counted`() {
        val episodes = listOf(
            EpisodeSummary(0, 1_000, "LIGHT"),
            EpisodeSummary(2_000, 3_000, null) // no Health Connect data covering this one
        )

        val metrics = NightMetricsCalculator.compute(episodes, 8.0, 0, 8.0)

        assertEquals(2, metrics.episodeCount)
        assertEquals(mapOf("LIGHT" to 1), metrics.stageDistribution)
    }

    @Test
    fun `snoreIndex is snoring rejections per hour of session`() {
        val metrics = NightMetricsCalculator.compute(emptyList(), 8.0, snoringRejectedCount = 40, sessionDurationHours = 8.0)
        assertEquals(5.0, metrics.snoreIndex, 0.0001)
    }
}
