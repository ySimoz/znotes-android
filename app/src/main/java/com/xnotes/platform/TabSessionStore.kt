package com.xnotes.platform

import android.content.SharedPreferences
import org.json.JSONArray

data class TabSessionSnapshot(
    val uris: List<String>,
    val activeIndex: Int,
)

/**
 * Persists open tab URIs and active tab index in [SharedPreferences].
 * Uses JSON array serialization to strictly preserve tab ordering across restarts.
 */
class TabSessionStore(private val prefs: SharedPreferences) {

    fun save(tabUris: List<String>, activeIndex: Int) {
        val jsonArray = JSONArray()
        for (uri in tabUris) {
            jsonArray.put(uri)
        }
        prefs.edit()
            .putString(KEY_TAB_URIS, jsonArray.toString())
            .putInt(KEY_ACTIVE_INDEX, activeIndex)
            .apply()
    }

    fun load(): TabSessionSnapshot {
        val raw = prefs.getString(KEY_TAB_URIS, null)
        val activeIndex = prefs.getInt(KEY_ACTIVE_INDEX, 0)
        if (raw.isNullOrEmpty()) {
            return TabSessionSnapshot(emptyList(), -1)
        }
        val list = mutableListOf<String>()
        runCatching {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val uri = jsonArray.optString(i)
                if (!uri.isNullOrEmpty()) {
                    list.add(uri)
                }
            }
        }
        val clamped = if (list.isEmpty()) -1 else activeIndex.coerceIn(0, list.size - 1)
        return TabSessionSnapshot(list, clamped)
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_TAB_URIS)
            .remove(KEY_ACTIVE_INDEX)
            .apply()
    }

    companion object {
        private const val KEY_TAB_URIS = "tab_session_uris_ordered"
        private const val KEY_ACTIVE_INDEX = "tab_session_active_index"
    }
}
