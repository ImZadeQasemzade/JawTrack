package com.jawtrack.corelogic.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IndexTrendTest {

    @Test
    fun `no history yields a null rolling average and a null delta`() {
        assertNull(IndexTrend.rollingAverage(emptyList()))
        assertNull(IndexTrend.delta(tonightIndex = 5.0, rollingAverage = null))
    }

    @Test
    fun `rolling average uses only the most recent windowSize nights`() {
        val history = listOf(100.0, 100.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0) // 9 nights, only last 7 should count

        val average = IndexTrend.rollingAverage(history, windowSize = 7)

        assertEquals((1.0 + 2.0 + 3.0 + 4.0 + 5.0 + 6.0 + 7.0) / 7.0, average!!, 0.0001)
    }

    @Test
    fun `fewer nights than the window size averages what exists`() {
        val average = IndexTrend.rollingAverage(listOf(2.0, 4.0), windowSize = 7)
        assertEquals(3.0, average!!, 0.0001)
    }

    @Test
    fun `delta is positive when tonight is worse than average, negative when better`() {
        assertEquals(2.0, IndexTrend.delta(tonightIndex = 7.0, rollingAverage = 5.0)!!, 0.0001)
        assertEquals(-2.0, IndexTrend.delta(tonightIndex = 3.0, rollingAverage = 5.0)!!, 0.0001)
    }
}
