package com.jawtrack.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ground-truth symptom check-in, keyed by ISO date (§7, §8 Screen 4). Populated starting Phase 6. */
@Entity(tableName = "morning_checkins")
data class MorningCheckin(
    @PrimaryKey val date: String, // ISO-8601 yyyy-MM-dd
    val jawSoreness1to5: Int? = null,
    val headache: Boolean? = null,
    val sleepQuality1to5: Int? = null,
    val toothSensitivity1to5: Int? = null
)
