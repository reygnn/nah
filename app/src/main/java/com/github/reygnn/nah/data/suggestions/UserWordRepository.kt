package com.github.reygnn.nah.data.suggestions

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// Korrupte Datei einmalig durch Defaults (leere Liste) ersetzen statt bei jedem Lesen zu werfen —
// die kuratierte Wortliste ginge dann zwar verloren, aber Tastatur/Einstellungen bleiben benutzbar.
private val Context.userWordsDataStore: DataStore<Preferences> by
    preferencesDataStore(
        name = "nah_user_words",
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

private val USER_WORDS_KEY = stringSetPreferencesKey("user_words")

/**
 * **Kuratierte** benutzerdefinierte Wörter (über die Verwaltung hinzugefügt/bearbeitet), persistiert
 * als String-Set in DataStore (kein Room — das Abfragen erledigt der [WordIndex], hier wird nur
 * gehalten). Speist als zweite Quelle in [SuggestionRepository] ein und wird **wörtlich** committet
 * (Namen/Adressen/E-Mails, Schreibweise ist massgeblich — siehe `SuggestionRepository.isUserWord`).
 * Gegenstück: [LearnedWordRepository] (beim Tippen gelernt, an Shift/Caps angepasst). Ändert **nie**
 * fertigen Text, sondern liefert nur Vorschläge. Mechanik in [StringSetWordStore].
 */
class UserWordRepository(context: Context) :
    StringSetWordStore(context.userWordsDataStore, USER_WORDS_KEY)
