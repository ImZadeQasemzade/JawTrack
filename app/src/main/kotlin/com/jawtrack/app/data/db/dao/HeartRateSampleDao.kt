package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jawtrack.app.data.db.entities.HeartRateSample

@Dao
interface HeartRateSampleDao {
    @Insert
    suspend fun insertAll(samples: List<HeartRateSample>)

    @Query("SELECT * FROM heart_rate_samples WHERE sessionId = :sessionId ORDER BY t ASC")
    suspend fun getForSession(sessionId: Long): List<HeartRateSample>
}
