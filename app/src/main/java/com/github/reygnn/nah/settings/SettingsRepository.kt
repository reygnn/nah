package com.github.reygnn.nah.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Eine korrupte Preferences-Datei (z. B. ein beim Force-Stop/Akku-leer abgebrochener Schreibvorgang)
// würde sonst bei JEDEM Lesen eine CorruptionException werfen — der Handler ersetzt sie einmalig durch
// Defaults, statt die Tastatur dauerhaft lahmzulegen.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nah_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val suggestionsEnabled = booleanPreferencesKey("suggestions_enabled")
        val userWordsEnabled = booleanPreferencesKey("user_words_enabled")
        val autoCapEnabled = booleanPreferencesKey("auto_cap_enabled")
        val letterColorHintsEnabled = booleanPreferencesKey("letter_color_hints_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data
        // Transiente Lese-IOExceptions nicht den Collector (IME-Service / Settings-Composition)
        // töten lassen — auf Defaults zurückfallen; der Korruptionsfall ist oben schon abgedeckt.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

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
