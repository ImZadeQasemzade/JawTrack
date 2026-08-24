package com.jawtrack.corelogic.report

import kotlin.math.abs

/** Reduces a clip's raw PCM to a peak-per-column waveform for Screen 3's clip view (§8). */
object WaveformDownsampler {

    /** Peak absolute amplitude in [0, 1] per column. Empty array if [columns] <= 0 or [samples] is empty. */
    fun downsample(samples: ShortArray, columns: Int): FloatArray {
        if (columns <= 0 || samples.isEmpty()) return FloatArray(0)

        val result = FloatArray(columns)
        val samplesPerColumn = samples.size.toDouble() / columns

        for (column in 0 until columns) {
            val start = (column * samplesPerColumn).toInt()
            val end = ((column + 1) * samplesPerColumn).toInt().coerceAtMost(samples.size)

            var peak = 0
            for (i in start until end) {
                val magnitude = abs(samples[i].toInt())
                if (magnitude > peak) peak = magnitude
            }
            result[column] = (peak / 32_768f).coerceIn(0f, 1f)
        }

        return result
    }
}
