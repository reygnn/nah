package com.github.reygnn.nah.settings

/** Bestwerte des Tipp-Trainings — bester Score und beste Serie, global über alle Stufen/Modi. Der
 *  einzige Dojo-Zustand, der das Verlassen überlebt; der laufende Spielstand absichtlich nicht. */
data class DojoBest(val score: Int = 0, val streak: Int = 0)

/**
 * Total geordnete „besserer Lauf"-Relation: höherer [score] gewinnt, bei Score-Gleichstand die
 * längere [streak]. Hält Score und Serie als EIN Run-Paar zusammen, statt zweier unabhängiger
 * Maxima aus womöglich verschiedenen Läufen.
 *
 * Die **einzige Quelle** dieser Regel — geteilt vom laufenden Spiel
 * ([com.github.reygnn.nah.viewmodel.DojoViewModel]), vom Seed-/Persistenz-Pfad
 * ([com.github.reygnn.nah.settings.DojoActivity]) und vom persistenzseitigen Sicherungsnetz
 * ([DojoStatsRepository.recordBest]). Bewusst kein Android, keine Coroutine: rein und JVM-testbar,
 * damit alle drei Aufrufer garantiert dieselbe Ordnung benutzen und nie auseinanderlaufen.
 *
 * Reine Kotlin-Datei (kein Android-Import), damit der ViewModel sie ohne Android-Kopplung nutzen kann.
 */
fun isBetterRun(score: Int, streak: Int, thanScore: Int, thanStreak: Int): Boolean =
    score > thanScore || (score == thanScore && streak > thanStreak)
