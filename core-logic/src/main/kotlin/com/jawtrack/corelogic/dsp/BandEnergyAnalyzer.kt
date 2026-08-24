package com.jawtrack.corelogic.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10

/**
 * Windows a raw PCM frame, runs it through [Fft], and buckets the spectrum into third-octave
 * bands (JawTrackSpec §4.5: per-band noise floor for the calibration night). The bin/band
 * mapping is precomputed once per instance since it only depends on sample rate and frame
 * size, not on the audio itself.
 */
class BandEnergyAnalyzer(private val sampleRateHz: Int, private val frameSize: Int) {

    init {
        require(Fft.isPowerOfTwo(frameSize)) { "frameSize must be a power of two, was $frameSize" }
    }

    private val hannWindow = DoubleArray(frameSize) { i ->
        0.5 - 0.5 * cos(2.0 * PI * i / (frameSize - 1))
    }

    private val binFrequenciesHz = DoubleArray(frameSize / 2 + 1) { k ->
        k * sampleRateHz.toDouble() / frameSize
    }

    /** Center Hz -> FFT bin indices that fall inside that band's edges (§ThirdOctaveBands). */
    private val bandToBins: Map<Double, List<Int>> = buildBandToBins()

    private fun buildBandToBins(): Map<Double, List<Int>> {
        val nyquist = sampleRateHz / 2.0
        val result = linkedMapOf<Double, List<Int>>()
        for (center in ThirdOctaveBands.CENTER_FREQUENCIES_HZ) {
            if (center > nyquist) continue
            val edges = ThirdOctaveBands.edgesHz(center)
            val bins = binFrequenciesHz.indices.filter { binFrequenciesHz[it] in edges }
            // At short frame sizes, low bands can be narrower than one FFT bin; fall back to
            // whichever single bin is closest to the band center so every band still reports.
            result[center] = bins.ifEmpty {
                listOf(binFrequenciesHz.indices.minBy { abs(binFrequenciesHz[it] - center) })
            }
        }
        return result
    }

    /** @param samples normalized PCM in [-1, 1], exactly [frameSize] long. */
    fun analyze(samples: DoubleArray): Map<Double, Double> {
        require(samples.size == frameSize) { "expected $frameSize samples, got ${samples.size}" }

        val real = DoubleArray(frameSize) { samples[it] * hannWindow[it] }
        val imag = DoubleArray(frameSize)
        Fft.transform(real, imag)

        val magnitudeSquared = DoubleArray(frameSize / 2 + 1) { k -> real[k] * real[k] + imag[k] * imag[k] }

        return bandToBins.mapValues { (_, bins) ->
            val energy = bins.sumOf { magnitudeSquared[it] }
            10.0 * log10(energy + EPSILON)
        }
    }

    companion object {
        private const val EPSILON = 1e-12

        fun normalize(samples: ShortArray): DoubleArray = DoubleArray(samples.size) { samples[it] / 32768.0 }
    }
}
