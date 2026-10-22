package com.xnotes.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DriveSyncScheduler {
    private const val UNIQUE_PERIODIC_WORK = "xnotes_drive_sync_periodic"
    private const val UNIQUE_IMMEDIATE_WORK = "xnotes_drive_sync_now"

    fun schedule(context: Context, prefs: DriveSyncPreferences) {
        val workManager = WorkManager.getInstance(context)
        if (!prefs.enabled || !GoogleAuthManager.isConnected(context) || prefs.frequency == SyncFrequency.ON_SAVE) {
            workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(prefs.onlyWhileCharging)
            .build()

        val intervalMinutes = prefs.frequency.minutes.coerceAtLeast(15L)

        val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun triggerImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<DriveSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
        workManager.cancelUniqueWork(UNIQUE_IMMEDIATE_WORK)
    }
}
