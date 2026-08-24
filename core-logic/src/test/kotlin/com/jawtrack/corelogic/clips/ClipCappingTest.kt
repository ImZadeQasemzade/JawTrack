package com.jawtrack.corelogic.clips

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClipCappingTest {

    @Test
    fun `maxClipSamples is 12 seconds worth at the given sample rate`() {
        assertEquals(12 * 16_000, ClipCapping.maxClipSamples(sampleRateHz = 16_000))
    }

    @Test
    fun `audio shorter than the cap is returned unchanged`() {
        val samples = shortArrayOf(1, 2, 3)
        assertArrayEquals(samples, ClipCapping.cap(samples, maxSamples = 100))
    }

    @Test
    fun `audio exactly at the cap is returned unchanged`() {
        val samples = shortArrayOf(1, 2, 3)
        assertArrayEquals(samples, ClipCapping.cap(samples, maxSamples = 3))
    }

    @Test
    fun `audio longer than the cap is truncated to the most recent samples, not the oldest`() {
        val samples = shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        val capped = ClipCapping.cap(samples, maxSamples = 4)

        assertArrayEquals(shortArrayOf(7, 8, 9, 10), capped)
    }

    @Test
    fun `audio far longer than the cap (e_g_ a whole ring buffer's worth) is still capped down to 12 seconds`() {
        val thirtySecondsOfSamples = ShortArray(30 * 16_000) { (it % 100).toShort() }

        val capped = ClipCapping.cap(thirtySecondsOfSamples, ClipCapping.maxClipSamples(16_000))

        assertEquals(12 * 16_000, capped.size)
    }
}
