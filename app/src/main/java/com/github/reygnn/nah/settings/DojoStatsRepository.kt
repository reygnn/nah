package com.github.reygnn.nah.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Eigener Store, getrennt von den Einstellungen — andere Lebensdauer und Eigentümer (das Dojo, nicht
// die Tastatur). Korruptions-/IOException-Behandlung wie in SettingsRepository: lieber auf Null-
// Bestwerte zurückfallen als das Dojo lahmlegen.
private val Context.dojoStatsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nah_dojo_stats",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Bestwerte des Tipp-Trainings — bester Score und beste Serie, global über alle Stufen/Modi. Der
 *  einzige Dojo-Zustand, der das Verlassen überlebt; der laufende Spielstand absichtlich nicht. */
data class DojoBest(val score: Int = 0, val streak: Int = 0)

class DojoStatsRepository(private val context: Context) {

    private object Keys {
        val bestScore = intPreferencesKey("dojo_best_score")
        val bestStreak = intPreferencesKey("dojo_best_streak")
    }

    val best: Flow<DojoBest> = context.dojoStatsDataStore.data
        // Transiente Lese-IOExceptions nicht den Collector (die Dojo-Composition) töten lassen.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { DojoBest(it[Keys.bestScore] ?: 0, it[Keys.bestStreak] ?: 0) }

    /**
     * Hält [score]/[streak] als Bestwerte fest — **monoton**, also nie unter den gespeicherten Wert.
     * Damit ist der Aufruf idempotent: der ViewModel führt seinen Bestwert ohnehin schon als Maximum,
     * und ein Schreiben mit demselben oder niedrigeren Wert ändert nichts.
     */
    suspend fun recordBest(score: Int, streak: Int) {
        context.dojoStatsDataStore.edit { prefs ->
            prefs[Keys.bestScore] = maxOf(prefs[Keys.bestScore] ?: 0, score)
            prefs[Keys.bestStreak] = maxOf(prefs[Keys.bestStreak] ?: 0, streak)
        }
    }

    /** Setzt die Bestwerte zurück. Bewusst klein gehalten — Andockpunkt für ein späteres
     *  „Bestwert löschen" und der Reset für isolierte Test-Läufe. */
    suspend fun clear() {
        context.dojoStatsDataStore.edit { it.clear() }
    }
}
