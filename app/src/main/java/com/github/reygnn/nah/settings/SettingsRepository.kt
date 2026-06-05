package com.github.reygnn.nah.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nah_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val suggestionsEnabled = booleanPreferencesKey("suggestions_enabled")
        val userWordsEnabled = booleanPreferencesKey("user_words_enabled")
        val autoCapEnabled = booleanPreferencesKey("auto_cap_enabled")
        val letterColorHintsEnabled = booleanPreferencesKey("letter_color_hints_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.suggestionsEnabled] = next.suggestionsEnabled
            prefs[Keys.userWordsEnabled] = next.userWordsEnabled
            prefs[Keys.autoCapEnabled] = next.autoCapEnabled
            prefs[Keys.letterColorHintsEnabled] = next.letterColorHintsEnabled
        }
    }

    private fun Preferences.toSettings(): Settings = Settings(
        suggestionsEnabled = this[Keys.suggestionsEnabled] ?: DEFAULT.suggestionsEnabled,
        userWordsEnabled = this[Keys.userWordsEnabled] ?: DEFAULT.userWordsEnabled,
        autoCapEnabled = this[Keys.autoCapEnabled] ?: DEFAULT.autoCapEnabled,
        letterColorHintsEnabled = this[Keys.letterColorHintsEnabled] ?: DEFAULT.letterColorHintsEnabled,
    )

    private companion object {
        val DEFAULT = Settings()
    }
}
