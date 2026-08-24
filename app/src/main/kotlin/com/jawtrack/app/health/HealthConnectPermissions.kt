package com.jawtrack.app.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SleepSessionRecord

/** Read-only Health Connect scopes JawTrack requests (§5.1). Requested via PermissionController's own contract, not the standard runtime-permission one. */
object HealthConnectPermissions {
    val REQUIRED: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class)
    )
}
