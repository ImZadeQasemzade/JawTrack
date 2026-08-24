package com.jawtrack.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UserLabel { CONFIRMED, REJECTED, UNSURE }

/** A detected candidate grinding episode (§7). Populated starting Phase 3. */
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val onsetAt: Long,
    val offsetAt: Long,
    val durationMs: Long,
    val peakScore: Float,
    val meanScore: Float,
    val peakDb: Float,
    val dominantBandHz: Float? = null,
    val classifierVersion: String,
    /** Joined in by EnrichmentWorker (§5.3), null until the morning sync runs. */
    val sleepStage: String? = null,
    /** Night/cluster-level HR context (§5.1) — not per-episode beat data. Null until enriched. */
    val hrContextJson: String? = null,
    val userLabel: UserLabel? = null,
    /** Gate 2 classes seen during this window (§4.8) — diagnostic, e.g. for false-positive review. */
    val rejectedClasses: List<String> = emptyList()
)
