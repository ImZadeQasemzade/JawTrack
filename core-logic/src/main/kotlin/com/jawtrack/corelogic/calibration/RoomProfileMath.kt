package com.jawtrack.corelogic.calibration

import kotlin.math.log10
import kotlin.math.pow

/**
 * Bridges the per-band `RoomProfile` calibration produces (§4.5) to the single broadband
 * scalar Gate 1's energy check needs (§4.7: `noiseFloor + 8 dB`) — Gate 1 runs on raw frame
 * RMS in the hot capture loop, not a per-band FFT, so it needs one number, not a map.
 */
object RoomProfileMath {

    /** Reconstructs an overall broadband floor by summing each band's power and converting back to dB. */
    fun broadbandFloorDb(bandFloorsDb: Map<Double, Double>): Double {
        require(bandFloorsDb.isNotEmpty()) { "cannot derive a broadband floor from an empty RoomProfile" }

        val totalPower = bandFloorsDb.values.sumOf { db -> 10.0.pow(db / 10.0) }
        return 10.0 * log10(totalPower)
    }
}
