package com.github.reygnn.nah.data.suggestions

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
     * One-shot read **without** the IOException fallback of [words]: a read error is thrown to the
     * caller instead of looking like an empty list. For the backup export, where a silently empty
     * list would produce an incomplete backup that still reports success. Keyboard and UI keep using
     * [words]. (A corrupt file reset to empty by the store's corruption handler still reads as empty —
     * indistinguishable from a list the user emptied.)
     */
    suspend fun read(): Set<String> = dataStore.data.first()[key] ?: emptySet()

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

    /**
     * Merges [incoming] into the store in a single transaction (backup restore). Never removes or
     * rewrites existing entries; invalid entries and duplicates are skipped (see [WordBackup.merge]).
     * Returns the number of entries actually added.
     */
    suspend fun addAll(incoming: List<String>): Int {
        var added = 0
        dataStore.edit { prefs ->
            val merged = WordBackup.merge(prefs[key] ?: emptySet(), incoming)
            added = merged.added
            if (merged.added > 0) prefs[key] = merged.words
        }
        return added
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
