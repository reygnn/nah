package com.github.reygnn.nah.data.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordBackupTest {

    @Test
    fun `encode then decode round-trips both lists`() {
        val user = setOf("Hauptstrasse 115", "max@firma.ch", "Zürich")
        val learned = setOf("nah", "Grüezi")

        val decoded = WordBackup.decode(WordBackup.encode(user, learned))

        assertNotNull(decoded)
        assertEquals(user, decoded!!.userWords.toSet())
        assertEquals(learned, decoded.learnedWords.toSet())
    }

    @Test
    fun `encode sorts case-insensitively and tags format and version`() {
        val json = WordBackup.encode(setOf("beta", "Alpha"), emptySet())

        assertTrue(json.indexOf("Alpha") < json.indexOf("beta"))
        assertTrue(json.contains("\"format\": \"nah-words\""))
        assertTrue(json.contains("\"version\": 1"))
    }

    @Test
    fun `decode rejects malformed JSON`() {
        assertNull(WordBackup.decode("{not json"))
        assertNull(WordBackup.decode(""))
    }

    @Test
    fun `decode rejects a foreign JSON file`() {
        assertNull(WordBackup.decode("""{"format":"something-else","userWords":["x"]}"""))
        assertNull(WordBackup.decode("""{"userWords":"not a list"}"""))
    }

    @Test
    fun `decode rejects a newer format version`() {
        assertNull(WordBackup.decode("""{"format":"nah-words","version":2,"userWords":["Hallo"]}"""))
    }

    @Test
    fun `decode tolerates unknown keys and missing lists`() {
        val decoded = WordBackup.decode("""{"format":"nah-words","version":1,"extra":true,"userWords":["Hallo"]}""")

        assertEquals(listOf("Hallo"), decoded!!.userWords)
        assertEquals(emptyList<String>(), decoded.learnedWords)
    }

    @Test
    fun `merge only adds, keeps existing entries untouched`() {
        val result = WordBackup.merge(setOf("Reygnn"), listOf("Zürich", "8050"))

        assertEquals(setOf("Reygnn", "Zürich", "8050"), result.words)
        assertEquals(2, result.added)
    }

    @Test
    fun `merge skips case-insensitive duplicates, also within the file`() {
        val result = WordBackup.merge(setOf("Hallo"), listOf("hallo", "Welt", "WELT"))

        assertEquals(setOf("Hallo", "Welt"), result.words)
        assertEquals(1, result.added)
    }

    @Test
    fun `merge trims and skips invalid entries`() {
        val result = WordBackup.merge(emptySet(), listOf("  Bern  ", "a", "ab\ncd", "x".repeat(51)))

        assertEquals(setOf("Bern"), result.words)
        assertEquals(1, result.added)
    }

    @Test
    fun `importing the same backup twice adds nothing the second time`() {
        val incoming = listOf("Hallo", "Welt")
        val first = WordBackup.merge(emptySet(), incoming)
        val second = WordBackup.merge(first.words, incoming)

        assertEquals(first.words, second.words)
        assertEquals(0, second.added)
    }
}
