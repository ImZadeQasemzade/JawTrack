package com.jawtrack.corelogic.dsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class FftTest {

    @Test
    fun `isPowerOfTwo accepts powers of two and rejects everything else`() {
        assertTrue(Fft.isPowerOfTwo(1))
        assertTrue(Fft.isPowerOfTwo(2))
        assertTrue(Fft.isPowerOfTwo(1024))
        assertFalse(Fft.isPowerOfTwo(0))
        assertFalse(Fft.isPowerOfTwo(-4))
        assertFalse(Fft.isPowerOfTwo(1000))
    }

    @Test
    fun `DC signal produces energy only in bin zero`() {
        val n = 64
        val real = DoubleArray(n) { 1.0 }
        val imag = DoubleArray(n)

        Fft.transform(real, imag)

        assertEquals(n.toDouble(), real[0], 1e-9)
        for (k in 1 until n) {
            assertEquals(0.0, real[k], 1e-6)
            assertEquals(0.0, imag[k], 1e-6)
        }
    }

    @Test
    fun `pure sinusoid at an exact bin frequency concentrates energy at that bin and its mirror`() {
        val n = 64
        val binIndex = 5
        val real = DoubleArray(n) { i -> sin(2.0 * PI * binIndex * i / n) }
        val imag = DoubleArray(n)

        Fft.transform(real, imag)

        val magnitude = DoubleArray(n) { k -> sqrt(real[k] * real[k] + imag[k] * imag[k]) }
        val peakBin = magnitude.indices.maxBy { magnitude[it] }
        val mirrorBin = n - binIndex

        assertTrue(peakBin == binIndex || peakBin == mirrorBin, "expected peak at $binIndex or $mirrorBin, got $peakBin")

        // energy should be concentrated: some other bin far from both peaks should be much smaller
        val farBin = (binIndex + n / 4) % n
        assertTrue(magnitude[farBin] < magnitude[peakBin] / 10.0)
    }

    @Test
    fun `matches a brute-force DFT for a small random-ish signal`() {
        val n = 16
        val input = DoubleArray(n) { i -> cos(i.toDouble()) + 0.5 * sin(3.0 * i) }
        val real = input.copyOf()
        val imag = DoubleArray(n)

        Fft.transform(real, imag)

        for (k in 0 until n) {
            var expectedRe = 0.0
            var expectedIm = 0.0
            for (t in 0 until n) {
                val angle = -2.0 * PI * k * t / n
                expectedRe += input[t] * cos(angle)
                expectedIm += input[t] * sin(angle)
            }
            assertEquals(expectedRe, real[k], 1e-9, "real part mismatch at bin $k")
            assertEquals(expectedIm, imag[k], 1e-9, "imag part mismatch at bin $k")
        }
    }
}
