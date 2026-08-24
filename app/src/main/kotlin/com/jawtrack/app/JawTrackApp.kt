package com.jawtrack.app

import android.app.Application
import com.jawtrack.app.clips.ClipStore
import com.jawtrack.app.clips.RetentionWorker
import com.jawtrack.app.data.db.JawTrackDatabase
import com.jawtrack.app.data.repo.ClipRepository
import com.jawtrack.app.data.repo.EnrichmentRepository
import com.jawtrack.app.data.repo.LabelRepository
import com.jawtrack.app.data.repo.ReportRepository
import com.jawtrack.app.data.repo.SessionRepository

class JawTrackApp : Application() {

    lateinit var database: JawTrackDatabase
        private set

    lateinit var sessionRepository: SessionRepository
        private set

    lateinit var clipRepository: ClipRepository
        private set

    lateinit var enrichmentRepository: EnrichmentRepository
        private set

    lateinit var reportRepository: ReportRepository
        private set

    lateinit var labelRepository: LabelRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = JawTrackDatabase.getInstance(this)
        sessionRepository = SessionRepository(database)
        clipRepository = ClipRepository(database, ClipStore(this))
        enrichmentRepository = EnrichmentRepository(database)
        reportRepository = ReportRepository(database)
        labelRepository = LabelRepository(database)

        RetentionWorker.schedulePeriodic(this)
        RetentionWorker.runOnce(this) // §6.7: also purge on every launch, not just daily
    }
}
