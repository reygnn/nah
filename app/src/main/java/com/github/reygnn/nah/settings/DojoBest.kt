package com.github.reygnn.nah.settings

import com.github.reygnn.nah.viewmodel.DojoLevel
import com.github.reygnn.nah.viewmodel.LevelBest

/**
 * Persistierte Dojo-Rekorde — **pro Stufe** (Modi zusammengefasst), je Stufe ein [LevelBest] aus zwei
 * unabhängigen Höchstständen (Score und Serie müssen nicht aus demselben Lauf stammen). Per Stufe statt
 * global, weil das längen-skalierte Wort-Scoring einen einzigen globalen Rekord sonst auf den
 * leichtesten Pool (Vokale) reduzierte. Der einzige Dojo-Zustand, der das Verlassen überlebt; der
 * laufende Spielstand absichtlich nicht.
 *
 * Eine fehlende Stufe in [byLevel] zählt als Nullrekord (siehe [forLevel]) — so bleibt ein um eine
 * Stufe erweitertes Enum abwärtskompatibel, ohne dass eine alte gespeicherte Datei migriert werden muss.
 */
data class DojoBest(val byLevel: Map<DojoLevel, LevelBest> = emptyMap()) {
    /** Der Rekord der gegebenen Stufe, Default-Nullrekord, solange die Stufe noch keinen hat. */
    fun forLevel(level: DojoLevel): LevelBest = byLevel[level] ?: LevelBest()
}
