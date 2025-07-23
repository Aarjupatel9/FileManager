package com.mhk.filemanager.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
}
