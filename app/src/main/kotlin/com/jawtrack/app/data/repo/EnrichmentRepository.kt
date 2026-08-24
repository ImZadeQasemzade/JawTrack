package com.jawtrack.app.data.repo

import com.jawtrack.app.data.db.JawTrackDatabase
import com.jawtrack.app.data.db.entities.EnrichmentState
import com.jawtrack.app.data.db.entities.Episode
import com.jawtrack.app.data.db.entities.HeartRateSample
import com.jawtrack.app.data.db.entities.HrvSample
import com.jawtrack.app.data.db.entities.NightMetrics
import com.jawtrack.app.data.db.entities.RoomProfile
import com.jawtrack.app.data.db.entities.Session
import com.jawtrack.app.data.db.entities.SleepStage
import com.jawtrack.corelogic.enrichment.SleepStageInterval
import com.jawtrack.corelogic.enrichment.StageDistributionJson
import com.jawtrack.corelogic.enrichment.NightMetrics as ComputedNightMetrics

/** Read/write access `EnrichmentWorker` needs (§5.3) — kept separate from `SessionRepository` since it's a distinct concern (Health Connect join, not recording). */
class EnrichmentRepository(private val db: JawTrackDatabase) {

    suspend fun getPendingEnrichment(): List<Session> = db.sessionDao().getPendingEnrichment()

    suspend fun getEpisodesForSession(sessionId: Long): List<Episode> = db.episodeDao().getForSession(sessionId)

    suspend fun getRoomProfileById(id: Long): RoomProfile? = db.roomProfileDao().getById(id)

    suspend fun saveHeartRateSamples(sessionId: Long, samples: List<Pair<Long, Int>>) {
        if (samples.isEmpty()) return
        db.heartRateSampleDao().insertAll(
            samples.map { (t, bpm) -> HeartRateSample(sessionId = sessionId, t = t, bpm = bpm, source = "health_connect") }
        )
    }

    suspend fun saveHrvSamples(sessionId: Long, samples: List<Pair<Long, Double>>) {
        if (samples.isEmpty()) return
        db.hrvSampleDao().insertAll(samples.map { (t, rmssd) -> HrvSample(sessionId = sessionId, t = t, rmssd = rmssd) })
    }

    suspend fun saveSleepStages(sessionId: Long, stages: List<SleepStageInterval>) {
        if (stages.isEmpty()) return
        db.sleepStageDao().insertAll(
            stages.map { SleepStage(sessionId = sessionId, startAt = it.startMillis, endAt = it.endMillis, stage = it.stage) }
        )
    }

    suspend fun updateEpisodeSleepStage(episodeId: Long, stage: String) {
        db.episodeDao().updateSleepStage(episodeId, stage)
    }

    suspend fun saveNightMetrics(sessionId: Long, metrics: ComputedNightMetrics, confidenceGrade: String) {
        db.nightMetricsDao().insert(
            NightMetrics(
                sessionId = sessionId,
                episodeCount = metrics.episodeCount,
                episodesPerHour = metrics.episodesPerHour,
                totalGrindingSeconds = metrics.totalGrindingSeconds,
                longestEpisodeMs = metrics.longestEpisodeMs,
                stageDistributionJson = StageDistributionJson.encode(metrics.stageDistribution),
                snoreIndex = metrics.snoreIndex,
                confidenceGrade = confidenceGrade
            )
        )
    }

    suspend fun updateEnrichmentState(sessionId: Long, state: EnrichmentState) {
        db.sessionDao().updateEnrichmentState(sessionId, state)
    }
}
