package com.xnotes.sync

import android.content.Context
import org.json.JSONObject
import java.io.File

class DriveSyncLedger(private val context: Context) {
    private val ledgerFile = File(context.filesDir, "drive_sync_ledger.json")
    private val entries = mutableMapOf<String, LedgerEntry>()
    private val lock = Any()

    init {
        load()
    }

    fun get(relativePath: String): LedgerEntry? = synchronized(lock) {
        entries[relativePath]
    }

    fun getAll(): Map<String, LedgerEntry> = synchronized(lock) {
        entries.toMap()
    }

    fun put(relativePath: String, entry: LedgerEntry) = synchronized(lock) {
        entries[relativePath] = entry
        save()
    }

    fun remove(relativePath: String) = synchronized(lock) {
        if (entries.remove(relativePath) != null) {
            save()
        }
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        if (ledgerFile.exists()) ledgerFile.delete()
    }

    private fun load() = synchronized(lock) {
        if (!ledgerFile.exists()) return
        try {
            val content = ledgerFile.readText()
            val json = JSONObject(content)
            entries.clear()
            for (key in json.keys()) {
                val obj = json.getJSONObject(key)
                entries[key] = LedgerEntry.fromJson(obj)
            }
        } catch (_: Exception) {
            entries.clear()
        }
    }

    private fun save() = synchronized(lock) {
        try {
            val json = JSONObject()
            for ((key, entry) in entries) {
                json.put(key, entry.toJson())
            }
            val temp = File(context.filesDir, "drive_sync_ledger.tmp")
            temp.writeText(json.toString())
            temp.renameTo(ledgerFile)
        } catch (_: Exception) { }
    }
}
