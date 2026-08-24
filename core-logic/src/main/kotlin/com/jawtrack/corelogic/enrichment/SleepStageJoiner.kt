package com.jawtrack.corelogic.enrichment

/** A sleep stage interval as read from Health Connect's `SleepSessionRecord.stages` (§5.1). */
data class SleepStageInterval(val startMillis: Long, val endMillis: Long, val stage: String)

/**
 * Joins episodes to sleep stages by time overlap (JawTrackSpec §5.3 step 2). Stage intervals
 * are minutes long, so this join is reliable even though per-episode heart rate isn't (§5.1's
 * "sleep-stage joining works fine — stage intervals are minutes long").
 */
object SleepStageJoiner {

    private val NON_SLEEP_STAGES = setOf("AWAKE", "OUT_OF_BED")

    /** The stage the episode overlaps most, by duration. Null if no stage interval covers it at all. */
    fun dominantStage(episodeOnsetMillis: Long, episodeOffsetMillis: Long, stages: List<SleepStageInterval>): String? {
        var best: String? = null
        var bestOverlapMillis = 0L
        for (stage in stages) {
            val overlapStart = maxOf(episodeOnsetMillis, stage.startMillis)
            val overlapEnd = minOf(episodeOffsetMillis, stage.endMillis)
            val overlap = overlapEnd - overlapStart
            if (overlap > bestOverlapMillis) {
                bestOverlapMillis = overlap
                best = stage.stage
            }
        }
        return best
    }

    /** Total time asleep (all stages except AWAKE/OUT_OF_BED), for the episodes/hour-of-sleep denominator (§7.1). */
    fun totalSleepDurationHours(stages: List<SleepStageInterval>): Double {
        val sleepMillis = stages
            .filterNot { it.stage in NON_SLEEP_STAGES }
            .sumOf { it.endMillis - it.startMillis }
        return sleepMillis / 3_600_000.0
    }
}
