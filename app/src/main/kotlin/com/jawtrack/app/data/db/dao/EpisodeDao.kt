package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jawtrack.app.data.db.entities.Episode
import com.jawtrack.app.data.db.entities.UserLabel

@Dao
interface EpisodeDao {

    @Insert
    suspend fun insert(episode: Episode): Long

    @Update
    suspend fun update(episode: Episode)

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getById(id: Long): Episode?

    @Query("SELECT * FROM episodes WHERE sessionId = :sessionId ORDER BY onsetAt ASC")
    suspend fun getForSession(sessionId: Long): List<Episode>

    @Query("SELECT * FROM episodes WHERE userLabel IS NULL AND sessionId = :sessionId ORDER BY onsetAt ASC")
    suspend fun getUnlabeledForSession(sessionId: Long): List<Episode>

    /** Set by EnrichmentWorker's sleep-stage join (§5.3 step 2). */
    @Query("UPDATE episodes SET sleepStage = :stage WHERE id = :episodeId")
    suspend fun updateSleepStage(episodeId: Long, stage: String)

    /** Set by Screen 3's labeling loop (§8). */
    @Query("UPDATE episodes SET userLabel = :label WHERE id = :episodeId")
    suspend fun updateUserLabel(episodeId: Long, label: UserLabel)
}
