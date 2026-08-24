package com.jawtrack.app

import android.app.Application
import com.jawtrack.app.clips.ClipStore
import com.jawtrack.app.clips.RetentionWorker
import com.jawtrack.app.data.db.JawTrackDatabase
import com.jawtrack.app.data.repo.ClipRepository
import com.jawtrack.app.data.repo.SessionRepository

class JawTrackApp : Application() {

    lateinit var database: JawTrackDatabase
        private set

    lateinit var sessionRepository: SessionRepository
        private set

    lateinit var clipRepository: ClipRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = JawTrackDatabase.getInstance(this)
        sessionRepository = SessionRepository(database)
        clipRepository = ClipRepository(database, ClipStore(this))

        RetentionWorker.schedulePeriodic(this)
        RetentionWorker.runOnce(this) // §6.7: also purge on every launch, not just daily
    }
}
