package com.jawtrack.app.data.repo

import com.jawtrack.app.data.db.JawTrackDatabase
import com.jawtrack.app.data.db.entities.AudioClip
import com.jawtrack.app.data.db.entities.Episode
import com.jawtrack.app.data.db.entities.Label
import com.jawtrack.app.data.db.entities.UserLabel

/** The confirm/reject labeling loop (§8 Screen 3) — what makes the personalized model possible (Phase 9). */
class LabelRepository(private val db: JawTrackDatabase) {

    suspend fun getUnlabeledForSession(sessionId: Long): List<Episode> = db.episodeDao().getUnlabeledForSession(sessionId)

    suspend fun getEpisode(episodeId: Long): Episode? = db.episodeDao().getById(episodeId)

    suspend fun getClipsForEpisode(episodeId: Long): List<AudioClip> = db.audioClipDao().getForEpisode(episodeId)

    suspend fun applyLabel(episodeId: Long, label: UserLabel, classifierVersion: String) {
        db.episodeDao().updateUserLabel(episodeId, label)
        db.labelDao().insert(
            Label(
                episodeId = episodeId,
                label = label.name,
                labeledAt = System.currentTimeMillis(),
                classifierVersion = classifierVersion
            )
        )
    }
}
