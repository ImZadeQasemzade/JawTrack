package com.jawtrack.corelogic.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfidenceGraderTest {

    private fun perfectNight() = ConfidenceGrader.Inputs(
        coveragePct = 100.0,
        cleanShutdown = true,
        speechRejectedCount = 0,
        sessionDurationHours = 8.0,
        episodeCount = 10,
        labeledEpisodeCount = 10,
        roomNoiseFloorDb = -60.0
    )

    private fun worstNight() = ConfidenceGrader.Inputs(
        coveragePct = 0.0,
        cleanShutdown = false,
        speechRejectedCount = 1000,
        sessionDurationHours = 8.0,
        episodeCount = 10,
        labeledEpisodeCount = 0,
        roomNoiseFloorDb = -10.0
    )

    @Test
    fun `a clean, fully-covered, quiet, fully-labeled night grades A`() {
        assertEquals(100.0, ConfidenceGrader.score(perfectNight()), 0.01)
        assertEquals("A", ConfidenceGrader.grade(perfectNight()))
    }

    @Test
    fun `an interrupted, noisy, unclean-shutdown night grades C`() {
        assertEquals(0.0, ConfidenceGrader.score(worstNight()), 0.01)
        assertEquals("C", ConfidenceGrader.grade(worstNight()))
    }

    @Test
    fun `a night with zero episodes is not penalized on the labeled-proportion factor`() {
        val noEpisodes = perfectNight().copy(episodeCount = 0, labeledEpisodeCount = 0)
        assertEquals(100.0, ConfidenceGrader.score(noEpisodes), 0.01)
    }

    @Test
    fun `an uncalibrated room (null noise floor) scores worse than a quiet one but better than a noisy one`() {
        val uncalibrated = perfectNight().copy(roomNoiseFloorDb = null)
        val quiet = perfectNight()
        val noisy = perfectNight().copy(roomNoiseFloorDb = -10.0)

        val uncalibratedScore = ConfidenceGrader.score(uncalibrated)
        val quietScore = ConfidenceGrader.score(quiet)
        val noisyScore = ConfidenceGrader.score(noisy)

        assertTrue(uncalibratedScore < quietScore)
        assertTrue(uncalibratedScore > noisyScore)
    }

    @Test
    fun `coverage alone moves the grade even when everything else is perfect`() {
        val lowCoverage = perfectNight().copy(coveragePct = 60.0)
        assertTrue(ConfidenceGrader.score(lowCoverage) < ConfidenceGrader.score(perfectNight()))
    }

    @Test
    fun `an unclean shutdown alone drags a grade down even with perfect coverage`() {
        val uncleanShutdown = perfectNight().copy(cleanShutdown = false)
        assertTrue(ConfidenceGrader.score(uncleanShutdown) < ConfidenceGrader.score(perfectNight()))
    }

    @Test
    fun `heavy speech rejection lowers the grade`() {
        val lotsOfSpeech = perfectNight().copy(speechRejectedCount = 200) // 25/hr over 8h, well past the 20/hr cap
        assertEquals(
            ConfidenceGrader.score(perfectNight()) - 0.15 * 100.0,
            ConfidenceGrader.score(lotsOfSpeech),
            0.01
        )
    }

    @Test
    fun `zero session duration does not crash or divide by zero`() {
        val zeroDuration = perfectNight().copy(sessionDurationHours = 0.0)
        val score = ConfidenceGrader.score(zeroDuration)
        assertTrue(score in 0.0..100.0)
    }
}
