package com.jawtrack.corelogic.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimelineLayoutTest {

    private val nightStart = 0L
    private val nightEnd = 8 * 60 * 60 * 1000L // 8 hours

    @Test
    fun `night start and end map to 0 and 1`() {
        assertEquals(0.0, TimelineLayout.fractionOf(nightStart, nightStart, nightEnd), 0.0001)
        assertEquals(1.0, TimelineLayout.fractionOf(nightEnd, nightStart, nightEnd), 0.0001)
    }

    @Test
    fun `midpoint maps to 0_5`() {
        val midpoint = nightStart + (nightEnd - nightStart) / 2
        assertEquals(0.5, TimelineLayout.fractionOf(midpoint, nightStart, nightEnd), 0.0001)
    }

    @Test
    fun `timestamps outside the window clamp to the nearer edge`() {
        assertEquals(0.0, TimelineLayout.fractionOf(-1000, nightStart, nightEnd), 0.0001)
        assertEquals(1.0, TimelineLayout.fractionOf(nightEnd + 1000, nightStart, nightEnd), 0.0001)
    }

    @Test
    fun `a zero-duration night does not crash or divide by zero`() {
        assertEquals(0.0, TimelineLayout.fractionOf(500, 1000, 1000), 0.0001)
    }

    @Test
    fun `fractionRangeOf for an interval fully inside the window`() {
        val quarter = nightEnd / 4
        val half = nightEnd / 2
        val range = TimelineLayout.fractionRangeOf(quarter, half, nightStart, nightEnd)

        assertEquals(0.25, range.startFraction, 0.0001)
        assertEquals(0.5, range.endFraction, 0.0001)
    }

    @Test
    fun `fractionRangeOf for an interval straddling the start of the window clamps only the start`() {
        val range = TimelineLayout.fractionRangeOf(-1000, nightEnd / 4, nightStart, nightEnd)

        assertEquals(0.0, range.startFraction, 0.0001)
        assertEquals(0.25, range.endFraction, 0.0001)
    }

    @Test
    fun `fractionRangeOf for an interval entirely outside the window collapses to a zero-width range`() {
        val range = TimelineLayout.fractionRangeOf(nightEnd + 1000, nightEnd + 2000, nightStart, nightEnd)

        assertEquals(1.0, range.startFraction, 0.0001)
        assertEquals(1.0, range.endFraction, 0.0001)
    }
}
