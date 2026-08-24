package com.jawtrack.app.health

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jawtrack.app.JawTrackApp
import com.jawtrack.app.data.db.entities.EnrichmentState
import com.jawtrack.app.data.db.entities.Session
import com.jawtrack.corelogic.calibration.RoomProfileJson
import com.jawtrack.corelogic.calibration.RoomProfileMath
import com.jawtrack.corelogic.enrichment.ConfidenceGrader
import com.jawtrack.corelogic.enrichment.EpisodeSummary
import com.jawtrack.corelogic.enrichment.NightMetricsCalculator
import com.jawtrack.corelogic.enrichment.SleepStageJoiner
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Joins overnight episodes to Health Connect data and computes night-level metrics
 * (JawTrackSpec §5.3, §7.1): read the session's window, join each episode to its sleep stage,
 * compute the JawTrack Index and confidence grade, mark the session enriched — or `PARTIAL`
 * with a retry-with-backoff if the watch clearly hasn't synced yet.
 *
 * **Unverified**: depends entirely on `HealthConnectRepo`, which itself needs a real Health
 * Connect provider with actual Garmin-synced data to exercise at all (see that file's warning).
 */
class EnrichmentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as JawTrackApp
        val healthConnectRepo = HealthConnectRepo(applicationContext)

        if (healthConnectRepo.availability() != HealthConnectAvailability.AVAILABLE) {
            Log.i(TAG, "Health Connect not available on this device; nothing to enrich this run")
            return Result.success()
        }
        if (!healthConnectRepo.hasAllPermissions()) {
            Log.i(TAG, "Health Connect permissions not granted; nothing to enrich this run")
            return Result.success()
        }

        val pending = app.enrichmentRepository.getPendingEnrichment()
        if (pending.isEmpty()) return Result.success()

        var anyStillPartial = false
        for (session in pending) {
            if (!enrichOne(session, app, healthConnectRepo)) anyStillPartial = true
        }

        // WorkManager retries the whole worker, not per-session -- fine here, since a re-run
        // just re-checks every still-pending session again (§5.3: "re-enqueue with backoff").
        return if (anyStillPartial) Result.retry() else Result.success()
    }

    /** @return true if this session reached ENRICHED; false if left PARTIAL (retry candidate). */
    private suspend fun enrichOne(session: Session, app: JawTrackApp, healthConnectRepo: HealthConnectRepo): Boolean {
        val endedAt = session.endedAt ?: return true // shouldn't be in the pending list otherwise

        val nightData = healthConnectRepo.readNight(Instant.ofEpochMilli(session.startedAt), Instant.ofEpochMilli(endedAt))

        if (nightData.isEmpty) {
            Log.i(TAG, "No Health Connect data yet for session ${session.id} -- likely waiting on a Garmin sync")
            app.enrichmentRepository.updateEnrichmentState(session.id, EnrichmentState.PARTIAL)
            return false
        }

        app.enrichmentRepository.saveHeartRateSamples(session.id, nightData.heartRateSamples.map { it.timeMillis to it.bpm })
        app.enrichmentRepository.saveHrvSamples(session.id, nightData.hrvSamples.map { it.timeMillis to it.rmssd })
        app.enrichmentRepository.saveSleepStages(session.id, nightData.sleepStages)

        val episodes = app.enrichmentRepository.getEpisodesForSession(session.id)
        val summaries = episodes.map { episode ->
            val stage = SleepStageJoiner.dominantStage(episode.onsetAt, episode.offsetAt, nightData.sleepStages)
            if (stage != null) app.enrichmentRepository.updateEpisodeSleepStage(episode.id, stage)
            EpisodeSummary(episode.onsetAt, episode.offsetAt, stage)
        }

        val sessionDurationHours = (endedAt - session.startedAt) / MILLIS_PER_HOUR
        val sleepDurationHours = SleepStageJoiner.totalSleepDurationHours(nightData.sleepStages)
            .takeIf { it > 0 } ?: sessionDurationHours

        val nightMetrics = NightMetricsCalculator.compute(
            episodes = summaries,
            sleepDurationHours = sleepDurationHours,
            snoringRejectedCount = session.snoringRejectedCount,
            sessionDurationHours = sessionDurationHours
        )

        val roomNoiseFloorDb = session.roomProfileId
            ?.let { app.enrichmentRepository.getRoomProfileById(it) }
            ?.let { RoomProfileMath.broadbandFloorDb(RoomProfileJson.decode(it.bandNoiseFloorJson)) }

        val confidenceGrade = ConfidenceGrader.grade(
            ConfidenceGrader.Inputs(
                coveragePct = session.coveragePct ?: 0.0,
                cleanShutdown = session.cleanShutdown,
                speechRejectedCount = session.speechRejectedCount,
                sessionDurationHours = sessionDurationHours,
                episodeCount = episodes.size,
                labeledEpisodeCount = episodes.count { it.userLabel != null },
                roomNoiseFloorDb = roomNoiseFloorDb
            )
        )

        app.enrichmentRepository.saveNightMetrics(session.id, nightMetrics, confidenceGrade)
        app.enrichmentRepository.updateEnrichmentState(session.id, EnrichmentState.ENRICHED)
        return true
    }

    companion object {
        private const val TAG = "EnrichmentWorker"
        private const val WORK_NAME = "jawtrack_enrichment"
        private const val MILLIS_PER_HOUR = 3_600_000.0
        private const val MIN_BACKOFF_MILLIS = 30_000L

        /** Enqueue on app foreground in the morning (§5.3). KEEP so a foreground while a retry is already backing off doesn't reset it. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<EnrichmentWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
