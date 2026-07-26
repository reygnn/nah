package com.github.reygnn.nah.ui

import kotlin.math.abs

/**
 * Reine Zustandsmaschine der Links/Rechts-Wischgeste auf der Leertaste (Cursor-Positionierung),
 * aus der `TapKey`-Composable gehoben, damit die Schwellen-/Akkumulations-Logik JVM-testbar ist —
 * die Composable bleibt ein dünner Pointer-Event-Adapter (analog [LongPressGesture]).
 *
 * Übersetzt die horizontale Fingerreise in Cursor-Schritte und entscheidet beim Loslassen, ob die
 * Geste ein reiner **Tap** war (→ die Composable committet ein Leerzeichen wie bisher) oder ein
 * **Wisch** (→ nur der Cursor wurde bewegt, KEIN Leerzeichen). Bewegt selbst nie Text — der
 * eigentliche Cursor-Sprung läuft über `KeyboardViewModel.moveCursor` (DPAD-Events); das oberste
 * Gesetz (fertiger Text wird nie angetastet) bleibt unberührt.
 *
 * Bewusst ohne Compose/Android-Typ. Kein eigener Thread — wird ausschliesslich aus dem
 * (single-threaded) Pointer-Gesten-Scope von `TapKey` getrieben, also keine Synchronisierung.
 *
 * @param stepPx Fingerreise pro Cursor-Schritt (ein Zeichen); zugleich die **Totzone**: erst ab
 *   dieser Strecke kippt die Geste vom „Leerzeichen"-Tap in den Cursor-Modus.
 */
internal class SpaceSwipeGesture(private val stepPx: Float) {

    // Noch nicht in einen Cursor-Schritt umgesetzte horizontale Reise (Rest nach ganzen Stufen).
    private var accumulated = 0f

    /** Wurde je gewischt (mindestens ein Cursor-Schritt)? Dann committet [commitsSpaceOnRelease]
     *  kein Leerzeichen mehr. Die Composable spiegelt das, um im Cursor-Modus die Events zu
     *  verbrauchen (kein Parent-Scroll) und den Ripple/Tap sauber zu trennen. */
    var moved = false
        private set

    /**
     * Verarbeitet ein horizontales Delta [dx] (in px, +rechts / -links) und liefert die daraus
     * fälligen Cursor-Schritte (positiv = rechts, negativ = links, `0` = noch in der Totzone bzw.
     * keine ganze Stufe erreicht). Innerhalb ±[stepPx] um die Ausgangslage passiert nichts, damit
     * ein leicht wackelnder Tap nicht schon als Wisch zählt; ab der ersten ganzen Stufe [moved].
     */
    fun onDrag(dx: Float): Int {
        accumulated += dx
        // Totzone: bevor je gewischt wurde, erst ab einer vollen Stufe reagieren.
        if (!moved && abs(accumulated) < stepPx) return 0
        val steps = (accumulated / stepPx).toInt() // trunkiert Richtung 0 → volle Stufen
        if (steps != 0) {
            moved = true
            accumulated -= steps * stepPx // Rest behalten, damit die nächste Stufe exakt eine weitere ist
        }
        return steps
    }

    /** Nach dem Loslassen: committet die Geste ein Leerzeichen? Nur, wenn nie gewischt wurde. */
    val commitsSpaceOnRelease: Boolean get() = !moved
}
