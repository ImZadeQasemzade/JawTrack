package com.jawtrack.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Encrypted evidence clip for one episode (§6, §7). Populated starting Phase 4. */
@Entity(
    tableName = "audio_clips",
    foreignKeys = [
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("episodeId"), Index("expiresAt")]
)
data class AudioClip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeId: Long,
    /** Path within context.filesDir — never external storage or MediaStore (§6.6). */
    val path: String,
    val durationMs: Long,
    val sampleRate: Int,
    val keyAlias: String,
    val expiresAt: Long
)
