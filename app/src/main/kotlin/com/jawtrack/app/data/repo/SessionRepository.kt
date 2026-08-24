package com.jawtrack.app.data.repo

import com.jawtrack.app.data.db.JawTrackDatabase
import com.jawtrack.app.data.db.entities.Gap
import com.jawtrack.app.data.db.entities.RoomProfile
import com.jawtrack.app.data.db.entities.Session
import com.jawtrack.app.data.db.entities.SessionState
import com.jawtrack.corelogic.audio.CoverageCalculator
import com.jawtrack.corelogic.audio.GapInterval
import com.jawtrack.corelogic.calibration.RoomProfileJson
import kotlinx.coroutines.flow.Flow

class SessionRepository(private val db: JawTrackDatabase) {

    fun observeMostRecentSession(): Flow<Session?> = db.sessionDao().observeMostRecent()

    suspend fun getMostRecentSession(): Session? = db.sessionDao().getMostRecent()

    suspend fun getSession(id: Long): Session? = db.sessionDao().getById(id)

    suspend fun startSession(startedAt: Long, audioSourceUsed: String, calibrationOnly: Boolean): Long {
        val session = Session(
            startedAt = startedAt,
            state = if (calibrationOnly) SessionState.CALIBRATION else SessionState.RECORDING,
            audioSourceUsed = audioSourceUsed,
            lastHeartbeatAt = startedAt,
            calibrationOnly = calibrationOnly
        )
        return db.sessionDao().insert(session)
    }

    suspend fun recordHeartbeat(sessionId: Long, timestamp: Long) {
        db.sessionDao().updateHeartbeat(sessionId, timestamp)
    }

    suspend fun recordGap(sessionId: Long, startAt: Long, endAt: Long, reason: String) {
        db.gapDao().insert(Gap(sessionId = sessionId, startAt = startAt, endAt = endAt, reason = reason))
    }

    suspend fun getMostRecentRoomProfile(): RoomProfile? = db.roomProfileDao().getMostRecent()

    /** Persists a calibration night's result (§4.5) and links it to the session that produced it. */
    suspend fun saveRoomProfile(sessionId: Long, bandFloorsDb: Map<Double, Double>, createdAt: Long): Long {
        val profileId = db.roomProfileDao().insert(
            RoomProfile(createdAt = createdAt, bandNoiseFloorJson = RoomProfileJson.encode(bandFloorsDb))
        )
        db.sessionDao().updateRoomProfile(sessionId, profileId)
        return profileId
    }

    /**
     * Marks the session ended, computing the honest coverage number from recorded gaps
     * (JawTrackSpec §4.3, §7.1) rather than trusting the requested duration blindly.
     */
    suspend fun endSession(sessionId: Long, startedAt: Long, endedAt: Long, cleanShutdown: Boolean) {
        val gaps = db.gapDao().getForSession(sessionId)
            .map { GapInterval(it.startAt, it.endAt) }
        val coverage = CoverageCalculator.compute(startedAt, endedAt, gaps)

        db.sessionDao().markEnded(
            sessionId = sessionId,
            endedAt = endedAt,
            state = if (cleanShutdown) SessionState.STOPPED_CLEAN else SessionState.STOPPED_ERROR,
            cleanShutdown = cleanShutdown,
            coveragePct = coverage.coveragePct,
            gapSeconds = coverage.totalGapSeconds
        )
    }
}
