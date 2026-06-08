package com.github.reygnn.nah.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pinnt die „mindestens X"-Regel für den unteren Tastatur-Abstand: [bottomKeyboardInset] nimmt den
 * realen System-Inset, fällt aber nie unter die Marge über der Gestenzone. Rein → ohne
 * Compose/Android JVM-testbar (`Dp` ist ein reiner Value-Typ), wie [TapKeyGeometryTest].
 */
class BottomInsetTest {

    @Test
    fun `Mindest-Abstand gewinnt, wenn der System-Inset kleiner ist (Pixel 9a, Gestennavigation)`() {
        // Gesten-Inset (~24dp) < Marge (48dp) → die Marge trägt.
        assertEquals(48.dp, bottomKeyboardInset(systemNavBottom = 24.dp, minClearance = 48.dp))
    }

    @Test
    fun `System-Inset gewinnt, wenn er groesser ist (hohe Drei-Knopf-Leiste)`() {
        // Drei-Knopf-Leiste (~56dp) > Marge (48dp) → keine Kollision, der reale Inset trägt.
        assertEquals(56.dp, bottomKeyboardInset(systemNavBottom = 56.dp, minClearance = 48.dp))
    }

    @Test
    fun `Gleichstand ist stabil`() {
        assertEquals(48.dp, bottomKeyboardInset(systemNavBottom = 48.dp, minClearance = 48.dp))
    }
}
