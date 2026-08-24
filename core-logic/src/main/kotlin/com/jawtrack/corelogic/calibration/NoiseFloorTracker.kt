package com.jawtrack.corelogic.calibration

import com.jawtrack.corelogic.dsp.ThirdOctaveBands
import kotlin.random.Random

/**
 * Accumulates per-band dB readings across a whole calibration night without ever storing the
 * night's audio itself (JawTrackSpec §4.5, §6.2: only the aggregate profile is ever persisted).
 * A night's worth of frames is too many to sort in full, so each band keeps a fixed-size
 * reservoir sample (Algorithm R) — an unbiased sample of that band's dB distribution — and
 * [computeFloors] reads a low percentile off the sample as the "quiet baseline" for that band,
 * deliberately excluding brief loud events rather than just taking the sample minimum.
 */
class NoiseFloorTracker(
    bandCenters: List<Double> = ThirdOctaveBands.CENTER_FREQUENCIES_HZ,
    private val reservoirCapacity: Int = DEFAULT_RESERVOIR_CAPACITY,
    private val random: Random = Random.Default
) {
    private val reservoirs: Map<Double, MutableList<Double>> = bandCenters.associateWith { mutableListOf() }
    private val sampleCounts: MutableMap<Double, Long> = bandCenters.associateWith { 0L }.toMutableMap()

    fun addSample(bandReadingsDb: Map<Double, Double>) {
        for ((band, value) in bandReadingsDb) {
            val reservoir = reservoirs[band] ?: continue
            val seenSoFar = (sampleCounts[band] ?: 0L) + 1
            sampleCounts[band] = seenSoFar

            if (reservoir.size < reservoirCapacity) {
                reservoir.add(value)
            } else {
                val replaceAt = random.nextLong(seenSoFar)
                if (replaceAt < reservoirCapacity) {
                    reservoir[replaceAt.toInt()] = value
                }
            }
        }
    }

    fun sampleCountFor(bandCenterHz: Double): Long = sampleCounts[bandCenterHz] ?: 0L

    /**
     * @param percentile 0.0 = sample minimum, 1.0 = sample maximum. Defaults to a low
     *   percentile so a single spuriously-quiet frame doesn't set the floor.
     */
    fun computeFloors(percentile: Double = DEFAULT_FLOOR_PERCENTILE): Map<Double, Double> {
        require(percentile in 0.0..1.0) { "percentile must be in [0,1], was $percentile" }

        return reservoirs
            .filterValues { it.isNotEmpty() }
            .mapValues { (_, values) ->
                val sorted = values.sorted()
                val index = ((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.size - 1)
                sorted[index]
            }
    }

    companion object {
        const val DEFAULT_RESERVOIR_CAPACITY = 3000
        const val DEFAULT_FLOOR_PERCENTILE = 0.2
    }
}
