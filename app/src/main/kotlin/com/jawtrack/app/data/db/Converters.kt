package com.jawtrack.app.data.db

import androidx.room.TypeConverter
import com.jawtrack.app.data.db.entities.EnrichmentState
import com.jawtrack.app.data.db.entities.SessionState
import com.jawtrack.app.data.db.entities.UserLabel

class Converters {

    @TypeConverter
    fun sessionStateToString(state: SessionState): String = state.name

    @TypeConverter
    fun sessionStateFromString(value: String): SessionState = SessionState.valueOf(value)

    @TypeConverter
    fun enrichmentStateToString(state: EnrichmentState): String = state.name

    @TypeConverter
    fun enrichmentStateFromString(value: String): EnrichmentState = EnrichmentState.valueOf(value)

    @TypeConverter
    fun userLabelToString(label: UserLabel?): String? = label?.name

    @TypeConverter
    fun userLabelFromString(value: String?): UserLabel? = value?.let { UserLabel.valueOf(it) }

    @TypeConverter
    fun stringListToDb(labels: List<String>): String = labels.joinToString(separator = "|")

    @TypeConverter
    fun stringListFromDb(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split("|")
}
