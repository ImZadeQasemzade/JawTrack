package com.jawtrack.corelogic.dsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpectralFeaturesTest {

    @Test
    fun `flat spectrum across a range has flatness near 1`() {
        val bands = mapOf(1000.0 to -40.0, 2000.0 to -40.0, 3150.0 to -40.0, 5000.0 to -40.0)

        val flatness = SpectralFeatures.flatness(bands, lowHz = 1000.0, highHz = 6000.0)

        assertEquals(1.0, flatness, 0.001)
    }

    @Test
    fun `energy concentrated in one band has low flatness`() {
        val bands = mapOf(1000.0 to -10.0, 2000.0 to -60.0, 3150.0 to -60.0, 5000.0 to -60.0)

        val flatness = SpectralFeatures.flatness(bands, lowHz = 1000.0, highHz = 6000.0)

        assertTrue(flatness < 0.3, "expected low flatness for a tonal spectrum, got $flatness")
    }

    @Test
    fun `flatness ignores bands outside the requested range`() {
        // wildly tonal band far outside range shouldn't affect a flat in-range measurement
        val bands = mapOf(50.0 to 20.0, 1000.0 to -40.0, 2000.0 to -40.0, 3150.0 to -40.0)

        val flatness = SpectralFeatures.flatness(bands, lowHz = 1000.0, highHz = 6000.0)

        assertEquals(1.0, flatness, 0.001)
    }

    @Test
    fun `flatness with no bands in range returns 0`() {
        val bands = mapOf(50.0 to -30.0)
        assertEquals(0.0, SpectralFeatures.flatness(bands, lowHz = 1000.0, highHz = 6000.0))
    }

    @Test
    fun `centroid is pulled toward the loudest band`() {
        val lowHeavy = mapOf(100.0 to -10.0, 8000.0 to -60.0)
        val highHeavy = mapOf(100.0 to -60.0, 8000.0 to -10.0)

        val lowCentroid = SpectralFeatures.centroidHz(lowHeavy)
        val highCentroid = SpectralFeatures.centroidHz(highHeavy)

        assertTrue(lowCentroid < highCentroid)
        assertTrue(lowCentroid < 1000.0)
        assertTrue(highCentroid > 4000.0)
    }

    @Test
    fun `centroid of silence is 0`() {
        assertEquals(0.0, SpectralFeatures.centroidHz(emptyMap()))
    }
}
