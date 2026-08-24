package com.jawtrack.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SessionState {
    RECORDING,
    STOPPED_CLEAN,
    STOPPED_ERROR,
    CALIBRATION
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
    val calibrationOnly: Boolean = false
)
