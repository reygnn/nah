package com.github.reygnn.nah.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reine Klassifikation der „Stützräder"-Farben — Vokal / häufiger Konsonant / neutral. */
class NahColorsTest {

    @Test
    fun `Vokale inkl Umlaute werden als Vokal erkannt`() {
        for (c in "aeiouäöü") {
            assertEquals("'$c' sollte Vokal sein", NahColors.Hint.Vowel, NahColors.hintFor(c))
        }
    }

    @Test
    fun `haeufige Konsonanten werden erkannt`() {
        for (c in "nrstd") {
            assertEquals("'$c' sollte Konsonant sein", NahColors.Hint.Consonant, NahColors.hintFor(c))
        }
    }

    @Test
    fun `uebrige Buchstaben sind neutral`() {
        for (c in "qcbfykpvhglxzwjm") {
            assertNull("'$c' sollte neutral sein", NahColors.hintFor(c))
        }
    }

    @Test
    fun `Grossbuchstaben werden gleich klassifiziert`() {
        assertEquals(NahColors.Hint.Vowel, NahColors.hintFor('A'))
        assertEquals(NahColors.Hint.Consonant, NahColors.hintFor('N'))
    }
}
