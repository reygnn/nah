package com.github.reygnn.nah.ui

/**
 * Reine Zustandsmaschine des Long-Press-Popups, aus der `TapKey`-Composable gehoben, damit die
 * Übergänge (öffnen / verfolgen / auswählen / abbrechen / aufräumen) JVM-testbar sind — die
 * Composable wird zum dünnen Pointer-Event-Adapter, analog `NahIme` ↔ `KeyboardViewModel`
 * (siehe app/src/test/CLAUDE.md: „Pure Logik … JVM-testen; den IME-Service nicht").
 *
 * Bewusst ohne Compose/Android-Typ: die Auswahl ist rein vertikal (siehe [chipIndexForY]), die
 * Composable reicht nur die Finger-Höhe herein. Kein eigener Thread — wird ausschliesslich aus
 * dem (single-threaded) Pointer-Gesten-Scope von `TapKey` getrieben, also keine Synchronisierung.
 */
class LongPressGesture(private val itemCount: Int) {

    /** Ob das Popup offen ist. Die Composable spiegelt das in ihren Compose-State (Recomposition). */
    var popupOpen = false
        private set

    /** Aktuell hervorgehobener Chip-Index, oder [CHIP_CANCEL] (Finger unter der Taste / Abbruch). */
    var highlight = CHIP_CANCEL
        private set

    /**
     * Long-Press ist ausgelöst: Popup öffnen und den Highlight aus der Start-Höhe [y] des Fingers
     * setzen — so committet schon ein Halten-und-Loslassen ohne jede Bewegung das erste Item.
     */
    fun onLongPressBegin(y: Float, chipHeightPx: Float, keyHeightPx: Float) {
        popupOpen = true
        highlight = chipIndexForY(y, chipHeightPx, itemCount, keyHeightPx)
    }

    /** Finger bewegt während des Haltens → Highlight neu aus der Höhe [y] bestimmen. */
    fun onMove(y: Float, chipHeightPx: Float, keyHeightPx: Float) {
        highlight = chipIndexForY(y, chipHeightPx, itemCount, keyHeightPx)
    }

    /**
     * Losgelassen: liefert den zu committenden Item-Index, oder `null` bei Abbruch / keiner Auswahl
     * (Finger unter der Taste, [CHIP_CANCEL]). Räumt den Zustand IMMER auf — ein folgendes
     * [onRelease] committet nichts mehr.
     */
    fun onRelease(): Int? {
        val selected = highlight.takeIf { it in 0 until itemCount }
        reset()
        return selected
    }

    /**
     * Geste abgebrochen (Pointer-Reset / Rekomposition mit geändertem Key während des Haltens):
     * schliessen, nichts committen. Gegenstück zum `finally` der Composable-Geste.
     */
    fun onCancel() = reset()

    private fun reset() {
        popupOpen = false
        highlight = CHIP_CANCEL
    }
}
