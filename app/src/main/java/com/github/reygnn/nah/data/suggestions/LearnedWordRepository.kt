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
// die gelernten Wörter gingen dann zwar verloren, aber Tastatur/Einstellungen bleiben benutzbar.
private val Context.learnedWordsDataStore: DataStore<Preferences> by
    preferencesDataStore(
        name = "nah_learned_words",
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

/**
 * Beim Tippen „gelernte" Wörter (das Lesezeichen-Chip in der Vorschlagsleiste), persistiert als
 * String-Set in einem **eigenen** DataStore — bewusst getrennt von den kuratierten [UserWordRepository]-
 * Wörtern, weil beide unterschiedlich committet werden: kuratierte Wörter **wörtlich** (Namen/Adressen/
 * E-Mails, Schreibweise ist massgeblich), gelernte Wörter dagegen **wie Wörterbuch-Wörter** an Shift/
 * Caps-Lock angepasst (siehe `SuggestionRepository.isLearnedWord` / `KeyboardViewModel.committedForm`).
 *
 * Gespeichert wird die **getippte Schreibweise** als Basis; das Präfix-Casing legt sich beim Antippen
 * darüber. Wie [UserWordRepository] ändert dieser Store **nie** fertigen Text, sondern liefert nur eine
 * zusätzliche Vorschlagsquelle.
 */
class LearnedWordRepository(private val context: Context) {

    val words: Flow<Set<String>> =
        context.learnedWordsDataStore.data
            // Transiente Lese-IOException → leere Liste statt den Collector zu töten (s. Handler oben).
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[KEY] ?: emptySet() }

    /**
     * Lernt ein Wort. `null` = erfolgreich, sonst der Ablehnungsgrund. Dieselbe [UserWordValidation]
     * wie die kuratierten Wörter (2–50 Zeichen, keine Steuerzeichen, keine case-insensitiven Duplikate);
     * die Validierung läuft innerhalb der DataStore-Transaktion gegen den aktuellen Stand (kein Race).
     */
    suspend fun add(raw: String): UserWordError? {
        val word = raw.trim()
        var error: UserWordError? = null
        context.learnedWordsDataStore.edit { prefs ->
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

    suspend fun remove(word: String) {
        context.learnedWordsDataStore.edit { prefs ->
            prefs[KEY] = (prefs[KEY] ?: emptySet())
                .filterNot { it.equals(word, ignoreCase = true) }
                .toSet()
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("learned_words")
    }
}
