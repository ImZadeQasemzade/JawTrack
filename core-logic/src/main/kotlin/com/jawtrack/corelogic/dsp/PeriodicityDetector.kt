package com.jawtrack.corelogic.dsp

/**
 * Detects a dominant slow periodicity in an energy envelope via normalized autocorrelation —
 * this is how Gate 3 separates snoring's ~3–6 s respiratory rhythm from grinding, which has no
 * such regularity (JawTrackSpec §4.7, §4.9: "build that test explicitly and unit-test it").
 */
object PeriodicityDetector {

    /**
     * @param envelope energy readings (e.g. dB) sampled at a fixed [sampleIntervalMillis].
     * @return the strongest normalized autocorrelation found across lags in
     *   [minPeriodMillis, maxPeriodMillis], in [0, 1]. 0.0 if there isn't enough envelope to
     *   cover the search window, or the envelope is constant (no signal to correlate).
     */
    fun strongestPeriodicity(
        envelope: DoubleArray,
        sampleIntervalMillis: Long,
        minPeriodMillis: Long = 3_000,
        maxPeriodMillis: Long = 6_000
    ): Double {
        require(sampleIntervalMillis > 0) { "sampleIntervalMillis must be positive" }
        require(minPeriodMillis <= maxPeriodMillis) { "minPeriodMillis must not exceed maxPeriodMillis" }

        val minLag = (minPeriodMillis / sampleIntervalMillis).toInt().coerceAtLeast(1)
        val maxLag = (maxPeriodMillis / sampleIntervalMillis).toInt()
        if (envelope.size <= maxLag) return 0.0

        val mean = envelope.average()
        val centered = DoubleArray(envelope.size) { envelope[it] - mean }
        val variance = centered.sumOf { it * it }
        if (variance <= 0.0) return 0.0

        var strongest = 0.0
        for (lag in minLag..maxLag) {
            var sum = 0.0
            for (i in 0 until envelope.size - lag) {
                sum += centered[i] * centered[i + lag]
            }
            val normalized = sum / variance
            if (normalized > strongest) strongest = normalized
        }
        return strongest.coerceIn(0.0, 1.0)
    }
}
