package com.github.reygnn.nah.viewmodel

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinnt die Ableitung der Feld-Eigenschaften aus den rohen `EditorInfo`-Werten — die
 * früher untestbar im IME-Service sass. Die `IME_*`/`InputType.TYPE_*`-Konstanten sind
 * Compile-Zeit-Konstanten und damit ohne Android-Runtime (kein Robolectric) verfügbar.
 */
class FieldContextTest {

    /** Bequemer Aufruf mit neutralem Textfeld-Default für die Felder, die ein Test nicht prüft. */
    private fun ctx(
        imeOptions: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        initialSelStart: Int = 0,
        initialSelEnd: Int = 0,
    ) = FieldContext.fromEditorInfo(imeOptions, inputType, initialSelStart, initialSelEnd)

    @Test
    fun `eine angeforderte Action wird durchgereicht`() {
        assertEquals(EditorInfo.IME_ACTION_SEARCH, ctx(imeOptions = EditorInfo.IME_ACTION_SEARCH).imeAction)
    }

    @Test
    fun `zusaetzliche Flags neben der Action werden ausmaskiert`() {
        val opts = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_FULLSCREEN
        assertEquals(EditorInfo.IME_ACTION_SEND, ctx(imeOptions = opts).imeAction)
    }

    @Test
    fun `NO_ENTER_ACTION unterdrueckt selbst eine gesetzte Action`() {
        val opts = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertNull(ctx(imeOptions = opts).imeAction)
    }

    @Test
    fun `IME_ACTION_NONE ergibt keine Action (echtes Enter)`() {
        assertNull(ctx(imeOptions = EditorInfo.IME_ACTION_NONE).imeAction)
    }

    @Test
    fun `IME_ACTION_UNSPECIFIED ergibt keine Action (echtes Enter)`() {
        assertNull(ctx(imeOptions = EditorInfo.IME_ACTION_UNSPECIFIED).imeAction)
    }

    @Test
    fun `ein reines Textfeld ist weder numerisch noch Passwort`() {
        val field = ctx(inputType = InputType.TYPE_CLASS_TEXT)
        assertFalse(field.numeric)
        assertFalse(field.isPassword)
    }

    @Test
    fun `Zahl-, Telefon- und Datumsfeld starten numerisch`() {
        assertTrue(ctx(inputType = InputType.TYPE_CLASS_NUMBER).numeric)
        assertTrue(ctx(inputType = InputType.TYPE_CLASS_PHONE).numeric)
        assertTrue(ctx(inputType = InputType.TYPE_CLASS_DATETIME).numeric)
    }

    @Test
    fun `nur das Telefonfeld ist phone, Zahl und Datum nicht`() {
        val phone = ctx(inputType = InputType.TYPE_CLASS_PHONE)
        assertTrue(phone.phone)
        assertTrue(phone.numeric) // phone impliziert numeric
        assertFalse(ctx(inputType = InputType.TYPE_CLASS_NUMBER).phone)
        assertFalse(ctx(inputType = InputType.TYPE_CLASS_DATETIME).phone)
        assertFalse(ctx(inputType = InputType.TYPE_CLASS_TEXT).phone)
    }

    @Test
    fun `Text-Passwortvarianten werden als Passwort erkannt`() {
        val plain = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val web = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        val visible = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        assertTrue(ctx(inputType = plain).isPassword)
        assertTrue(ctx(inputType = web).isPassword)
        assertTrue(ctx(inputType = visible).isPassword)
    }

    @Test
    fun `numerisches PIN-Feld ist numerisch UND Passwort`() {
        val pin = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val field = ctx(inputType = pin)
        assertTrue(field.numeric)
        assertTrue(field.isPassword)
    }

    @Test
    fun `NO_SUGGESTIONS wird nur fuer die Textklasse gelesen`() {
        val textNoSug = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertTrue(ctx(inputType = textNoSug).noSuggestions)
        assertFalse(ctx(inputType = InputType.TYPE_CLASS_TEXT).noSuggestions)
        // Dasselbe Flag-Bit bedeutet in der Number-Klasse etwas anderes → nicht als noSuggestions lesen.
        val numberSameBit = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertFalse(ctx(inputType = numberSameBit).noSuggestions)
    }

    @Test
    fun `Anfangs-Auswahl wird uebernommen, Unbekanntes (-1) auf 0 gezogen`() {
        val field = ctx(initialSelStart = 3, initialSelEnd = 7)
        assertEquals(3, field.initialSelStart)
        assertEquals(7, field.initialSelEnd)
        val unknown = ctx(initialSelStart = -1, initialSelEnd = -1)
        assertEquals(0, unknown.initialSelStart)
        assertEquals(0, unknown.initialSelEnd)
    }
}
