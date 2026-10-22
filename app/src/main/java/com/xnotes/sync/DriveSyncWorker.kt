package com.xnotes.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xnotes.platform.JsonStore

class DriveSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val settingsStore = JsonStore.settings(applicationContext)
            val settingsJson = settingsStore.read()
            val prefsJson = settingsJson.optJSONObject("prefs")
            val browseRoot = settingsJson.optString("browse_root").ifEmpty { null }
            val syncPrefs = DriveSyncPreferences.fromJson(prefsJson?.optJSONObject("drive_sync"))

            if (!GoogleAuthManager.isConnected(applicationContext)) {
                return Result.success()
            }

            val engine = DriveSyncEngine(applicationContext)
            val result = engine.sync(syncPrefs, browseRoot)

            // Update sync preferences with timestamp and status
            val updatedSyncPrefs = syncPrefs.copy(
                lastSyncTime = System.currentTimeMillis(),
                lastSyncStatus = result.message,
            )

            val updatedPrefsJson = (prefsJson ?: org.json.JSONObject()).apply {
                put("drive_sync", updatedSyncPrefs.toJson())
            }
            settingsJson.put("prefs", updatedPrefsJson)
            settingsStore.write(settingsJson)

            if (result.success) Result.success() else Result.retry()
        } catch (e: Exception) {
            android.util.Log.e("DriveSyncWorker", "Error in sync worker", e)
            Result.retry()
        }
    }
}
