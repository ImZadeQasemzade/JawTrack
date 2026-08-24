package com.jawtrack.corelogic.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnergyGateTest {

    @Test
    fun `stays closed while below threshold`() {
        val gate = EnergyGate(thresholdDb = -30.0)

        assertFalse(gate.evaluate(0, rmsDb = -40.0))
        assertFalse(gate.evaluate(1000, rmsDb = -35.0))
    }

    @Test
    fun `opens only after sustaining above threshold for the minimum duration`() {
        val gate = EnergyGate(thresholdDb = -30.0, minSustainMillis = 200)

        assertFalse(gate.evaluate(0, rmsDb = -10.0))    // just crossed, 0ms sustained
        assertFalse(gate.evaluate(100, rmsDb = -10.0))  // 100ms sustained, not enough yet
        assertTrue(gate.evaluate(200, rmsDb = -10.0))   // 200ms sustained, opens
        assertTrue(gate.evaluate(500, rmsDb = -10.0))   // stays open while still above
    }

    @Test
    fun `a dip back below threshold resets the sustain timer`() {
        val gate = EnergyGate(thresholdDb = -30.0, minSustainMillis = 200)

        assertFalse(gate.evaluate(0, rmsDb = -10.0))
        assertFalse(gate.evaluate(150, rmsDb = -10.0))
        assertFalse(gate.evaluate(160, rmsDb = -40.0)) // dips below — resets
        assertFalse(gate.evaluate(300, rmsDb = -10.0)) // only 0ms into a new run
        assertTrue(gate.evaluate(500, rmsDb = -10.0))  // 200ms into the new run — reopens
    }

    @Test
    fun `exactly at threshold does not count as above`() {
        val gate = EnergyGate(thresholdDb = -30.0)
        assertFalse(gate.evaluate(0, rmsDb = -30.0))
    }

    @Test
    fun `reset clears an in-progress sustain window`() {
        val gate = EnergyGate(thresholdDb = -30.0, minSustainMillis = 200)
        gate.evaluate(0, rmsDb = -10.0)
        gate.evaluate(150, rmsDb = -10.0)

        gate.reset()

        assertFalse(gate.evaluate(151, rmsDb = -10.0)) // treated as a fresh crossing
        assertTrue(gate.evaluate(351, rmsDb = -10.0))
    }

    @Test
    fun `sustainedMillisAsOf reports elapsed time in the current run, 0 when closed`() {
        val gate = EnergyGate(thresholdDb = -30.0, minSustainMillis = 200)

        assertEquals(0L, gate.sustainedMillisAsOf(0)) // never crossed yet

        gate.evaluate(0, rmsDb = -10.0)
        assertEquals(0L, gate.sustainedMillisAsOf(0))
        assertEquals(300L, gate.sustainedMillisAsOf(300))

        gate.evaluate(300, rmsDb = -40.0) // drops below -- resets
        assertEquals(0L, gate.sustainedMillisAsOf(300))
    }

    @Test
    fun `forRoomProfile derives its threshold from the calibration floor plus margin`() {
        // floor -50dB + 8dB margin = -42dB threshold
        val belowThreshold = EnergyGate.forRoomProfile(mapOf(1000.0 to -50.0), marginDb = 8.0)
        assertFalse(belowThreshold.evaluate(0, rmsDb = -45.0)) // under floor+8

        val aboveThreshold = EnergyGate.forRoomProfile(mapOf(1000.0 to -50.0), marginDb = 8.0)
        assertFalse(aboveThreshold.evaluate(0, rmsDb = -30.0))   // just crossed
        assertTrue(aboveThreshold.evaluate(200, rmsDb = -30.0))  // sustained past threshold
    }
}
