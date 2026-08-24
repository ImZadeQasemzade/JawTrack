package com.jawtrack.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.jawtrack.corelogic.enrichment.SleepStageInterval
import java.time.Instant

enum class HealthConnectAvailability { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

data class HeartRateSampleData(val timeMillis: Long, val bpm: Int)
data class HrvSampleData(val timeMillis: Long, val rmssd: Double)
data class RespiratoryRateSampleData(val timeMillis: Long, val rate: Double)

data class HealthConnectNightData(
    val heartRateSamples: List<HeartRateSampleData>,
    val hrvSamples: List<HrvSampleData>,
    val respiratoryRateSamples: List<RespiratoryRateSampleData>,
    val sleepStages: List<SleepStageInterval>
) {
    /** Health Connect returned nothing at all for the window — the watch most likely hasn't synced yet (§5.3). */
    val isEmpty: Boolean
        get() = heartRateSamples.isEmpty() && hrvSamples.isEmpty() && respiratoryRateSamples.isEmpty() && sleepStages.isEmpty()
}

/**
 * Reads Garmin-synced data from Health Connect (§5.1). **Unverified**: written without Android
 * SDK or network access to compile against the real `androidx.health.connect:connect-client`
 * artifact (see `docs/PHASE_STATUS.md`). `androidx.health.connect` is a stable, long-documented
 * Jetpack library rather than a fast-moving third-party one like the MediaPipe integration in
 * `YamnetGate2Classifier`, so confidence in the API shape below is higher — but nothing here
 * has actually compiled or run.
 *
 * §5.1's honest constraint: overnight HR from Garmin is typically ~1 sample/1–2min, far too
 * coarse to confirm a 5s arousal. This repo hands back raw samples; callers must only use them
 * at night/cluster level, never claim per-episode beat precision.
 */
class HealthConnectRepo(private val context: Context) {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NEEDS_UPDATE
        else -> HealthConnectAvailability.UNAVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(HealthConnectPermissions.REQUIRED)

    suspend fun readNight(windowStart: Instant, windowEnd: Instant): HealthConnectNightData {
        val timeRange = TimeRangeFilter.between(windowStart, windowEnd)

        val heartRate = client.readRecords(ReadRecordsRequest(recordType = HeartRateRecord::class, timeRangeFilter = timeRange))
            .records
            .flatMap { record -> record.samples.map { HeartRateSampleData(it.time.toEpochMilli(), it.beatsPerMinute.toInt()) } }

        val hrv = client.readRecords(ReadRecordsRequest(recordType = HeartRateVariabilityRmssdRecord::class, timeRangeFilter = timeRange))
            .records
            .map { HrvSampleData(it.time.toEpochMilli(), it.heartRateVariabilityMillis) }

        val respiratory = client.readRecords(ReadRecordsRequest(recordType = RespiratoryRateRecord::class, timeRangeFilter = timeRange))
            .records
            .map { RespiratoryRateSampleData(it.time.toEpochMilli(), it.rate) }

        val sleepStages = client.readRecords(ReadRecordsRequest(recordType = SleepSessionRecord::class, timeRangeFilter = timeRange))
            .records
            .flatMap { session ->
                session.stages.map { stage ->
                    SleepStageInterval(
                        startMillis = stage.startTime.toEpochMilli(),
                        endMillis = stage.endTime.toEpochMilli(),
                        stage = stageLabel(stage.stage)
                    )
                }
            }

        return HealthConnectNightData(heartRate, hrv, respiratory, sleepStages)
    }

    private fun stageLabel(stageType: Int): String = when (stageType) {
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "AWAKE"
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "OUT_OF_BED"
        SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP"
        SleepSessionRecord.STAGE_TYPE_REM -> "REM"
        // An envelope-only session with no stage detail -- Phase 0's own verification task (§2.1.1)
        // is to find out whether this ever actually happens on the target watch.
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "LIGHT"
        else -> "UNKNOWN"
    }
}
