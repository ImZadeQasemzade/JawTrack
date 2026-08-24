package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jawtrack.app.data.db.entities.RoomProfile

@Dao
interface RoomProfileDao {
    @Insert
    suspend fun insert(profile: RoomProfile): Long

    @Query("SELECT * FROM room_profiles ORDER BY createdAt DESC LIMIT 1")
    suspend fun getMostRecent(): RoomProfile?

    @Query("SELECT * FROM room_profiles WHERE id = :id")
    suspend fun getById(id: Long): RoomProfile?
}
