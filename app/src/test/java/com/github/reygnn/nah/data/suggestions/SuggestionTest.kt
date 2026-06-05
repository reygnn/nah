package com.github.reygnn.nah.data.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrieTest {

    private fun trie() = Trie().apply {
        insert("hallo", 800)
        insert("haben", 890)
        insert("hat", 890)
        insert("halten", 640)
    }

    @Test
    fun `getSuggestions liefert nach Frequenz sortiert`() {
        val result = trie().getSuggestions("ha", limit = 3).map { it.first }
        assertEquals(3, result.size)
        // "haben"/"hat" (890) vor "hallo" (800) vor "halten" (640)
        assertTrue(result.first() in setOf("haben", "hat"))
        assertTrue("halten" !in result) // von der Top-3 verdrängt
    }

    @Test
    fun `unbekanntes Praefix liefert leer`() {
        assertTrue(trie().getSuggestions("xyz", limit = 3).isEmpty())
    }

    @Test
    fun `contains erkennt Woerter`() {
        val t = trie()
        assertTrue(t.contains("hallo"))
        assertFalse(t.contains("hal"))
    }
}

class SuggestionRepositoryTest {

    private val repo = SuggestionRepository()

    @Test
    fun `kurzes Praefix liefert keine Vorschlaege`() {
        assertTrue(repo.suggest("h", includeBuiltIn = true, includeUser = false).isEmpty())
    }

    @Test
    fun `bekanntes Praefix liefert bis zu drei Vorschlaege aus der de-CH-Liste`() {
        val result = repo.suggest("ha", includeBuiltIn = true, includeUser = false)
        assertTrue(result.isNotEmpty())
        assertTrue(result.size <= 3)
        assertTrue(result.all { it.lowercase().startsWith("ha") })
    }

    @Test
    fun `user-woerter erscheinen nur wenn einbezogen und ranken vor der Liste`() {
        val r = SuggestionRepository()
        r.setUserWords(setOf("haxyz"))
        // Ohne User-Quelle: das eigene Wort taucht nicht auf.
        assertTrue(r.suggest("ha", includeBuiltIn = true, includeUser = false).none { it == "haxyz" })
        // Mit User-Quelle: hohe Frequenz → ganz vorne.
        assertEquals("haxyz", r.suggest("ha", includeBuiltIn = true, includeUser = true).first())
    }

    @Test
    fun `nur User-Quelle liefert ausschliesslich eigene Woerter`() {
        val r = SuggestionRepository()
        r.setUserWords(setOf("zzabc"))
        assertEquals(listOf("zzabc"), r.suggest("zz", includeBuiltIn = false, includeUser = true))
        // Keine Quelle aktiv → leer, selbst wenn ein Wort passen würde.
        assertTrue(r.suggest("zz", includeBuiltIn = false, includeUser = false).isEmpty())
    }
}
