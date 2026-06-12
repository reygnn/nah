package com.github.reygnn.nah.data.suggestions

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// Korrupte Datei einmalig durch Defaults (leere Liste) ersetzen statt bei jedem Lesen zu werfen —
// die gelernten Wörter gingen dann zwar verloren, aber Tastatur/Einstellungen bleiben benutzbar.
private val Context.learnedWordsDataStore: DataStore<Preferences> by
    preferencesDataStore(
        name = "nah_learned_words",
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

private val LEARNED_WORDS_KEY = stringSetPreferencesKey("learned_words")

/**
 * Beim Tippen „gelernte" Wörter (das Lesezeichen-Chip in der Vorschlagsleiste), persistiert als
 * String-Set in einem **eigenen** DataStore — bewusst getrennt von den kuratierten [UserWordRepository]-
 * Wörtern, weil beide unterschiedlich committet werden: kuratierte Wörter **wörtlich** (Namen/Adressen/
 * E-Mails, Schreibweise ist massgeblich), gelernte Wörter dagegen **wie Wörterbuch-Wörter** an Shift/
 * Caps-Lock angepasst (siehe `SuggestionRepository.isLearnedWord` / `KeyboardViewModel.committedForm`).
 *
 * Gespeichert wird die **getippte Schreibweise** als Basis; das Präfix-Casing legt sich beim Antippen
 * darüber. Wie [UserWordRepository] ändert dieser Store **nie** fertigen Text, sondern liefert nur eine
 * zusätzliche Vorschlagsquelle. Gemeinsame Mechanik (inkl. [add]/[update]/[remove]) in [StringSetWordStore].
 */
class LearnedWordRepository(context: Context) :
    StringSetWordStore(context.learnedWordsDataStore, LEARNED_WORDS_KEY)
