package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jawtrack.app.data.db.entities.SleepStage

@Dao
interface SleepStageDao {
    @Insert
    suspend fun insertAll(stages: List<SleepStage>)

    @Query("SELECT * FROM sleep_stages WHERE sessionId = :sessionId ORDER BY startAt ASC")
    suspend fun getForSession(sessionId: Long): List<SleepStage>
}
