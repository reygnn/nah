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
    fun `Ziffern werden abgelehnt`() {
        assertEquals(UserWordError.InvalidCharacters, UserWordValidation.validate("abc1", emptySet()))
    }

    @Test
    fun `plausible E-Mail wird akzeptiert`() {
        assertNull(UserWordValidation.validate("a@b.ch", emptySet()))
    }

    @Test
    fun `kaputte E-Mail wird abgelehnt`() {
        assertEquals(UserWordError.InvalidCharacters, UserWordValidation.validate("a@@b.ch", emptySet()))
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
