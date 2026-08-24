package com.jawtrack.corelogic.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaveformDownsamplerTest {

    @Test
    fun `silence downsamples to all zeros`() {
        val silence = ShortArray(1000)
        val waveform = WaveformDownsampler.downsample(silence, columns = 10)

        assertEquals(10, waveform.size)
        assertTrue(waveform.all { it == 0f })
    }

    @Test
    fun `full-scale samples downsample to peak amplitude`() {
        val loud = ShortArray(1000) { Short.MAX_VALUE }
        val waveform = WaveformDownsampler.downsample(loud, columns = 10)

        waveform.forEach { assertEquals(1.0f, it, 0.001f) }
    }

    @Test
    fun `a loud first half and quiet second half show up as distinct columns`() {
        val samples = ShortArray(1000) { i -> if (i < 500) Short.MAX_VALUE else 0 }

        val waveform = WaveformDownsampler.downsample(samples, columns = 2)

        assertEquals(1.0f, waveform[0], 0.001f)
        assertEquals(0.0f, waveform[1], 0.001f)
    }

    @Test
    fun `negative-amplitude samples contribute their magnitude, not a negative value`() {
        val samples = ShortArray(100) { Short.MIN_VALUE }
        val waveform = WaveformDownsampler.downsample(samples, columns = 1)

        assertEquals(1.0f, waveform[0], 0.001f)
    }

    @Test
    fun `zero columns or empty samples return an empty array rather than crashing`() {
        assertEquals(0, WaveformDownsampler.downsample(shortArrayOf(1, 2, 3), columns = 0).size)
        assertEquals(0, WaveformDownsampler.downsample(ShortArray(0), columns = 10).size)
    }
}
