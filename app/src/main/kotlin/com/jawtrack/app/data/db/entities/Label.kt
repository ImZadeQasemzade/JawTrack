package com.jawtrack.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** History of a user's confirm/reject taps on an episode, for retraining export (§7, §4.7 Gate 3). */
@Entity(
    tableName = "labels",
    foreignKeys = [
        ForeignKey(entity = Episode::class, parentColumns = ["id"], childColumns = ["episodeId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("episodeId")]
)
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeId: Long,
    val label: String,
    val labeledAt: Long,
    val classifierVersion: String
)
