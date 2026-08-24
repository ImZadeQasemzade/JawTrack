package com.jawtrack.corelogic.report

/** JawTrack Index rolling average and delta (§8 Screen 1: "Delta vs. 7-night rolling average"). */
object IndexTrend {

    const val DEFAULT_WINDOW_SIZE = 7

    /** @param previousNightsIndex prior nights' episodes/hour, oldest first, tonight excluded. Null if there's no history yet. */
    fun rollingAverage(previousNightsIndex: List<Double>, windowSize: Int = DEFAULT_WINDOW_SIZE): Double? {
        if (previousNightsIndex.isEmpty()) return null
        return previousNightsIndex.takeLast(windowSize).average()
    }

    /** Null if there's no rolling average to compare against yet (first tracked night). */
    fun delta(tonightIndex: Double, rollingAverage: Double?): Double? = rollingAverage?.let { tonightIndex - it }
}
