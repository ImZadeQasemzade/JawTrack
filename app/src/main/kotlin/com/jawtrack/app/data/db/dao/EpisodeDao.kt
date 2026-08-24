package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jawtrack.app.data.db.entities.Episode

/** Unused until Phase 3 (episode assembly) writes its first row. */
@Dao
interface EpisodeDao {

    @Insert
    suspend fun insert(episode: Episode): Long

    @Update
    suspend fun update(episode: Episode)

    @Query("SELECT * FROM episodes WHERE sessionId = :sessionId ORDER BY onsetAt ASC")
    suspend fun getForSession(sessionId: Long): List<Episode>

    @Query("SELECT * FROM episodes WHERE userLabel IS NULL AND sessionId = :sessionId ORDER BY onsetAt ASC")
    suspend fun getUnlabeledForSession(sessionId: Long): List<Episode>
}
