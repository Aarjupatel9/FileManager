package com.mhk.filemanager.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mhk.filemanager.data.model.Constants.SORT_CONSTANTS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create a DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    // Define a key for the sort order preference
    companion object {
        val SORT_ORDER_KEY = intPreferencesKey("sort_order")
    }

    // Flow to read the sort order from DataStore.
    // It will emit the saved value or the default value if nothing is saved.
    val sortOrderFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SORT_ORDER_KEY] ?: SORT_CONSTANTS.SORT_BY_NAME_ASC
        }

    // Function to save the sort order to DataStore.
    // This is a suspend function, so it must be called from a coroutine.
    suspend fun setSortOrder(sortOrder: Int) {
        context.dataStore.edit { settings ->
            settings[SORT_ORDER_KEY] = sortOrder
        }
    }

    // Flow to read path-specific sort order.
    fun getSortOrderForPath(path: String): Flow<Int> {
        val pathKey = intPreferencesKey("sort_order_$path")
        return context.dataStore.data.map { preferences ->
            preferences[pathKey] ?: preferences[SORT_ORDER_KEY] ?: SORT_CONSTANTS.SORT_BY_NAME_ASC
        }
    }

    // Function to save path-specific sort order.
    suspend fun setSortOrderForPath(path: String, sortOrder: Int) {
        val pathKey = intPreferencesKey("sort_order_$path")
        context.dataStore.edit { settings ->
            settings[pathKey] = sortOrder
        }
    }

    // Flow to read custom playlist order.
    fun getCustomPlaylistOrder(playlistPath: String): Flow<List<String>> {
        val key = stringPreferencesKey("playlist_order_$playlistPath")
        return context.dataStore.data.map { preferences ->
            val saved = preferences[key] ?: ""
            if (saved.isEmpty()) emptyList() else saved.split(",")
        }
    }

    // Function to save custom playlist order.
    suspend fun setCustomPlaylistOrder(playlistPath: String, fileOrder: List<String>) {
        val key = stringPreferencesKey("playlist_order_$playlistPath")
        context.dataStore.edit { settings ->
            settings[key] = fileOrder.joinToString(",")
        }
    }
}
