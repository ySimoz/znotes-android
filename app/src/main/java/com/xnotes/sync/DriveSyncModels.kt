package com.xnotes.sync

import org.json.JSONArray
import org.json.JSONObject

enum class SyncScope(val id: String) {
    ALL("all"),
    CUSTOM("custom");

    companion object {
        fun fromId(id: String): SyncScope = entries.find { it.id == id } ?: ALL
    }
}

enum class SyncFrequency(val id: String, val minutes: Long) {
    MINUTES_15("15m", 15L),
    HOURLY("1h", 60L),
    HOURS_3("3h", 180L),
    HOURS_6("6h", 360L),
    DAILY("24h", 1440L),
    ON_SAVE("on_save", 0L);

    companion object {
        fun fromId(id: String): SyncFrequency = entries.find { it.id == id } ?: HOURLY
    }
}

enum class DeletionPolicy(val id: String) {
    MOVE_TO_TRASH("trash"),
    ALWAYS_ASK("ask"),
    KEEP_ON_DRIVE("keep");

    companion object {
        fun fromId(id: String): DeletionPolicy = entries.find { it.id == id } ?: MOVE_TO_TRASH
    }
}

data class DriveSyncPreferences(
    val enabled: Boolean = false,
    val accountEmail: String? = null,
    val accountName: String? = null,
    val customClientId: String? = null,
    val syncScope: SyncScope = SyncScope.ALL,
    val selectedFolders: Set<String> = emptySet(),
    val frequency: SyncFrequency = SyncFrequency.HOURLY,
    val wifiOnly: Boolean = true,
    val onlyWhileCharging: Boolean = false,
    val deletionPolicy: DeletionPolicy = DeletionPolicy.MOVE_TO_TRASH,
    val lastSyncTime: Long = 0L,
    val lastSyncStatus: String? = null,
    val pendingDeletions: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        accountEmail?.let { put("account_email", it) }
        accountName?.let { put("account_name", it) }
        customClientId?.let { put("custom_client_id", it) }
        put("sync_scope", syncScope.id)
        put("selected_folders", JSONArray(selectedFolders))
        put("frequency", frequency.id)
        put("wifi_only", wifiOnly)
        put("only_while_charging", onlyWhileCharging)
        put("deletion_policy", deletionPolicy.id)
        put("last_sync_time", lastSyncTime)
        lastSyncStatus?.let { put("last_sync_status", it) }
        put("pending_deletions", JSONArray(pendingDeletions))
    }

    companion object {
        fun fromJson(o: JSONObject?): DriveSyncPreferences {
            if (o == null) return DriveSyncPreferences()
            val folders = mutableSetOf<String>()
            o.optJSONArray("selected_folders")?.let { arr ->
                for (i in 0 until arr.length()) folders.add(arr.optString(i))
            }
            val deletions = mutableListOf<String>()
            o.optJSONArray("pending_deletions")?.let { arr ->
                for (i in 0 until arr.length()) deletions.add(arr.optString(i))
            }
            return DriveSyncPreferences(
                enabled = o.optBoolean("enabled", false),
                accountEmail = o.optString("account_email").ifEmpty { null },
                accountName = o.optString("account_name").ifEmpty { null },
                customClientId = o.optString("custom_client_id").ifEmpty { null },
                syncScope = SyncScope.fromId(o.optString("sync_scope")),
                selectedFolders = folders,
                frequency = SyncFrequency.fromId(o.optString("frequency")),
                wifiOnly = o.optBoolean("wifi_only", true),
                onlyWhileCharging = o.optBoolean("only_while_charging", false),
                deletionPolicy = DeletionPolicy.fromId(o.optString("deletion_policy")),
                lastSyncTime = o.optLong("last_sync_time", 0L),
                lastSyncStatus = o.optString("last_sync_status").ifEmpty { null },
                pendingDeletions = deletions,
            )
        }
    }
}

data class LedgerEntry(
    val driveFileId: String,
    val driveParentId: String,
    val localMtime: Long,
    val lastSyncedMtime: Long,
    val sizeBytes: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("driveFileId", driveFileId)
        put("driveParentId", driveParentId)
        put("localMtime", localMtime)
        put("lastSyncedMtime", lastSyncedMtime)
        put("sizeBytes", sizeBytes)
    }

    companion object {
        fun fromJson(o: JSONObject): LedgerEntry = LedgerEntry(
            driveFileId = o.getString("driveFileId"),
            driveParentId = o.getString("driveParentId"),
            localMtime = o.getLong("localMtime"),
            lastSyncedMtime = o.getLong("lastSyncedMtime"),
            sizeBytes = o.optLong("sizeBytes", 0L),
        )
    }
}

data class SyncResult(
    val success: Boolean,
    val syncedCount: Int = 0,
    val deletedCount: Int = 0,
    val errorCount: Int = 0,
    val message: String = "",
)
