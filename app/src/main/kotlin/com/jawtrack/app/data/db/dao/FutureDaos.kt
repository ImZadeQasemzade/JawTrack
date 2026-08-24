package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jawtrack.app.data.db.entities.DailyFactors
import com.jawtrack.app.data.db.entities.HeartRateSample
import com.jawtrack.app.data.db.entities.HrvSample
import com.jawtrack.app.data.db.entities.Label
import com.jawtrack.app.data.db.entities.MorningCheckin
import com.jawtrack.app.data.db.entities.NightMetrics
import com.jawtrack.app.data.db.entities.RoomProfile
import com.jawtrack.app.data.db.entities.SleepStage

/**
 * Minimal CRUD for entities not yet wired into any running pipeline (§7 defines their
 * schema now so it doesn't need migrating later; each becomes active in the phase noted
 * on its entity). Grouped here rather than one-file-each while they're this small.
 */

@Dao
interface HeartRateSampleDao { // Phase 5
    @Insert suspend fun insertAll(samples: List<HeartRateSample>)
    @Query("SELECT * FROM heart_rate_samples WHERE sessionId = :sessionId ORDER BY t ASC") suspend fun getForSession(sessionId: Long): List<HeartRateSample>
}

@Dao
interface HrvSampleDao { // Phase 5
    @Insert suspend fun insertAll(samples: List<HrvSample>)
    @Query("SELECT * FROM hrv_samples WHERE sessionId = :sessionId ORDER BY t ASC") suspend fun getForSession(sessionId: Long): List<HrvSample>
}

@Dao
interface SleepStageDao { // Phase 5
    @Insert suspend fun insertAll(stages: List<SleepStage>)
    @Query("SELECT * FROM sleep_stages WHERE sessionId = :sessionId ORDER BY startAt ASC") suspend fun getForSession(sessionId: Long): List<SleepStage>
}

@Dao
interface NightMetricsDao { // Phase 5
    @Insert suspend fun insert(metrics: NightMetrics)
    @Query("SELECT * FROM night_metrics WHERE sessionId = :sessionId") suspend fun getForSession(sessionId: Long): NightMetrics?
}

@Dao
interface DailyFactorsDao { // Phase 7
    @Insert suspend fun upsert(factors: DailyFactors)
    @Query("SELECT * FROM daily_factors WHERE date = :date") suspend fun getForDate(date: String): DailyFactors?
    @Query("SELECT * FROM daily_factors ORDER BY date DESC") suspend fun getAll(): List<DailyFactors>
}

@Dao
interface MorningCheckinDao { // Phase 6
    @Insert suspend fun upsert(checkin: MorningCheckin)
    @Query("SELECT * FROM morning_checkins WHERE date = :date") suspend fun getForDate(date: String): MorningCheckin?
    @Query("SELECT * FROM morning_checkins ORDER BY date DESC") suspend fun getAll(): List<MorningCheckin>
}

@Dao
interface LabelDao { // Phase 6, feeds Phase 9 retraining export
    @Insert suspend fun insert(label: Label): Long
    @Query("SELECT * FROM labels ORDER BY labeledAt ASC") suspend fun getAll(): List<Label>
}

@Dao
interface RoomProfileDao { // Phase 2
    @Insert suspend fun insert(profile: RoomProfile): Long
    @Query("SELECT * FROM room_profiles ORDER BY createdAt DESC LIMIT 1") suspend fun getMostRecent(): RoomProfile?
}
