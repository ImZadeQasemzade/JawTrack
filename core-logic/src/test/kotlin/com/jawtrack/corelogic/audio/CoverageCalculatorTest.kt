package com.jawtrack.corelogic.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoverageCalculatorTest {

    private val nightStart = 0L
    private val nightEnd = 8 * 60 * 60 * 1000L // 8 hours

    @Test
    fun `no gaps means 100 percent coverage`() {
        val result = CoverageCalculator.compute(nightStart, nightEnd, emptyList())

        assertEquals(100.0, result.coveragePct, 0.001)
        assertEquals(0L, result.totalGapSeconds)
    }

    @Test
    fun `one gap reduces coverage proportionally`() {
        val oneHourMillis = 60 * 60 * 1000L
        val gaps = listOf(GapInterval(startAtMillis = oneHourMillis, endAtMillis = 2 * oneHourMillis))

        val result = CoverageCalculator.compute(nightStart, nightEnd, gaps)

        assertEquals(87.5, result.coveragePct, 0.001) // 7/8 hours covered
        assertEquals(3600L, result.totalGapSeconds)
    }

    @Test
    fun `overlapping gaps are not double counted`() {
        val gaps = listOf(
            GapInterval(0, 1000),
            GapInterval(500, 1500)
        )

        val result = CoverageCalculator.compute(0, 2000, gaps)

        // naive sum would be 2000ms of gap over a 2000ms night (0%); actual union is 1500ms -> 25%
        assertEquals(25.0, result.coveragePct, 0.001)
    }

    @Test
    fun `gaps extending outside the session window are clamped`() {
        val gaps = listOf(GapInterval(startAtMillis = -1000, endAtMillis = nightEnd + 1000))

        val result = CoverageCalculator.compute(nightStart, nightEnd, gaps)

        assertEquals(0.0, result.coveragePct, 0.001)
    }

    @Test
    fun `zero duration session reports zero coverage rather than dividing by zero`() {
        val result = CoverageCalculator.compute(1000, 1000, emptyList())

        assertEquals(0.0, result.coveragePct, 0.001)
    }
}
