package com.jawtrack.corelogic.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrogramGeneratorTest {

    private val sampleRate = 16_000

    private fun toneSamples(freqHz: Double, count: Int, amplitude: Double = 0.5): ShortArray =
        ShortArray(count) { i -> (amplitude * sin(2.0 * PI * freqHz * i / sampleRate) * 32_767).toInt().toShort() }

    @Test
    fun `a clip shorter than one window produces no frames`() {
        val samples = ShortArray(100)
        val frames = SpectrogramGenerator.generate(samples, sampleRate, windowSamples = 1024)

        assertTrue(frames.isEmpty())
    }

    @Test
    fun `a clip exactly one window long produces exactly one frame`() {
        val samples = ShortArray(1024)
        val frames = SpectrogramGenerator.generate(samples, sampleRate, windowSamples = 1024, hopSamples = 512)

        assertEquals(1, frames.size)
    }

    @Test
    fun `hop stepping produces the expected number of frames`() {
        // window=1024, hop=512, total=2048 -> windows start at 0 and 512 (1024 would need samples up to 1536, still fits) and 1024 (up to 2048, fits)
        val samples = ShortArray(2048)
        val frames = SpectrogramGenerator.generate(samples, sampleRate, windowSamples = 1024, hopSamples = 512)

        assertEquals(3, frames.size) // starts at 0, 512, 1024
    }

    @Test
    fun `a 1kHz tone reads loudest in the 1000Hz band across every frame`() {
        val samples = toneSamples(1000.0, count = 4096)
        val frames = SpectrogramGenerator.generate(samples, sampleRate, windowSamples = 1024, hopSamples = 1024)

        assertTrue(frames.isNotEmpty())
        frames.forEach { frame ->
            val loudestBand = frame.bandDb.entries.maxBy { it.value }.key
            assertTrue(loudestBand in 800.0..1250.0, "expected loudest band near 1000Hz, got $loudestBand")
        }
    }
}
