package com.jawtrack.app.data.repo

import com.jawtrack.app.data.db.JawTrackDatabase
import com.jawtrack.app.data.db.entities.Episode
import com.jawtrack.app.data.db.entities.Gap
import com.jawtrack.app.data.db.entities.HeartRateSample
import com.jawtrack.app.data.db.entities.NightMetrics
import com.jawtrack.app.data.db.entities.Session
import com.jawtrack.app.data.db.entities.SleepStage
import com.jawtrack.corelogic.report.IndexTrend

/** Read access Screens 1–2 need (§8) — a distinct concern from recording/enrichment, same reasoning as `ClipRepository`/`EnrichmentRepository`. */
class ReportRepository(private val db: JawTrackDatabase) {

    suspend fun getMostRecentReportableSession(): Session? = db.sessionDao().getMostRecentEnded()

    suspend fun getSession(sessionId: Long): Session? = db.sessionDao().getById(sessionId)

    suspend fun getNightMetrics(sessionId: Long): NightMetrics? = db.nightMetricsDao().getForSession(sessionId)

    suspend fun getEpisodes(sessionId: Long): List<Episode> = db.episodeDao().getForSession(sessionId)

    suspend fun getHeartRateSamples(sessionId: Long): List<HeartRateSample> = db.heartRateSampleDao().getForSession(sessionId)

    suspend fun getSleepStages(sessionId: Long): List<SleepStage> = db.sleepStageDao().getForSession(sessionId)

    suspend fun getGaps(sessionId: Long): List<Gap> = db.gapDao().getForSession(sessionId)

    /** Prior nights' JawTrack Index, oldest first, for the 7-night rolling average (§8 Screen 1). */
    suspend fun getRecentIndexHistory(excludingSessionId: Long, limit: Int = IndexTrend.DEFAULT_WINDOW_SIZE): List<Double> {
        val recent = db.sessionDao().getRecentEnriched(limit + 1) // +1 in case tonight is already ENRICHED and included
            .filter { it.id != excludingSessionId }
            .take(limit)
            .reversed() // oldest first

        return recent.mapNotNull { db.nightMetricsDao().getForSession(it.id)?.episodesPerHour }
    }
}
