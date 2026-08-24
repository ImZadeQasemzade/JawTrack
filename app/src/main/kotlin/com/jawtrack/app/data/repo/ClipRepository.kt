package com.jawtrack.app.data.repo

import com.jawtrack.app.clips.ClipStore
import com.jawtrack.app.data.db.JawTrackDatabase
import com.jawtrack.app.data.db.entities.AudioClip
import com.jawtrack.corelogic.retention.RetentionPolicy

class ClipRepository(
    private val db: JawTrackDatabase,
    private val clipStore: ClipStore
) {

    /** Encrypts and persists a clip for [episodeId], expiring per [retentionDays] from now (§4.5, §6.3, §6.7). */
    suspend fun saveClip(
        episodeId: Long,
        pcm16Bit: ShortArray,
        sampleRateHz: Int,
        createdAtMillis: Long,
        retentionDays: Int = RetentionPolicy.DEFAULT_RETENTION_DAYS
    ): Long? {
        val written = clipStore.writeClip(pcm16Bit, sampleRateHz) ?: return null
        val clip = AudioClip(
            episodeId = episodeId,
            path = written.path,
            durationMs = written.durationMs,
            sampleRate = written.sampleRate,
            keyAlias = written.keyAlias,
            expiresAt = RetentionPolicy.computeExpiresAt(createdAtMillis, retentionDays)
        )
        return db.audioClipDao().insert(clip)
    }

    /** Decrypts a clip straight to memory for playback/labeling (§8 Screen 3) — never to a temp file. */
    fun readClip(path: String): ShortArray = clipStore.readClip(path)

    /** Deletes every clip whose retention window has passed. Returns how many were purged. */
    suspend fun purgeExpired(nowMillis: Long): Int {
        val expired = db.audioClipDao().getExpired(nowMillis)
        expired.forEach { clip ->
            clipStore.deleteClip(clip.path)
            db.audioClipDao().deleteById(clip.id)
        }
        return expired.size
    }

    /** "Delete all audio" (§6.7): every clip file and row, immediately, no undo. */
    suspend fun deleteAllClips() {
        clipStore.deleteAllClips()
        db.audioClipDao().deleteAll()
    }
}
