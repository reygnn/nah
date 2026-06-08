package com.github.reygnn.nah.data.suggestions

import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.OptimizedLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Datenintegrität des eingebauten Korpus. Die Liste darf laut CLAUDE.md wachsen, OHNE das Layout
 * anzufassen — diese Tests sind die Leitplanke dafür: ein versehentliches Duplikat, ein ß oder ein
 * nicht tippbares Zeichen fällt hier auf, statt still einen Vorschlag oder Drill-Eintrag zu
 * verschlucken. Bewusst KEINE feste Eintragszahl gepinnt — Wachstum ist erlaubt, nur sauber muss es
 * sein. Rein JVM (OptimizedLayout ist reines Kotlin), kein Android-Runtime.
 */
class GermanWordListTest {

    private val words = GermanWordList.words

    /**
     * Genau drei bewusste Gross-/Klein-Homographen teilen sich einen kleingeschriebenen Key
     * (`leben`/`Leben`, `morgen`/`Morgen`, `arm`/`Arm`) — mehr nicht. Jedes weitere Duplikat
     * verdeckte in [WordIndex] still das höher-frequente Wort (nur die höchste Frequenz überlebt
     * den Key), ohne dass es je auffiele.
     */
    @Test
    fun `nur die drei gewollten Case-Homographen kollidieren, keine versehentlichen Dubletten`() {
        val collisions = words
            .groupBy { it.first.lowercase() }
            .filterValues { it.size > 1 }
            .keys
        assertEquals(setOf("leben", "morgen", "arm"), collisions)
    }

    /**
     * de-CH kennt kein ß ("ss"). Ein ß-Wort wäre auf dem Layout gar nicht tippbar und würde aus dem
     * Dojo-Pool still herausgefiltert — hier fällt es stattdessen sofort auf.
     */
    @Test
    fun `kein Wort enthaelt ein ß`() {
        val offenders = words.map { it.first }.filter { "ß" in it }
        assertTrue("ß gefunden in: $offenders", offenders.isEmpty())
    }

    /**
     * Jedes Listenwort muss sich Zeichen für Zeichen auf dem echten Layout tippen lassen — als
     * eigener Tasten-Output oder als Long-Press-Alternative (ä/ö/ü). Sonst verschwände es
     * kommentarlos aus dem Tipp-Drill (`DojoViewModel` filtert untippbare Wörter raus). Quelle der
     * tippbaren Tokens ist das Layout selbst, nicht der Produktionsfilter — sonst prüfte der Test
     * eine Kopie seiner selbst.
     */
    @Test
    fun `jedes Wort ist Zeichen fuer Zeichen auf dem Layout tippbar`() {
        val singleCharTokens = OptimizedLayout.deCh().rows.flatten()
            .filterIsInstance<CharKey>()
            .flatMap { listOf(it.output) + it.alternatives }
            .map { it.lowercase() }
            .filter { it.length == 1 }
            .toSet()
        val untypeable = words.map { it.first }.filter { word ->
            word.any { c -> c.lowercaseChar().toString() !in singleCharTokens }
        }
        assertTrue("nicht tippbare Wörter: $untypeable", untypeable.isEmpty())
    }
}
