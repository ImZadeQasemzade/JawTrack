package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jawtrack.app.data.db.entities.Gap

@Dao
interface GapDao {

    @Insert
    suspend fun insert(gap: Gap): Long

    @Query("SELECT * FROM gaps WHERE sessionId = :sessionId ORDER BY startAt ASC")
    suspend fun getForSession(sessionId: Long): List<Gap>
}
