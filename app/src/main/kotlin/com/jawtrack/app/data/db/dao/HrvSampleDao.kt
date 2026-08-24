package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jawtrack.app.data.db.entities.HrvSample

@Dao
interface HrvSampleDao {
    @Insert
    suspend fun insertAll(samples: List<HrvSample>)

    @Query("SELECT * FROM hrv_samples WHERE sessionId = :sessionId ORDER BY t ASC")
    suspend fun getForSession(sessionId: Long): List<HrvSample>
}
