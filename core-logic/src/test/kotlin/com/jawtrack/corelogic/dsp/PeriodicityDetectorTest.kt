package com.jawtrack.corelogic.dsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class PeriodicityDetectorTest {

    @Test
    fun `a snore-like 4s-period envelope scores highly periodic`() {
        val sampleIntervalMs = 200L
        val periodMs = 4_000.0
        // 60s of envelope, well past several full cycles
        val envelope = DoubleArray(300) { i ->
            sin(2.0 * PI * (i * sampleIntervalMs) / periodMs)
        }

        val score = PeriodicityDetector.strongestPeriodicity(envelope, sampleIntervalMs)

        assertTrue(score > 0.8, "expected strong periodicity for a clean 4s-period signal, got $score")
    }

    @Test
    fun `random noise envelope scores low periodicity`() {
        val random = Random(7)
        val envelope = DoubleArray(300) { random.nextDouble(-1.0, 1.0) }

        val score = PeriodicityDetector.strongestPeriodicity(envelope, sampleIntervalMillis = 200)

        assertTrue(score < 0.3, "expected low periodicity for noise, got $score")
    }

    @Test
    fun `a grinding-like short aperiodic burst scores low periodicity`() {
        // a handful of energy spikes with no regular spacing, embedded in silence
        val envelope = DoubleArray(300)
        listOf(10, 45, 46, 120, 121, 122, 200).forEach { envelope[it] = 1.0 }

        val score = PeriodicityDetector.strongestPeriodicity(envelope, sampleIntervalMillis = 200)

        assertTrue(score < 0.5, "expected low periodicity for an aperiodic burst pattern, got $score")
    }

    @Test
    fun `constant envelope has no variance and returns 0 rather than NaN`() {
        val envelope = DoubleArray(300) { -40.0 }

        val score = PeriodicityDetector.strongestPeriodicity(envelope, sampleIntervalMillis = 200)

        assertEquals(0.0, score)
    }

    @Test
    fun `too little data to cover the search window returns 0`() {
        val envelope = DoubleArray(5) { it.toDouble() }

        val score = PeriodicityDetector.strongestPeriodicity(envelope, sampleIntervalMillis = 200)

        assertEquals(0.0, score)
    }

    @Test
    fun `rejects an inverted min max period range`() {
        assertThrows<IllegalArgumentException> {
            PeriodicityDetector.strongestPeriodicity(
                DoubleArray(300) { it.toDouble() },
                sampleIntervalMillis = 200,
                minPeriodMillis = 6_000,
                maxPeriodMillis = 3_000
            )
        }
    }
}
