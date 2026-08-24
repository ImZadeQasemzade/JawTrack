package com.jawtrack.corelogic.detection

import com.jawtrack.corelogic.calibration.RoomProfileMath

/**
 * Gate 1 (JawTrackSpec §4.7): frame RMS must exceed `noiseFloor + 8 dB` continuously for at
 * least 200 ms before anything downstream runs. 95%+ of night frames are silence, so this is
 * the gate that has to be cheap — a debounce timer over a threshold comparison, nothing more.
 * Stateful and streaming: feed it frames in timestamp order as the reader thread produces them.
 */
class EnergyGate(
    private val thresholdDb: Double,
    private val minSustainMillis: Long = DEFAULT_MIN_SUSTAIN_MILLIS
) {
    private var aboveSinceMillis: Long? = null

    /** @return true once the signal has stayed above threshold for at least [minSustainMillis]. */
    fun evaluate(timestampMillis: Long, rmsDb: Double): Boolean {
        if (rmsDb <= thresholdDb) {
            aboveSinceMillis = null
            return false
        }

        val since = aboveSinceMillis ?: timestampMillis.also { aboveSinceMillis = it }
        return timestampMillis - since >= minSustainMillis
    }

    fun reset() {
        aboveSinceMillis = null
    }

    /** How long the signal has been continuously above threshold, as of [timestampMillis]; 0 if currently below. */
    fun sustainedMillisAsOf(timestampMillis: Long): Long =
        aboveSinceMillis?.let { since -> timestampMillis - since } ?: 0L

    companion object {
        const val DEFAULT_MIN_SUSTAIN_MILLIS = 200L
        const val DEFAULT_MARGIN_DB = 8.0

        /** Builds a gate whose threshold is derived from a calibration night's RoomProfile (§4.5). */
        fun forRoomProfile(
            bandFloorsDb: Map<Double, Double>,
            marginDb: Double = DEFAULT_MARGIN_DB,
            minSustainMillis: Long = DEFAULT_MIN_SUSTAIN_MILLIS
        ): EnergyGate = EnergyGate(RoomProfileMath.broadbandFloorDb(bandFloorsDb) + marginDb, minSustainMillis)
    }
}
