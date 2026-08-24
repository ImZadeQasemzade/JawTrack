package com.jawtrack.corelogic.dsp

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Spectral shape measures over a [BandEnergyAnalyzer] output (`centerHz -> dB`), used by
 * Gate 3's grinding heuristic (JawTrackSpec §4.7) and its snore-rejection test (§4.9):
 * grinding is broadband/noisy (high flatness), snoring is more tonal/harmonic and centered
 * lower than grinding's 1.5–4 kHz.
 */
object SpectralFeatures {

    private const val EPSILON = 1e-12

    /**
     * Wiener spectral flatness (geometric mean / arithmetic mean of band power) restricted to
     * [lowHz, highHz]. 1.0 = perfectly flat/noisy, near 0.0 = energy concentrated in a few
     * tonal bands.
     */
    fun flatness(bandDb: Map<Double, Double>, lowHz: Double, highHz: Double): Double {
        val powers = bandDb.filterKeys { it in lowHz..highHz }.values.map { dbToPower(it) }
        if (powers.isEmpty()) return 0.0

        val arithmeticMean = powers.average()
        if (arithmeticMean <= 0.0) return 0.0

        val geometricMean = exp(powers.map { ln(it.coerceAtLeast(EPSILON)) }.average())
        return (geometricMean / arithmeticMean).coerceIn(0.0, 1.0)
    }

    /** Power-weighted average frequency, optionally restricted to a sub-range. */
    fun centroidHz(bandDb: Map<Double, Double>, lowHz: Double = 0.0, highHz: Double = Double.MAX_VALUE): Double {
        val entries = bandDb.filterKeys { it in lowHz..highHz }
        val totalPower = entries.values.sumOf { dbToPower(it) }
        if (totalPower <= 0.0) return 0.0

        val weightedSum = entries.entries.sumOf { (hz, db) -> hz * dbToPower(db) }
        return weightedSum / totalPower
    }

    private fun dbToPower(db: Double): Double = 10.0.pow(db / 10.0)
}
