package com.jawtrack.app.clips

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jawtrack.app.JawTrackApp
import java.util.concurrent.TimeUnit

/**
 * Scheduled purge of expired encrypted clips (JawTrackSpec §6.7, §3.1): runs daily and on
 * every app launch, so a clip never lingers past its retention window just because the app
 * wasn't opened that day.
 */
class RetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as JawTrackApp
        val purged = app.clipRepository.purgeExpired(System.currentTimeMillis())
        Log.i(TAG, "Retention purge removed $purged expired clip(s)")
        return Result.success()
    }

    companion object {
        private const val TAG = "RetentionWorker"
        private const val PERIODIC_WORK_NAME = "jawtrack_retention_daily"
        private const val ONE_TIME_WORK_NAME = "jawtrack_retention_on_launch"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<RetentionWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
