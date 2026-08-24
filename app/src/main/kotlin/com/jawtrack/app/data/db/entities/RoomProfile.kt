package com.jawtrack.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-band noise floor from a calibration night (§4.5). Populated starting Phase 2. */
@Entity(tableName = "room_profiles")
data class RoomProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    /** JSON map of third-octave band center Hz -> noise floor dB. */
    val bandNoiseFloorJson: String
)
