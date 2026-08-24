package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jawtrack.app.data.db.entities.DailyFactors
import com.jawtrack.app.data.db.entities.Label
import com.jawtrack.app.data.db.entities.MorningCheckin

/**
 * Minimal CRUD for entities not yet wired into any running pipeline (§7 defines their
 * schema now so it doesn't need migrating later; each becomes active in the phase noted
 * on its entity). Grouped here rather than one-file-each while they're this small.
 */

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
