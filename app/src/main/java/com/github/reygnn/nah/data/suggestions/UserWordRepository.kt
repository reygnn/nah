package com.github.reygnn.nah.data.suggestions

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Korrupte Datei einmalig durch Defaults (leere Liste) ersetzen statt bei jedem Lesen zu werfen —
// die kuratierte Wortliste ginge dann zwar verloren, aber Tastatur/Einstellungen bleiben benutzbar.
private val Context.userWordsDataStore: DataStore<Preferences> by
    preferencesDataStore(
        name = "nah_user_words",
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

/**
 * Benutzerdefinierte Wörter, persistiert als String-Set in DataStore (kein Room —
 * das Abfragen erledigt der [WordIndex], hier wird nur gehalten). Speist als zweite
 * Quelle in [SuggestionRepository] ein und ändert **nie** fertigen Text, sondern
 * liefert nur Vorschläge.
 */
class UserWordRepository(private val context: Context) {

    val words: Flow<Set<String>> =
        context.userWordsDataStore.data
            // Transiente Lese-IOException → leere Liste statt den Collector zu töten (s. Handler oben).
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[KEY] ?: emptySet() }

    /**
     * Fügt ein Wort hinzu. `null` = erfolgreich, sonst der Ablehnungsgrund. Die
     * Validierung läuft innerhalb der DataStore-Transaktion gegen den aktuellen
     * Stand (Duplikat-Prüfung ohne Race).
     */
    suspend fun add(raw: String): UserWordError? {
        val word = raw.trim()
        var error: UserWordError? = null
        context.userWordsDataStore.edit { prefs ->
            val current = prefs[KEY] ?: emptySet()
            val reason = UserWordValidation.validate(word, current)
            if (reason != null) {
                error = reason
                return@edit
            }
            prefs[KEY] = current + word
        }
        return error
    }

    /**
     * Ersetzt [old] durch [raw] (Tippfehler korrigieren). `null` = erfolgreich, sonst
     * der Ablehnungsgrund. Die Duplikat-Prüfung ignoriert das bearbeitete Wort selbst,
     * damit reine Gross-/Kleinschreibungs- oder Tippfehler-Korrekturen durchgehen.
     */
    suspend fun update(old: String, raw: String): UserWordError? {
        val word = raw.trim()
        var error: UserWordError? = null
        context.userWordsDataStore.edit { prefs ->
            val current = prefs[KEY] ?: emptySet()
            // Den bearbeiteten Eintrag von der Duplikat-Prüfung ausnehmen (siehe validate).
            val reason = UserWordValidation.validate(word, current, ignore = old)
            if (reason != null) {
                error = reason
                return@edit
            }
            prefs[KEY] = current.filterNot { it.equals(old, ignoreCase = true) }.toSet() + word
        }
        return error
    }

    suspend fun remove(word: String) {
        context.userWordsDataStore.edit { prefs ->
            prefs[KEY] = (prefs[KEY] ?: emptySet())
                .filterNot { it.equals(word, ignoreCase = true) }
                .toSet()
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("user_words")
    }
}
