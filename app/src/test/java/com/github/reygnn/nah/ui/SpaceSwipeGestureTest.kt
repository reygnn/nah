package com.github.reygnn.nah.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinnt die **Schwellen-/Akkumulations-Logik** der Space-Wisch-Cursor-Geste, die sonst in der
 * `awaitEachGesture`-Schleife von `TapKey` läge und ungetestet wäre: Totzone (→ reiner Tap
 * committet ein Leerzeichen), Umkippen in den Cursor-Modus, ganze Stufen aus akkumulierter Reise,
 * Rest-Übertrag, Richtungswechsel.
 *
 * [stepPx] = Fingerreise pro Cursor-Schritt und zugleich Totzone.
 */
class SpaceSwipeGestureTest {

    private val step = 18f

    @Test
    fun `kleiner Wackler bleibt in der Totzone und committet ein Leerzeichen`() {
        val gesture = SpaceSwipeGesture(step)
        assertEquals(0, gesture.onDrag(4f))
        assertEquals(0, gesture.onDrag(-6f)) // hin und her, nie eine ganze Stufe
        assertFalse(gesture.moved)
        assertTrue(gesture.commitsSpaceOnRelease) // → Tap = Leerzeichen wie bisher
    }

    @Test
    fun `eine volle Stufe nach rechts bewegt den Cursor und unterdrueckt das Leerzeichen`() {
        val gesture = SpaceSwipeGesture(step)
        assertEquals(1, gesture.onDrag(step))
        assertTrue(gesture.moved)
        assertFalse(gesture.commitsSpaceOnRelease) // gewischt → KEIN Leerzeichen
    }

    @Test
    fun `nach links liefert negative Schritte`() {
        val gesture = SpaceSwipeGesture(step)
        assertEquals(-1, gesture.onDrag(-step))
    }

    @Test
    fun `ein schneller Wisch liefert mehrere Stufen auf einmal`() {
        val gesture = SpaceSwipeGesture(step)
        assertEquals(3, gesture.onDrag(3.4f * step)) // 3 ganze Stufen, 0.4 Rest
    }

    @Test
    fun `der Rest wird uebertragen, sodass die naechste Stufe exakt eine weitere ist`() {
        val gesture = SpaceSwipeGesture(step)
        assertEquals(0, gesture.onDrag(0.6f * step)) // Totzone noch nicht durchbrochen
        assertEquals(1, gesture.onDrag(0.6f * step)) // 1.2 step akkumuliert → 1 Schritt, 0.2 Rest
        assertEquals(0, gesture.onDrag(0.6f * step)) // 0.8 step → noch keine ganze Stufe
        assertEquals(1, gesture.onDrag(0.6f * step)) // 1.4 step → die nächste Stufe
    }

    @Test
    fun `nach dem Umkippen zaehlt schon eine kleine Bewegung, aber ohne ganze Stufe kein Schritt`() {
        val gesture = SpaceSwipeGesture(step)
        assertEquals(1, gesture.onDrag(step)) // Cursor-Modus an
        assertEquals(0, gesture.onDrag(2f)) // kleine Restbewegung, keine ganze Stufe → 0 …
        assertTrue(gesture.moved) //  … aber bleibt gewischt
        assertFalse(gesture.commitsSpaceOnRelease)
    }

    @Test
    fun `Richtungswechsel innerhalb der Geste kehrt das Vorzeichen um`() {
        val gesture = SpaceSwipeGesture(step)
        assertEquals(2, gesture.onDrag(2f * step)) // nach rechts
        assertEquals(-3, gesture.onDrag(-3f * step)) // dann zurück nach links
    }
}
