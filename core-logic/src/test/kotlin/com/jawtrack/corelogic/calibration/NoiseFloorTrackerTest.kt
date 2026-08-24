package com.jawtrack.corelogic.calibration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class NoiseFloorTrackerTest {

    private val band = 1000.0

    @Test
    fun `below capacity, computeFloors reads the exact requested percentile of all samples seen`() {
        val tracker = NoiseFloorTracker(bandCenters = listOf(band), reservoirCapacity = 100)
        // 0..99 dB, so the 20th percentile (index 19 of 100 sorted values, 0-indexed) is 19.0
        (0..99).forEach { tracker.addSample(mapOf(band to it.toDouble())) }

        val floors = tracker.computeFloors(percentile = 0.2)

        assertEquals(19.0, floors.getValue(band), 0.001)
    }

    @Test
    fun `percentile 0 returns the minimum and 1 returns the maximum`() {
        val tracker = NoiseFloorTracker(bandCenters = listOf(band), reservoirCapacity = 100)
        listOf(5.0, -30.0, 12.0, 100.0, -10.0).forEach { tracker.addSample(mapOf(band to it)) }

        assertEquals(-30.0, tracker.computeFloors(percentile = 0.0).getValue(band), 0.001)
        assertEquals(100.0, tracker.computeFloors(percentile = 1.0).getValue(band), 0.001)
    }

    @Test
    fun `reservoir sampling over capacity still yields a floor within the true data range`() {
        val tracker = NoiseFloorTracker(bandCenters = listOf(band), reservoirCapacity = 50, random = Random(42))
        // simulate a whole night: ~28,800 frames at ~1 per second for 8h, values in a known range
        repeat(28_800) { i -> tracker.addSample(mapOf(band to (-60.0 + (i % 40)))) }

        assertEquals(28_800L, tracker.sampleCountFor(band))

        val floor = tracker.computeFloors(percentile = 0.2).getValue(band)
        assertTrue(floor in -60.0..-20.0, "expected floor within true data range, got $floor")
    }

    @Test
    fun `bands with no samples are absent from the result rather than reported as some default`() {
        val tracker = NoiseFloorTracker(bandCenters = listOf(band, 2000.0))
        tracker.addSample(mapOf(band to -40.0))

        val floors = tracker.computeFloors()

        assertTrue(floors.containsKey(band))
        assertTrue(!floors.containsKey(2000.0))
    }

    @Test
    fun `invalid percentile is rejected`() {
        val tracker = NoiseFloorTracker(bandCenters = listOf(band))
        tracker.addSample(mapOf(band to 0.0))

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            tracker.computeFloors(percentile = 1.5)
        }
    }
}
