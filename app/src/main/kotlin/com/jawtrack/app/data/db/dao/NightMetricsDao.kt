package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jawtrack.app.data.db.entities.NightMetrics

@Dao
interface NightMetricsDao {
    @Insert
    suspend fun insert(metrics: NightMetrics)

    @Query("SELECT * FROM night_metrics WHERE sessionId = :sessionId")
    suspend fun getForSession(sessionId: Long): NightMetrics?
}
