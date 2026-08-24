package com.jawtrack.corelogic.audio

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudioRingBufferTest {

    @Test
    fun `snapshot before full is exactly what was written, in order`() {
        val buffer = AudioRingBuffer(capacitySamples = 10)
        buffer.write(shortArrayOf(1, 2, 3, 4, 5))

        assertEquals(5, buffer.samplesAvailable)
        assertArrayEquals(shortArrayOf(1, 2, 3, 4, 5), buffer.snapshot())
    }

    @Test
    fun `wraps around and keeps only the most recent capacity samples`() {
        val buffer = AudioRingBuffer(capacitySamples = 5)
        buffer.write(shortArrayOf(1, 2, 3, 4, 5, 6, 7))

        // capacity 5, wrote 7 -> oldest two (1, 2) fell off
        assertEquals(5, buffer.samplesAvailable)
        assertArrayEquals(shortArrayOf(3, 4, 5, 6, 7), buffer.snapshot())
    }

    @Test
    fun `write in multiple chunks smaller than capacity still orders correctly across wraps`() {
        val buffer = AudioRingBuffer(capacitySamples = 4)
        buffer.write(shortArrayOf(1, 2))
        buffer.write(shortArrayOf(3, 4))
        buffer.write(shortArrayOf(5, 6))

        assertArrayEquals(shortArrayOf(3, 4, 5, 6), buffer.snapshot())
    }

    @Test
    fun `snapshotLast returns fewer samples than requested when not enough buffered yet`() {
        val buffer = AudioRingBuffer(capacitySamples = 100)
        buffer.write(shortArrayOf(1, 2, 3))

        assertArrayEquals(shortArrayOf(1, 2, 3), buffer.snapshotLast(10))
    }

    @Test
    fun `snapshotLast trims to the most recent n samples after wrap`() {
        val buffer = AudioRingBuffer(capacitySamples = 5)
        buffer.write(shortArrayOf(1, 2, 3, 4, 5, 6, 7))

        assertArrayEquals(shortArrayOf(6, 7), buffer.snapshotLast(2))
    }

    @Test
    fun `clear resets buffer to empty`() {
        val buffer = AudioRingBuffer(capacitySamples = 5)
        buffer.write(shortArrayOf(1, 2, 3))
        buffer.clear()

        assertEquals(0, buffer.samplesAvailable)
        assertArrayEquals(ShortArray(0), buffer.snapshot())
    }

    @Test
    fun `write offset and length are respected`() {
        val buffer = AudioRingBuffer(capacitySamples = 10)
        val source = shortArrayOf(9, 9, 1, 2, 3, 9, 9)
        buffer.write(source, offset = 2, length = 3)

        assertArrayEquals(shortArrayOf(1, 2, 3), buffer.snapshot())
    }
}
