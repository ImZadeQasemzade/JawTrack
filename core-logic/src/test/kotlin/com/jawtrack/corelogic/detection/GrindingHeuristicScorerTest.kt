package com.jawtrack.corelogic.detection

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class GrindingHeuristicScorerTest {

    private fun flatBandsInGrindingRange(): Map<Double, Double> =
        mapOf(1000.0 to -40.0, 1600.0 to -40.0, 2000.0 to -40.0, 2500.0 to -40.0, 3150.0 to -40.0)

    private fun aperiodicEnvelope(): DoubleArray {
        // no regular structure at all within the 3-6s search window
        val envelope = DoubleArray(150) { -60.0 }
        envelope[10] = -20.0
        envelope[11] = -20.0
        return envelope
    }

    private fun snoreLikePeriodicEnvelope(): DoubleArray {
        val sampleIntervalMs = 200L
        val periodMs = 4_000.0
        return DoubleArray(150) { i -> sin(2.0 * PI * (i * sampleIntervalMs) / periodMs) }
    }

    @Test
    fun `a broadband window with mid-range centroid, no periodicity, in-range sustain scores high`() {
        val input = Gate3Input(
            bandDb = flatBandsInGrindingRange(),
            energyEnvelope = aperiodicEnvelope(),
            energyEnvelopeSampleIntervalMillis = 200,
            sustainedDurationMillis = 1_000
        )

        val score = GrindingHeuristicScorer.score(input)

        assertTrue(score > 0.8, "expected a grinding-like window to score high, got $score")
    }

    @Test
    fun `a periodic snore-like envelope drags the score down even with a grinding-like spectrum`() {
        val grindingInput = Gate3Input(
            bandDb = flatBandsInGrindingRange(),
            energyEnvelope = aperiodicEnvelope(),
            energyEnvelopeSampleIntervalMillis = 200,
            sustainedDurationMillis = 1_000
        )
        val snoreLikeInput = grindingInput.copy(energyEnvelope = snoreLikePeriodicEnvelope())

        val grindingScore = GrindingHeuristicScorer.score(grindingInput)
        val snoreScore = GrindingHeuristicScorer.score(snoreLikeInput)

        assertTrue(snoreScore < grindingScore - 0.15, "expected periodicity to noticeably lower the score")
    }

    @Test
    fun `sustain duration outside 0_4-5s range costs score even with an otherwise ideal window`() {
        val tooShort = Gate3Input(
            bandDb = flatBandsInGrindingRange(),
            energyEnvelope = aperiodicEnvelope(),
            energyEnvelopeSampleIntervalMillis = 200,
            sustainedDurationMillis = 50
        )
        val inRange = tooShort.copy(sustainedDurationMillis = 1_000)

        assertTrue(GrindingHeuristicScorer.score(tooShort) < GrindingHeuristicScorer.score(inRange))
    }

    @Test
    fun `a pure tone spectrum (low flatness) scores much lower than broadband noise`() {
        val toneOnly = Gate3Input(
            bandDb = mapOf(2000.0 to -10.0, 1000.0 to -70.0, 3150.0 to -70.0),
            energyEnvelope = aperiodicEnvelope(),
            energyEnvelopeSampleIntervalMillis = 200,
            sustainedDurationMillis = 1_000
        )
        val broadband = toneOnly.copy(bandDb = flatBandsInGrindingRange())

        assertTrue(GrindingHeuristicScorer.score(toneOnly) < GrindingHeuristicScorer.score(broadband))
    }

    @Test
    fun `a centroid far outside 1_5-4kHz costs score`() {
        val lowCentroid = Gate3Input(
            bandDb = mapOf(100.0 to -30.0, 125.0 to -30.0, 160.0 to -30.0),
            energyEnvelope = aperiodicEnvelope(),
            energyEnvelopeSampleIntervalMillis = 200,
            sustainedDurationMillis = 1_000
        )

        assertTrue(GrindingHeuristicScorer.score(lowCentroid) < 0.8)
    }
}
