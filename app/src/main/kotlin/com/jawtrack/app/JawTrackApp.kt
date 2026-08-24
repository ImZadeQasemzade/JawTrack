package com.jawtrack.app

import android.app.Application
import com.jawtrack.app.data.db.JawTrackDatabase
import com.jawtrack.app.data.repo.SessionRepository

class JawTrackApp : Application() {

    lateinit var database: JawTrackDatabase
        private set

    lateinit var sessionRepository: SessionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = JawTrackDatabase.getInstance(this)
        sessionRepository = SessionRepository(database)
    }
}
