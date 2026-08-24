package com.jawtrack.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SessionState {
    RECORDING,
    STOPPED_CLEAN,
    STOPPED_ERROR,
    CALIBRATION
}

/** Health Connect enrichment status (§5.3) — independent of [SessionState]: a session can be
 * STOPPED_CLEAN and still PENDING enrichment while waiting on the watch to sync. */
enum class EnrichmentState {
    PENDING,
    /** Enriched, but Garmin hadn't synced sleep/HR data for this window yet — report shows a "waiting on sync" banner. */
    PARTIAL,
    ENRICHED
}

/** One overnight recording session (JawTrackSpec §7). */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val state: SessionState,
    val roomProfileId: Long? = null,
    /** "UNPROCESSED" or "MIC" — logged because detection thresholds aren't comparable across sources (§4.1). */
    val audioSourceUsed: String? = null,
    val coveragePct: Double? = null,
    val gapSeconds: Long? = null,
    val lastHeartbeatAt: Long? = null,
    val cleanShutdown: Boolean = false,
    val calibrationOnly: Boolean = false,
    /** Counter only — the flagged buffer itself is destroyed immediately and never persisted (§6.4). */
    val speechRejectedCount: Int = 0,
    /** Feeds the night's snore index (§7.1) — falls out of Gate 2 rejections for free. */
    val snoringRejectedCount: Int = 0,
    val enrichmentState: EnrichmentState = EnrichmentState.PENDING
)
