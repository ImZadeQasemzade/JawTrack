package com.jawtrack.corelogic.calibration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RoomProfileMathTest {

    @Test
    fun `a single band's floor becomes the broadband floor directly`() {
        val floor = RoomProfileMath.broadbandFloorDb(mapOf(1000.0 to -42.0))
        assertEquals(-42.0, floor, 0.001)
    }

    @Test
    fun `combining equal-level bands raises the broadband floor above any single band`() {
        val bands = mapOf(100.0 to -40.0, 1000.0 to -40.0, 5000.0 to -40.0)

        val floor = RoomProfileMath.broadbandFloorDb(bands)

        assertEquals(-40.0 + 10.0 * kotlin.math.log10(3.0), floor, 0.001)
    }

    @Test
    fun `empty profile is rejected rather than silently producing a bogus floor`() {
        assertThrows<IllegalArgumentException> {
            RoomProfileMath.broadbandFloorDb(emptyMap())
        }
    }
}
