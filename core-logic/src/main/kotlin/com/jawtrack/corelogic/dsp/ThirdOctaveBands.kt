package com.jawtrack.corelogic.dsp

import kotlin.math.pow

/**
 * Standard ISO preferred third-octave band center frequencies, 25 Hz–8000 Hz (the Nyquist
 * limit at JawTrack's 16 kHz capture rate). JawTrackSpec §4.5 stores the room's noise floor
 * per band; §4.7's grinding heuristics reference specific bands within this range (1–6 kHz).
 */
object ThirdOctaveBands {

    private const val BAND_RATIO = 1.0 / 6.0 // half-width of a third-octave band, in octaves

    val CENTER_FREQUENCIES_HZ: List<Double> = listOf(
        25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0,
        250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0,
        2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0
    )

    /** [lowHz, highHz) edges for a band centered at [centerHz]. */
    fun edgesHz(centerHz: Double): ClosedFloatingPointRange<Double> {
        val factor = 2.0.pow(BAND_RATIO)
        return (centerHz / factor)..(centerHz * factor)
    }
}
