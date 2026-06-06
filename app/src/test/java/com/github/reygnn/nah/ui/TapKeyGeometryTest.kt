package com.github.reygnn.nah.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pinnt die Geometrie des **vertikalen** Long-Press-Menüs: [chipIndexFor] wählt rein über die
 * Finger-Höhe (y), und [bandLeftInWindow] zentriert die eine Chip breite Spalte über der Taste
 * (an den Fensterrand geclampt). Kernverhalten: Halten ohne Bewegung → Chip 0 (Default, z. B. ä),
 * nach oben schieben → höhere Chips, unter die Taste ziehen → [CHIP_CANCEL] (Abbruch).
 * Beide Funktionen sind rein und damit ohne Compose/Android JVM-testbar.
 */
class TapKeyGeometryTest {

    private val chipHeightPx = 50f
    private val chipWidthPx = 46f
    private val count = 3
    private val windowPx = 400f
    private val keyHeightPx = 58f

    /** Finger-y (tastenlokal) für die Mitte von Chip [index]: Chip 0 sitzt direkt über der
     *  Tastenoberkante (y < 0), jeder weitere eine Chip-Höhe höher. */
    private fun yForChipCenter(index: Int): Float = -(index + 0.5f) * chipHeightPx

    @Test
    fun `Halten ohne Bewegung waehlt Chip 0 (Default)`() {
        // Finger ruht auf der Taste (0 ≤ y ≤ keyHeight) → erstes Item, ohne zu schieben.
        assertEquals(0, chipIndexFor(Offset(x = 20f, y = 5f), chipHeightPx, count, keyHeightPx))
        assertEquals(0, chipIndexFor(Offset(x = 20f, y = keyHeightPx), chipHeightPx, count, keyHeightPx))
    }

    @Test
    fun `nach oben schieben trifft jeden Chip der Reihe nach`() {
        for (i in 0 until count) {
            assertEquals(i, chipIndexFor(Offset(x = 0f, y = yForChipCenter(i)), chipHeightPx, count, keyHeightPx))
        }
    }

    @Test
    fun `Auswahl ist unabhaengig von der Finger-X`() {
        // Egal ob links oder rechts vom Tastenzentrum — nur die Höhe zählt (eine Spalte).
        assertEquals(1, chipIndexFor(Offset(x = -200f, y = yForChipCenter(1)), chipHeightPx, count, keyHeightPx))
        assertEquals(1, chipIndexFor(Offset(x = 200f, y = yForChipCenter(1)), chipHeightPx, count, keyHeightPx))
    }

    @Test
    fun `weit ueber das Menue hinaus wird auf den obersten Chip begrenzt`() {
        assertEquals(count - 1, chipIndexFor(Offset(x = 0f, y = -1000f), chipHeightPx, count, keyHeightPx))
    }

    @Test
    fun `Finger unter die Taste gezogen bricht ab`() {
        assertEquals(CHIP_CANCEL, chipIndexFor(Offset(x = 20f, y = keyHeightPx + 1f), chipHeightPx, count, keyHeightPx))
    }

    @Test
    fun `mittige Taste - Spalte sitzt zentriert ueber der Taste`() {
        val keyLeft = 177f // Taste (Breite 46) mittig: Center 200
        val bandLeft = bandLeftInWindow(keyLeft, chipWidthPx, chipWidthPx, windowPx)
        assertEquals(177f, bandLeft, 0.01f) // 200 - 23, nicht geclampt
    }

    @Test
    fun `linke Rand-Taste - Spalte wird auf 0 geclampt`() {
        val keyLeft = -10f // Center 13 → gewünschte Kante -10 → geclampt auf 0
        val bandLeft = bandLeftInWindow(keyLeft, chipWidthPx, chipWidthPx, windowPx)
        assertEquals(0f, bandLeft, 0.01f)
    }

    @Test
    fun `rechte Rand-Taste - Spalte wird an die Fensterkante geclampt`() {
        val keyLeft = 380f // Center 403 → gewünscht 380 → geclampt auf maxLeft = 400-46 = 354
        val bandLeft = bandLeftInWindow(keyLeft, chipWidthPx, chipWidthPx, windowPx)
        assertEquals(354f, bandLeft, 0.01f)
    }
}
