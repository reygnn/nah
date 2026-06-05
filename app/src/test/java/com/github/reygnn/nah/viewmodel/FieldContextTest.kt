package com.github.reygnn.nah.viewmodel

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pinnt die Bitmasken-Ableitung der Return-Action aus `imeOptions` — die früher
 * untestbar im IME-Service sass. Die `IME_*`-Konstanten sind Compile-Zeit-Konstanten
 * und damit ohne Android-Runtime (kein Robolectric) verfügbar.
 */
class FieldContextTest {

    @Test
    fun `eine angeforderte Action wird durchgereicht`() {
        assertEquals(
            EditorInfo.IME_ACTION_SEARCH,
            FieldContext.fromImeOptions(EditorInfo.IME_ACTION_SEARCH).imeAction,
        )
    }

    @Test
    fun `zusaetzliche Flags neben der Action werden ausmaskiert`() {
        val opts = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_FULLSCREEN
        assertEquals(EditorInfo.IME_ACTION_SEND, FieldContext.fromImeOptions(opts).imeAction)
    }

    @Test
    fun `NO_ENTER_ACTION unterdrueckt selbst eine gesetzte Action`() {
        val opts = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertNull(FieldContext.fromImeOptions(opts).imeAction)
    }

    @Test
    fun `IME_ACTION_NONE ergibt keine Action (echtes Enter)`() {
        assertNull(FieldContext.fromImeOptions(EditorInfo.IME_ACTION_NONE).imeAction)
    }

    @Test
    fun `IME_ACTION_UNSPECIFIED ergibt keine Action (echtes Enter)`() {
        assertNull(FieldContext.fromImeOptions(EditorInfo.IME_ACTION_UNSPECIFIED).imeAction)
    }
}
