package com.github.reygnn.nah.settings

/**
 * Bestwerte des Tipp-Trainings — bester Score und beste Serie, **unabhängig** über alle Stufen/Modi.
 * Die beiden Felder sind getrennte Höchststände: `score` ist der höchste je erreichte Punktestand,
 * `streak` die längste je erreichte Serie — sie müssen nicht aus demselben Lauf stammen (eine lange
 * Serie zählt als Rekord, auch wenn der Punkt-Bestlauf eine kürzere hatte). Der einzige Dojo-Zustand,
 * der das Verlassen überlebt; der laufende Spielstand absichtlich nicht.
 */
data class DojoBest(val score: Int = 0, val streak: Int = 0)
