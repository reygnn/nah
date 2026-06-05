package com.github.reygnn.nah.data.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserWordValidationTest {

    @Test
    fun `gueltiges Wort wird akzeptiert`() {
        assertNull(UserWordValidation.validate("Reygnn", emptySet()))
    }

    @Test
    fun `zu kurz`() {
        assertEquals(UserWordError.TooShort, UserWordValidation.validate("a", emptySet()))
    }

    @Test
    fun `zu lang`() {
        assertEquals(UserWordError.TooLong, UserWordValidation.validate("a".repeat(51), emptySet()))
    }

    @Test
    fun `Buchstaben und Ziffern werden akzeptiert`() {
        assertNull(UserWordValidation.validate("abc1", emptySet()))
        assertNull(UserWordValidation.validate("8050", emptySet())) // PLZ
    }

    @Test
    fun `Phrasen mit Leerzeichen werden akzeptiert`() {
        assertNull(UserWordValidation.validate("Hauptstrasse 115", emptySet()))
        assertNull(UserWordValidation.validate("8050 Zürich", emptySet()))
    }

    @Test
    fun `Satzzeichen und E-Mails werden akzeptiert`() {
        assertNull(UserWordValidation.validate("Müllerstr. 1", emptySet()))
        assertNull(UserWordValidation.validate("max@firma.ch", emptySet()))
    }

    @Test
    fun `Steuerzeichen werden abgelehnt`() {
        assertEquals(UserWordError.InvalidCharacters, UserWordValidation.validate("ab\ncd", emptySet()))
    }

    @Test
    fun `Duplikat case-insensitiv`() {
        assertEquals(UserWordError.AlreadyExists, UserWordValidation.validate("Hallo", setOf("hallo")))
    }

    @Test
    fun `wird vor der Pruefung getrimmt`() {
        assertNull(UserWordValidation.validate("  hallo  ", emptySet()))
    }
}
