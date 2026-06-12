package com.github.reygnn.nah.data.suggestions

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Gemeinsame DataStore-Mechanik der beiden Wort-Stores: [UserWordRepository] (kuratiert) und
 * [LearnedWordRepository] (beim Tippen gelernt). Hält ein String-Set, validiert über
 * [UserWordValidation] **innerhalb** der Edit-Transaktion gegen den aktuellen Stand (Duplikat-Prüfung
 * ohne Race) und fängt transiente Lese-IOExceptions ab — der einmalige Korruptionsfall wird vom
 * `ReplaceFileCorruptionHandler` der konkreten Stores abgedeckt (siehe deren DataStore-Delegates).
 *
 * Ändert **nie** fertigen Text; liefert nur eine zusätzliche Vorschlagsquelle. **Wie** ein Wort
 * committet wird — wörtlich (kuratiert) vs. an Shift/Caps angepasst (gelernt) — entscheidet NICHT
 * dieser Store, sondern `SuggestionRepository`/`KeyboardViewModel.committedForm`. Die beiden konkreten
 * Stores unterscheiden sich darum nur in ihrem DataStore-Namen und -Key, nicht in der Mechanik.
 */
abstract class StringSetWordStore(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<Set<String>>,
) {

    val words: Flow<Set<String>> =
        dataStore.data
            // Transiente Lese-IOException → leere Liste statt den Collector zu töten (s. Klassen-Doc).
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[key] ?: emptySet() }

    /**
     * Fügt ein Wort hinzu. `null` = erfolgreich, sonst der Ablehnungsgrund. Dieselbe [UserWordValidation]
     * für beide Stores (2–50 Zeichen, keine Steuerzeichen, keine case-insensitiven Duplikate); die
     * Validierung läuft innerhalb der DataStore-Transaktion gegen den aktuellen Stand (kein Race).
     */
    suspend fun add(raw: String): UserWordError? =
        editValidated(raw, ignore = null) { current, word -> current + word }

    /**
     * Ersetzt [old] durch [raw] (Tippfehler oder Gross-/Kleinschreibung korrigieren). `null` =
     * erfolgreich, sonst der Ablehnungsgrund. Die Duplikat-Prüfung ignoriert den bearbeiteten Eintrag
     * selbst (siehe [UserWordValidation.validate]), damit reine Korrekturen durchgehen.
     */
    suspend fun update(old: String, raw: String): UserWordError? =
        editValidated(raw, ignore = old) { current, word ->
            current.filterNot { it.equals(old, ignoreCase = true) }.toSet() + word
        }

    suspend fun remove(word: String) {
        dataStore.edit { prefs ->
            prefs[key] = (prefs[key] ?: emptySet())
                .filterNot { it.equals(word, ignoreCase = true) }
                .toSet()
        }
    }

    /** Validiert [raw] gegen den aktuellen Stand (in der Transaktion) und schreibt bei Erfolg das von
     *  [apply] gebildete neue Set. [ignore] nimmt den gerade bearbeiteten Eintrag von der Duplikat-
     *  Prüfung aus (`null` beim Hinzufügen). */
    private suspend fun editValidated(
        raw: String,
        ignore: String?,
        apply: (current: Set<String>, word: String) -> Set<String>,
    ): UserWordError? {
        val word = raw.trim()
        var error: UserWordError? = null
        dataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            val reason = UserWordValidation.validate(word, current, ignore = ignore)
            if (reason != null) {
                error = reason
                return@edit
            }
            prefs[key] = apply(current, word)
        }
        return error
    }
}
