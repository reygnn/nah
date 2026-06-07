package com.github.reygnn.nah.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.reygnn.nah.viewmodel.DojoLevel
import com.github.reygnn.nah.viewmodel.LevelBest
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

    // Zwei Keys PRO STUFE. Der Suffix ist DojoLevel.name (VOWELS/CONSONANTS/ALPHABET/WORDS) → stabil,
    // solange die Enum-Konstanten nicht umbenannt werden (täte man es, läse eine alte Datei die alten
    // Keys nicht mehr → Nullrekord für die Stufe, kein Crash). Ältere Builds speicherten EINEN globalen
    // Rekord unter „dojo_best_score"/„dojo_best_streak"; diese Keys werden nicht mehr gelesen — der
    // frühere globale Rekord wandert bewusst NICHT auf eine Stufe (er war stufenübergreifend, eine
    // ehrliche Zuordnung gibt es nicht) und verfällt beim Upgrade.
    private object Keys {
        fun score(level: DojoLevel) = intPreferencesKey("dojo_best_score_${level.name}")
        fun streak(level: DojoLevel) = intPreferencesKey("dojo_best_streak_${level.name}")
    }

    val best: Flow<DojoBest> = context.dojoStatsDataStore.data
        // Transiente Lese-IOExceptions nicht den Collector (die Dojo-Composition) töten lassen.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            DojoBest(
                DojoLevel.entries.associateWith { level ->
                    LevelBest(prefs[Keys.score(level)] ?: 0, prefs[Keys.streak(level)] ?: 0)
                },
            )
        }

    /**
     * Hebt Score und Serie **jeder Stufe** als **zwei unabhängige Maxima** an: jedes Feld wird nur
     * überschrieben, wenn der neue Wert das gespeicherte echt übertrifft — getrennt, nicht als Paar, und
     * je Stufe für sich. So ist der Score einer Stufe stets ihr höchster je erreichter Punktestand und
     * ihre Serie die längste, auch wenn sie aus verschiedenen Läufen stammen. Alle Felder sind damit
     * monoton; der Aufruf ist idempotent: ein in keinem Feld besseres [best] ändert nichts.
     */
    suspend fun recordBest(best: DojoBest) {
        context.dojoStatsDataStore.edit { prefs ->
            for ((level, lb) in best.byLevel) {
                val curScore = prefs[Keys.score(level)] ?: 0
                val curStreak = prefs[Keys.streak(level)] ?: 0
                if (lb.score > curScore) prefs[Keys.score(level)] = lb.score
                if (lb.streak > curStreak) prefs[Keys.streak(level)] = lb.streak
            }
        }
    }

    /** Setzt die Bestwerte zurück. Bewusst klein gehalten — Andockpunkt für ein späteres
     *  „Bestwert löschen" und der Reset für isolierte Test-Läufe. */
    suspend fun clear() {
        context.dojoStatsDataStore.edit { it.clear() }
    }
}
