package com.jawtrack.corelogic.enrichment

/**
 * A/B/C confidence grade for a night's JawTrack Index (§7.1): "A noisy night with 60% coverage
 * must not present its number with the same authority as a clean one... this is important and
 * often skipped — build it." The spec names five inputs (coverage %, room noise floor,
 * speech-rejection count, proportion of episodes labeled, clean shutdown) but not exact
 * weights or thresholds — what follows is a documented, defensible interpretation of those
 * five factors, not a value taken from the spec itself. Tune once real nights exist to compare
 * grades against (§10).
 */
object ConfidenceGrader {

    data class Inputs(
        val coveragePct: Double,
        val cleanShutdown: Boolean,
        val speechRejectedCount: Int,
        val sessionDurationHours: Double,
        val episodeCount: Int,
        val labeledEpisodeCount: Int,
        /** Broadband floor from calibration (`RoomProfileMath.broadbandFloorDb`); null if never calibrated. */
        val roomNoiseFloorDb: Double?
    )

    fun grade(inputs: Inputs): String = when {
        score(inputs) >= GRADE_A_THRESHOLD -> "A"
        score(inputs) >= GRADE_B_THRESHOLD -> "B"
        else -> "C"
    }

    /** 0–100 composite; exposed separately from [grade] mainly so tests can pin exact values, not just the letter bucket. */
    fun score(inputs: Inputs): Double {
        val coverageScore = inputs.coveragePct.coerceIn(0.0, 100.0)
        val cleanShutdownScore = if (inputs.cleanShutdown) 100.0 else 0.0
        val speechScore = speechRejectionScore(inputs.speechRejectedCount, inputs.sessionDurationHours)
        val labeledScore = labeledProportionScore(inputs.episodeCount, inputs.labeledEpisodeCount)
        val noiseFloorScore = noiseFloorScore(inputs.roomNoiseFloorDb)

        return coverageScore * WEIGHT_COVERAGE +
            cleanShutdownScore * WEIGHT_CLEAN_SHUTDOWN +
            speechScore * WEIGHT_SPEECH_REJECTION +
            labeledScore * WEIGHT_LABELED_PROPORTION +
            noiseFloorScore * WEIGHT_NOISE_FLOOR
    }

    private fun speechRejectionScore(speechRejectedCount: Int, sessionDurationHours: Double): Double {
        if (sessionDurationHours <= 0) return 100.0
        val perHour = speechRejectedCount / sessionDurationHours
        return (100.0 - (perHour / MAX_SPEECH_REJECTIONS_PER_HOUR) * 100.0).coerceIn(0.0, 100.0)
    }

    private fun labeledProportionScore(episodeCount: Int, labeledEpisodeCount: Int): Double {
        // Nothing to label yet on a quiet (or freshly-recorded) night shouldn't be penalized.
        if (episodeCount == 0) return 100.0
        return (labeledEpisodeCount.toDouble() / episodeCount * 100.0).coerceIn(0.0, 100.0)
    }

    private fun noiseFloorScore(roomNoiseFloorDb: Double?): Double {
        // Never calibrated is worse than a known-quiet room but not as bad as a known-noisy one.
        val floor = roomNoiseFloorDb ?: return 50.0
        val range = NOISY_FLOOR_DB - QUIET_FLOOR_DB
        return ((NOISY_FLOOR_DB - floor) / range * 100.0).coerceIn(0.0, 100.0)
    }

    private const val WEIGHT_COVERAGE = 0.35
    private const val WEIGHT_CLEAN_SHUTDOWN = 0.25
    private const val WEIGHT_SPEECH_REJECTION = 0.15
    private const val WEIGHT_LABELED_PROPORTION = 0.10
    private const val WEIGHT_NOISE_FLOOR = 0.15

    private const val MAX_SPEECH_REJECTIONS_PER_HOUR = 20.0
    private const val QUIET_FLOOR_DB = -55.0
    private const val NOISY_FLOOR_DB = -30.0

    private const val GRADE_A_THRESHOLD = 85.0
    private const val GRADE_B_THRESHOLD = 60.0
}
