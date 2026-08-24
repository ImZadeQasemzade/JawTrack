package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jawtrack.app.data.db.entities.AudioClip

@Dao
interface AudioClipDao {

    @Insert
    suspend fun insert(clip: AudioClip): Long

    @Query("SELECT * FROM audio_clips WHERE episodeId = :episodeId")
    suspend fun getForEpisode(episodeId: Long): List<AudioClip>

    @Query("SELECT * FROM audio_clips WHERE expiresAt <= :now")
    suspend fun getExpired(now: Long): List<AudioClip>

    @Query("DELETE FROM audio_clips WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Used only by "delete all audio" (§6.7) — immediate, no undo. */
    @Query("DELETE FROM audio_clips")
    suspend fun deleteAll()
}
