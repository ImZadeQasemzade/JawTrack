package com.jawtrack.corelogic.dsp

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class BandEnergyAnalyzerTest {

    private val sampleRate = 16_000
    private val frameSize = 1024

    private fun sineWave(freqHz: Double, amplitude: Double = 0.5): DoubleArray =
        DoubleArray(frameSize) { i -> amplitude * sin(2.0 * PI * freqHz * i / sampleRate) }

    @Test
    fun `a 1kHz tone reads loudest in the 1000Hz band`() {
        val analyzer = BandEnergyAnalyzer(sampleRate, frameSize)
        val bands = analyzer.analyze(sineWave(1000.0))

        val loudestBand = bands.entries.maxBy { it.value }.key

        assertTrue(loudestBand in 800.0..1250.0, "expected loudest band near 1000Hz, got $loudestBand")
    }

    @Test
    fun `a 1kHz tone is much quieter in a distant band than in its own band`() {
        val analyzer = BandEnergyAnalyzer(sampleRate, frameSize)
        val bands = analyzer.analyze(sineWave(1000.0))

        val ownBandDb = bands.getValue(1000.0)
        val distantBandDb = bands.getValue(100.0)

        assertTrue(ownBandDb - distantBandDb > 20.0, "expected >20dB separation, got own=$ownBandDb distant=$distantBandDb")
    }

    @Test
    fun `silence produces a low, roughly uniform floor across bands`() {
        val analyzer = BandEnergyAnalyzer(sampleRate, frameSize)
        val bands = analyzer.analyze(DoubleArray(frameSize))

        assertTrue(bands.values.all { it < -50.0 })
    }

    @Test
    fun `every standard band up to Nyquist is present in the output`() {
        val analyzer = BandEnergyAnalyzer(sampleRate, frameSize)
        val bands = analyzer.analyze(sineWave(1000.0))

        val expectedBands = ThirdOctaveBands.CENTER_FREQUENCIES_HZ.filter { it <= sampleRate / 2.0 }
        assertTrue(bands.keys.containsAll(expectedBands))
    }
}
