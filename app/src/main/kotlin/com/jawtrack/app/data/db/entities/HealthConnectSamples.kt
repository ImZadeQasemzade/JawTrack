package com.jawtrack.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Heart rate sample pulled from Health Connect (§5, §7). Populated starting Phase 5. */
@Entity(
    tableName = "heart_rate_samples",
    foreignKeys = [
        ForeignKey(entity = Session::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("sessionId")]
)
data class HeartRateSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val t: Long,
    val bpm: Int,
    val source: String
)

/** HRV (RMSSD) sample from Health Connect. Populated starting Phase 5. */
@Entity(
    tableName = "hrv_samples",
    foreignKeys = [
        ForeignKey(entity = Session::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("sessionId")]
)
data class HrvSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val t: Long,
    val rmssd: Double
)

/** Sleep stage interval from Health Connect's SleepSessionRecord. Populated starting Phase 5. */
@Entity(
    tableName = "sleep_stages",
    foreignKeys = [
        ForeignKey(entity = Session::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("sessionId")]
)
data class SleepStage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startAt: Long,
    val endAt: Long,
    val stage: String
)
