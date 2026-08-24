package com.jawtrack.corelogic.watchdog

/**
 * Result of checking whether a recording session was silently killed (JawTrackSpec §4.6.2):
 * the service writes a heartbeat timestamp every 60 s; if the app relaunches and finds a
 * session that never called its clean-shutdown path, and the last heartbeat trails off well
 * before "now", that's the signature of an OEM battery killer rather than a normal stop.
 */
data class HeartbeatEvaluation(
    val likelySilentDeath: Boolean,
    /** Best estimate of when recording actually stopped, if [likelySilentDeath] is true. */
    val apparentStopAtMillis: Long?,
    val gapSinceLastHeartbeatMillis: Long
)

object HeartbeatEvaluator {

    const val DEFAULT_HEARTBEAT_INTERVAL_MILLIS: Long = 60_000

    /**
     * @param missedBeatsThreshold how many consecutive missed 60 s beats before we call it
     *   a silent death rather than, e.g., a brief scheduling hiccup. Spec's own watchdog
     *   description implies "trailed off early" — a couple of missed beats is conclusive
     *   for a foreground service that should be rock-steady while awake.
     */
    fun evaluate(
        cleanShutdown: Boolean,
        lastHeartbeatAtMillis: Long?,
        referenceNowMillis: Long,
        heartbeatIntervalMillis: Long = DEFAULT_HEARTBEAT_INTERVAL_MILLIS,
        missedBeatsThreshold: Int = 2
    ): HeartbeatEvaluation {
        if (cleanShutdown || lastHeartbeatAtMillis == null) {
            return HeartbeatEvaluation(
                likelySilentDeath = false,
                apparentStopAtMillis = null,
                gapSinceLastHeartbeatMillis = 0
            )
        }

        val gap = referenceNowMillis - lastHeartbeatAtMillis
        val threshold = heartbeatIntervalMillis * missedBeatsThreshold
        val silentDeath = gap > threshold

        return HeartbeatEvaluation(
            likelySilentDeath = silentDeath,
            apparentStopAtMillis = if (silentDeath) lastHeartbeatAtMillis else null,
            gapSinceLastHeartbeatMillis = gap
        )
    }
}
