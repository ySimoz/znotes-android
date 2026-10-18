package com.xnotes.platform

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSessionStoreTest {

    private class InMemorySharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<*>)?.mapNotNull { it as? String }?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class EditorImpl(private val parent: InMemorySharedPreferences) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { key?.let { pending[it] = values }; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
            override fun remove(key: String?): SharedPreferences.Editor { key?.let { toRemove.add(it) }; return this }
            override fun clear(): SharedPreferences.Editor { clear = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) parent.data.clear()
                for (k in toRemove) parent.data.remove(k)
                parent.data.putAll(pending)
            }
        }
    }

    @Test
    fun roundTripsOrderedTabUrisAndActiveIndex() {
        val prefs = InMemorySharedPreferences()
        val store = TabSessionStore(prefs)

        val uris = listOf(
            "content://com.android.providers.media.documents/document/101",
            "content://com.android.providers.media.documents/document/102",
            "content://com.android.providers.media.documents/document/103",
        )
        store.save(uris, 1)

        val restored = store.load()
        assertEquals(uris, restored.uris)
        assertEquals(1, restored.activeIndex)
    }

    @Test
    fun handlesEmptyAndClearedSession() {
        val prefs = InMemorySharedPreferences()
        val store = TabSessionStore(prefs)

        val empty = store.load()
        assertTrue(empty.uris.isEmpty())
        assertEquals(-1, empty.activeIndex)

        store.save(listOf("content://doc1"), 0)
        store.clear()

        val afterClear = store.load()
        assertTrue(afterClear.uris.isEmpty())
        assertEquals(-1, afterClear.activeIndex)
    }

    @Test
    fun clampsActiveIndexIntoBounds() {
        val prefs = InMemorySharedPreferences()
        val store = TabSessionStore(prefs)

        store.save(listOf("content://doc1", "content://doc2"), 99)
        val loaded = store.load()
        assertEquals(1, loaded.activeIndex)
    }
}
