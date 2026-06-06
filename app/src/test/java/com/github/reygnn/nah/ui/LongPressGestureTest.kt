package com.github.reygnn.nah.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinnt die **Zustandsübergänge** der Long-Press-Geste, die zuvor in der `awaitEachGesture`-Schleife
 * von `TapKey` eingeschlossen und damit ungetestet waren: öffnen → verfolgen → auswählen/abbrechen →
 * Cleanup. Die reine Geometrie (Höhe → Chip) deckt zusätzlich [TapKeyGeometryTest] ab.
 *
 * y-Konvention (wie [chipIndexForY]): 0 = Tastenoberkante, +y nach unten, -y nach oben. Finger auf
 * der Taste (0..keyHeight) → Chip 0; je [chipH] weiter über die Oberkante → der nächsthöhere Chip.
 */
class LongPressGestureTest {

    private val chipH = 50f // CHIP_HEIGHT in px
    private val keyH = 58f // Tastenhöhe in px

    @Test
    fun `Halten ohne Schieben waehlt das erste Item`() {
        val gesture = LongPressGesture(itemCount = 3)
        gesture.onLongPressBegin(y = 10f, chipH, keyH) // Finger ruht auf der Taste
        assertTrue(gesture.popupOpen)
        assertEquals(0, gesture.highlight)
        assertEquals(0, gesture.onRelease()) // committet Item 0 (z. B. ä)
        assertFalse(gesture.popupOpen) // aufgeräumt
    }

    @Test
    fun `nach oben gezogen waehlt hoehere Items`() {
        val gesture = LongPressGesture(itemCount = 3)
        gesture.onLongPressBegin(0f, chipH, keyH)
        gesture.onMove(y = -1.5f * chipH, chipH, keyH) // gut eine Chip-Höhe über die Oberkante
        assertEquals(1, gesture.highlight)
        assertEquals(1, gesture.onRelease())
    }

    @Test
    fun `weiter als das letzte Item wird auf count-1 geklemmt`() {
        val gesture = LongPressGesture(itemCount = 2)
        gesture.onLongPressBegin(0f, chipH, keyH)
        gesture.onMove(y = -10f * chipH, chipH, keyH) // weit über alle Chips hinaus
        assertEquals(1, gesture.highlight) // geklemmt, kein Index-Überlauf
        assertEquals(1, gesture.onRelease())
    }

    @Test
    fun `unter die Taste gezogen bricht ab und committet nichts`() {
        val gesture = LongPressGesture(itemCount = 3)
        gesture.onLongPressBegin(0f, chipH, keyH)
        gesture.onMove(y = keyH + 20f, chipH, keyH) // unter die Unterkante
        assertEquals(CHIP_CANCEL, gesture.highlight)
        assertNull(gesture.onRelease()) // kein Commit
    }

    @Test
    fun `hoch und wieder zurueck auf die Taste ist erneut Item 0`() {
        val gesture = LongPressGesture(itemCount = 3)
        gesture.onLongPressBegin(0f, chipH, keyH)
        gesture.onMove(-2f * chipH, chipH, keyH) // hoch zu Item 2
        assertEquals(2, gesture.highlight)
        gesture.onMove(15f, chipH, keyH) // zurück auf die Taste
        assertEquals(0, gesture.highlight) // wieder wählbar — kein „Klebenbleiben"
    }

    @Test
    fun `Abbruch mitten im Halten raeumt auf und committet nichts`() {
        val gesture = LongPressGesture(itemCount = 3)
        gesture.onLongPressBegin(-2f * chipH, chipH, keyH)
        assertTrue(gesture.popupOpen)
        gesture.onCancel() // try/finally-Pfad der Composable
        assertFalse(gesture.popupOpen)
        assertEquals(CHIP_CANCEL, gesture.highlight)
        assertNull(gesture.onRelease()) // nach Cancel committet ein Release nichts
    }

    @Test
    fun `leere Item-Liste haelt CHIP_CANCEL und committet nie`() {
        val gesture = LongPressGesture(itemCount = 0) // Robustheit (Popup erscheint real nur > 0)
        gesture.onLongPressBegin(0f, chipH, keyH)
        assertEquals(CHIP_CANCEL, gesture.highlight)
        assertNull(gesture.onRelease())
    }
}
