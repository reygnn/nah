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
     * Hebt Score und Serie als **zwei unabhängige Maxima** an: jedes Feld wird nur überschrieben, wenn
     * der neue Wert das gespeicherte echt übertrifft — getrennt, nicht als Paar. So ist `bestScore`
     * stets der höchste je erreichte Punktestand und `bestStreak` die längste je erreichte Serie, auch
     * wenn sie aus verschiedenen Läufen stammen. Beide Felder sind damit monoton; der Aufruf ist
     * idempotent: ein in beiden Feldern gleich gutes oder schlechteres Paar ändert nichts.
     */
    suspend fun recordBest(score: Int, streak: Int) {
        context.dojoStatsDataStore.edit { prefs ->
            val curScore = prefs[Keys.bestScore] ?: 0
            val curStreak = prefs[Keys.bestStreak] ?: 0
            if (score > curScore) prefs[Keys.bestScore] = score
            if (streak > curStreak) prefs[Keys.bestStreak] = streak
        }
    }

    /** Setzt die Bestwerte zurück. Bewusst klein gehalten — Andockpunkt für ein späteres
     *  „Bestwert löschen" und der Reset für isolierte Test-Läufe. */
    suspend fun clear() {
        context.dojoStatsDataStore.edit { it.clear() }
    }
}
