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
    fun `gleiche Frequenz wird alphabetisch sortiert (deterministisch)`() {
        val t = Trie().apply {
            insert("birne", 500)
            insert("apfel", 500)
        }
        assertEquals(listOf("apfel", "birne"), t.getSuggestions("", limit = 5).map { it.first })
    }

    @Test
    fun `contains erkennt Woerter`() {
        val t = trie()
        assertTrue(t.contains("hallo"))
        assertFalse(t.contains("hal"))
    }
}

class SuggestionRepositoryTest {

    // Im Test synchron vorgebaut (kein Hintergrund-Dispatcher nötig).
    private val repo = SuggestionRepository().apply { warmUpBuiltIn() }

    @Test
    fun `kurzes Praefix liefert keine Vorschlaege`() {
        assertTrue(repo.suggest("h", includeBuiltIn = true, includeUser = false).isEmpty())
    }

    @Test
    fun `eingebaute Vorschlaege erscheinen erst nach warmUp`() {
        val cold = SuggestionRepository()
        // Vor dem Warmup ist der eingebaute Trie noch nicht da → nicht-eingreifend leer.
        assertTrue(cold.suggest("ha", includeBuiltIn = true, includeUser = false).isEmpty())
        cold.warmUpBuiltIn()
        assertTrue(cold.suggest("ha", includeBuiltIn = true, includeUser = false).isNotEmpty())
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
        val r = SuggestionRepository().apply { warmUpBuiltIn() }
        r.setUserWords(setOf("haxyz"))
        // Ohne User-Quelle: das eigene Wort taucht nicht auf.
        assertTrue(r.suggest("ha", includeBuiltIn = true, includeUser = false).none { it == "haxyz" })
        // Mit User-Quelle: hohe Frequenz → ganz vorne.
        assertEquals("haxyz", r.suggest("ha", includeBuiltIn = true, includeUser = true).first())
    }

    @Test
    fun `numerisches eigenes Wort wird per Ziffern-Praefix vorgeschlagen`() {
        val r = SuggestionRepository()
        r.setUserWords(setOf("8050")) // z. B. eine PLZ
        assertEquals(listOf("8050"), r.suggest("80", includeBuiltIn = false, includeUser = true))
    }

    @Test
    fun `Phrase wird ueber den Anfang des ersten Wortes vorgeschlagen`() {
        val r = SuggestionRepository()
        r.setUserWords(setOf("Hauptstrasse 115"))
        // strncmp-from-start: das erste Wort matcht …
        assertEquals(
            listOf("Hauptstrasse 115"),
            r.suggest("haupt", includeBuiltIn = false, includeUser = true),
        )
        // … aber nicht ein Teilstring mitten drin (kein strstr).
        assertTrue(r.suggest("strasse", includeBuiltIn = false, includeUser = true).isEmpty())
    }

    @Test
    fun `isUserWord erkennt eigene Eintraege case-insensitiv, eingebaute nicht`() {
        val r = SuggestionRepository().apply { warmUpBuiltIn() }
        r.setUserWords(setOf("Müller", "max@firma.ch"))
        assertTrue(r.isUserWord("Müller"))
        assertTrue(r.isUserWord("müller")) // case-insensitiv (Trie.contains)
        assertTrue(r.isUserWord("max@firma.ch"))
        // Ein reines Wörterbuch-Wort ist KEIN eigenes Wort → bekommt Präfix-Casing.
        assertFalse(r.isUserWord("Zeit"))
    }

    @Test
    fun `isUserWord ist false ohne gesetzte eigene Woerter`() {
        assertFalse(SuggestionRepository().isUserWord("egal"))
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
