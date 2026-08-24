package com.jawtrack.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** User-logged daily factors, keyed by ISO date (§7). Feeds CorrelationEngine starting Phase 7. */
@Entity(tableName = "daily_factors")
data class DailyFactors(
    @PrimaryKey val date: String, // ISO-8601 yyyy-MM-dd
    val caffeineMg: Int? = null,
    val caffeineLastTime: Long? = null,
    val alcoholUnits: Double? = null,
    val stress1to5: Int? = null,
    val exerciseMinutes: Int? = null,
    val lastMealTime: Long? = null,
    val screenTimeBeforeBedMin: Int? = null,
    val nightGuardWorn: Boolean? = null,
    val nightGuardId: String? = null,
    val notes: String? = null
)
