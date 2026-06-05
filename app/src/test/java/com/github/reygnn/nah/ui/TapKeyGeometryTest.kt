package com.github.reygnn.nah.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pinnt die Long-Press-Popup-Geometrie: die Chip-Auswahl ([chipIndexFor]) muss exakt
 * dem treffen, was das Popup an seiner ([bandLeftInWindow]) Position anzeigt — auch wenn
 * die Taste so nah am Rand sitzt, dass das Band geclampt wird. Beide Funktionen sind rein
 * und damit ohne Compose/Android JVM-testbar.
 */
class TapKeyGeometryTest {

    private val chipPx = 46f
    private val count = 3
    private val bandPx = count * chipPx // 138
    private val windowPx = 400f

    /** Finger-Offset (tastenlokal) für die Mitte von Chip [index], gegeben die Band-Kante. */
    private fun pointerForChipCenter(keyLeft: Float, bandLeft: Float, index: Int): Offset {
        val windowX = bandLeft + index * chipPx + chipPx / 2f
        return Offset(x = windowX - keyLeft, y = -10f) // y < 0 → im Popup-Band
    }

    @Test
    fun `mittige Taste - jeder Chip wird korrekt getroffen`() {
        val keyLeft = 177f // Taste (Breite 46) mittig: Center 200
        val bandLeft = bandLeftInWindow(keyLeft, 46f, bandPx, windowPx)
        assertEquals(131f, bandLeft, 0.01f) // 200 - 69, nicht geclampt
        for (i in 0 until count) {
            assertEquals(i, chipIndexFor(pointerForChipCenter(keyLeft, bandLeft, i), keyLeft, bandLeft, chipPx, count))
        }
    }

    @Test
    fun `Finger auf der Taste (y nicht negativ) waehlt keinen Chip`() {
        val keyLeft = 177f
        val bandLeft = bandLeftInWindow(keyLeft, 46f, bandPx, windowPx)
        assertEquals(-1, chipIndexFor(Offset(x = 20f, y = 5f), keyLeft, bandLeft, chipPx, count))
    }

    @Test
    fun `linke Rand-Taste - Band wird auf 0 geclampt und der Chip stimmt trotzdem`() {
        val keyLeft = 10f // Center 33 → gewünschte Band-Kante -36 → geclampt auf 0
        val bandLeft = bandLeftInWindow(keyLeft, 46f, bandPx, windowPx)
        assertEquals(0f, bandLeft, 0.01f)
        // Chip 0 sitzt jetzt linksbündig; der Finger darüber trifft auch Chip 0
        // (die alte, tastenzentrierte Rechnung hätte hier Chip 1 geliefert → Fehl-Commit).
        assertEquals(0, chipIndexFor(pointerForChipCenter(keyLeft, bandLeft, 0), keyLeft, bandLeft, chipPx, count))
        assertEquals(2, chipIndexFor(pointerForChipCenter(keyLeft, bandLeft, 2), keyLeft, bandLeft, chipPx, count))
    }

    @Test
    fun `rechte Rand-Taste - Band wird an die Fensterkante geclampt`() {
        val keyLeft = 350f // Center 373 → gewünscht 304 → geclampt auf maxLeft = 400-138 = 262
        val bandLeft = bandLeftInWindow(keyLeft, 46f, bandPx, windowPx)
        assertEquals(262f, bandLeft, 0.01f)
        assertEquals(2, chipIndexFor(pointerForChipCenter(keyLeft, bandLeft, 2), keyLeft, bandLeft, chipPx, count))
    }

    @Test
    fun `Finger links bzw rechts vom Band wird auf die Randchips begrenzt`() {
        val keyLeft = 177f
        val bandLeft = bandLeftInWindow(keyLeft, 46f, bandPx, windowPx)
        // Weit links vom Band → Chip 0; weit rechts → letzter Chip.
        assertEquals(0, chipIndexFor(Offset(x = (bandLeft - 100f) - keyLeft, y = -10f), keyLeft, bandLeft, chipPx, count))
        assertEquals(count - 1, chipIndexFor(Offset(x = (bandLeft + bandPx + 100f) - keyLeft, y = -10f), keyLeft, bandLeft, chipPx, count))
    }
}
