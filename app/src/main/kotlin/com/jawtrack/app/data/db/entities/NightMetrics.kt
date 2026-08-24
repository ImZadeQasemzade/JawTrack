package com.jawtrack.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Night-level rollup (§7.1). One row per session, computed by EnrichmentWorker starting Phase 5. */
@Entity(
    tableName = "night_metrics",
    foreignKeys = [
        ForeignKey(entity = Session::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class NightMetrics(
    @PrimaryKey val sessionId: Long,
    val episodeCount: Int,
    /** The JawTrack Index — episodes/hour of sleep. Never labeled with clinical severity terms. */
    val episodesPerHour: Double,
    val totalGrindingSeconds: Long,
    val longestEpisodeMs: Long,
    val stageDistributionJson: String,
    val snoreIndex: Double,
    /** A/B/C — reflects coverage, noise floor, speech-rejection count, label proportion, clean shutdown. */
    val confidenceGrade: String
)
