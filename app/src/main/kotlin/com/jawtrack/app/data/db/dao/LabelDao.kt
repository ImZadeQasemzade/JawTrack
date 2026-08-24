package com.jawtrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jawtrack.app.data.db.entities.Label

/** Feeds Phase 9's retraining export. */
@Dao
interface LabelDao {
    @Insert
    suspend fun insert(label: Label): Long

    @Query("SELECT * FROM labels ORDER BY labeledAt ASC")
    suspend fun getAll(): List<Label>
}
