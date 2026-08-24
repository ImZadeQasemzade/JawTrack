package com.jawtrack.corelogic.detection

import com.jawtrack.corelogic.dsp.PeriodicityDetector
import com.jawtrack.corelogic.dsp.SpectralFeatures

/**
 * @param bandDb current window's per-band energy (from `BandEnergyAnalyzer`).
 * @param energyEnvelope recent broadband energy history, for the periodicity check — needs
 *   to span at least [PERIODICITY_MAX_MS] to say anything about ~3-6s snore rhythm.
 * @param energyEnvelopeSampleIntervalMillis sample spacing of [energyEnvelope].
 * @param sustainedDurationMillis how long Gate 1's energy threshold has been continuously
 *   crossed for this candidate (i.e. [EnergyGate]'s running sustain timer).
 */
data class Gate3Input(
    val bandDb: Map<Double, Double>,
    val energyEnvelope: DoubleArray,
    val energyEnvelopeSampleIntervalMillis: Long,
    val sustainedDurationMillis: Long
)

/**
 * Gate 3's v1 heuristic stand-in for the trained dense head (JawTrackSpec §4.7): scores a
 * window on the four documented criteria and averages them into a single confidence in
 * [0, 1], deliberately shaped like a classifier output so `EpisodeAssembler`'s hysteresis
 * thresholding (enter >0.7, exit <0.4) works the same way once a trained head replaces this
 * (§4.7: "keep the heuristic as fallback and cross-check").
 */
object GrindingHeuristicScorer {

    private const val FLATNESS_LOW_HZ = 1_000.0
    private const val FLATNESS_HIGH_HZ = 6_000.0
    private const val FLATNESS_THRESHOLD = 0.5

    private val CENTROID_RANGE_HZ = 1_500.0..4_000.0
    private val SUSTAIN_RANGE_MILLIS = 400L..5_000L

    fun score(input: Gate3Input): Double {
        val flatness = SpectralFeatures.flatness(input.bandDb, FLATNESS_LOW_HZ, FLATNESS_HIGH_HZ)
        val centroid = SpectralFeatures.centroidHz(input.bandDb)
        val periodicity = PeriodicityDetector.strongestPeriodicity(
            input.energyEnvelope,
            input.energyEnvelopeSampleIntervalMillis
        )

        val flatnessScore = (flatness / FLATNESS_THRESHOLD).coerceIn(0.0, 1.0)
        val centroidScore = if (centroid in CENTROID_RANGE_HZ) 1.0 else 0.0
        val periodicityAbsenceScore = (1.0 - periodicity).coerceIn(0.0, 1.0)
        val sustainScore = if (input.sustainedDurationMillis in SUSTAIN_RANGE_MILLIS) 1.0 else 0.0

        return listOf(flatnessScore, centroidScore, periodicityAbsenceScore, sustainScore).average()
    }
}
