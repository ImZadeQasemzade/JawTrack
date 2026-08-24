package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jawtrack.app.data.db.entities.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): Session?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun getMostRecent(): Session?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT 1")
    fun observeMostRecent(): Flow<Session?>

    @Query("UPDATE sessions SET lastHeartbeatAt = :timestamp WHERE id = :sessionId")
    suspend fun updateHeartbeat(sessionId: Long, timestamp: Long)

    @Query(
        "UPDATE sessions SET endedAt = :endedAt, state = :state, cleanShutdown = :cleanShutdown, " +
            "coveragePct = :coveragePct, gapSeconds = :gapSeconds WHERE id = :sessionId"
    )
    suspend fun markEnded(
        sessionId: Long,
        endedAt: Long,
        state: com.jawtrack.app.data.db.entities.SessionState,
        cleanShutdown: Boolean,
        coveragePct: Double?,
        gapSeconds: Long?
    )
}
