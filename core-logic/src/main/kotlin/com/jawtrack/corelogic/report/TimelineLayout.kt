package com.jawtrack.corelogic.report

/**
 * Maps night-window timestamps to normalized [0, 1] horizontal positions for Screen 2's Canvas
 * timeline (§8: sleep-stage band, HR line, episode ticks, gap hatching all share this same
 * x-axis). Pure coordinate math — the Canvas drawing itself is Android-only and unverified,
 * but getting the positions right is what actually matters and is fully testable here.
 */
object TimelineLayout {

    /** How far through [nightStartMillis, nightEndMillis] [timestampMillis] falls, clamped to [0, 1]. */
    fun fractionOf(timestampMillis: Long, nightStartMillis: Long, nightEndMillis: Long): Double {
        val duration = nightEndMillis - nightStartMillis
        if (duration <= 0) return 0.0
        return ((timestampMillis - nightStartMillis).toDouble() / duration).coerceIn(0.0, 1.0)
    }

    data class FractionRange(val startFraction: Double, val endFraction: Double)

    /** Fraction range for an interval. One entirely outside the night window collapses to a zero-width range at the nearer edge, rather than being omitted — callers can skip zero-width ranges themselves if they'd rather not draw them. */
    fun fractionRangeOf(
        intervalStartMillis: Long,
        intervalEndMillis: Long,
        nightStartMillis: Long,
        nightEndMillis: Long
    ): FractionRange = FractionRange(
        startFraction = fractionOf(intervalStartMillis, nightStartMillis, nightEndMillis),
        endFraction = fractionOf(intervalEndMillis, nightStartMillis, nightEndMillis)
    )
}
