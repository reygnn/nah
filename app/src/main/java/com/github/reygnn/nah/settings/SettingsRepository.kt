package com.github.reygnn.nah.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nah_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val suggestionsEnabled = booleanPreferencesKey("suggestions_enabled")
        val keyboardHeightFraction = floatPreferencesKey("keyboard_height_fraction")
        val autoCapEnabled = booleanPreferencesKey("auto_cap_enabled")
        val missMapLearningEnabled = booleanPreferencesKey("miss_map_learning_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.suggestionsEnabled] = next.suggestionsEnabled
            prefs[Keys.keyboardHeightFraction] = next.keyboardHeightFraction
            prefs[Keys.autoCapEnabled] = next.autoCapEnabled
            prefs[Keys.missMapLearningEnabled] = next.missMapLearningEnabled
        }
    }

    private fun Preferences.toSettings(): Settings = Settings(
        suggestionsEnabled = this[Keys.suggestionsEnabled] ?: DEFAULT.suggestionsEnabled,
        keyboardHeightFraction = this[Keys.keyboardHeightFraction] ?: DEFAULT.keyboardHeightFraction,
        autoCapEnabled = this[Keys.autoCapEnabled] ?: DEFAULT.autoCapEnabled,
        missMapLearningEnabled = this[Keys.missMapLearningEnabled] ?: DEFAULT.missMapLearningEnabled,
    )

    private companion object {
        val DEFAULT = Settings()
    }
}
