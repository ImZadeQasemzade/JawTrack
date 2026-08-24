package com.jawtrack.corelogic.report

import com.jawtrack.corelogic.dsp.BandEnergyAnalyzer

/** One time-slice of the spectrogram: per-band dB at that moment. */
data class SpectrogramFrame(val bandDb: Map<Double, Double>)

/**
 * Short-time spectrogram for Screen 3's clip view (§8: "grinding shows a broadband smear vs.
 * snoring's harmonic stack — it makes labeling fast"). Reuses [BandEnergyAnalyzer] — the same
 * band-energy computation Gate 3 already runs — rather than a second FFT pipeline just for
 * display.
 */
object SpectrogramGenerator {

    const val DEFAULT_WINDOW_SAMPLES = 1_024

    fun generate(
        samples: ShortArray,
        sampleRateHz: Int,
        windowSamples: Int = DEFAULT_WINDOW_SAMPLES,
        hopSamples: Int = windowSamples / 2
    ): List<SpectrogramFrame> {
        if (samples.size < windowSamples) return emptyList()

        val analyzer = BandEnergyAnalyzer(sampleRateHz, windowSamples)
        val frames = mutableListOf<SpectrogramFrame>()

        var start = 0
        while (start + windowSamples <= samples.size) {
            val window = samples.copyOfRange(start, start + windowSamples)
            frames.add(SpectrogramFrame(analyzer.analyze(BandEnergyAnalyzer.normalize(window))))
            start += hopSamples
        }

        return frames
    }
}
