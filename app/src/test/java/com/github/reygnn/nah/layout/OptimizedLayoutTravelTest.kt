package com.github.reygnn.nah.layout

import com.github.reygnn.nah.data.suggestions.GermanWordList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.hypot
import org.junit.Test

/**
 * Pinnt die Kern-Eigenschaft des optimierten Layouts: die Fingerreise für einen
 * Finger liegt deutlich unter QWERTZ-CH. Schützt davor, die Anordnung versehentlich
 * zu verschlechtern. Verwendet eine kompakte Tabelle häufiger deutscher Bigramme
 * (Rankings sind sprachstabil); die volle Optimierung liegt in tools/optimize_layout.py.
 */
class OptimizedLayoutTravelTest {

    // Häufige deutsche Bigramme mit groben relativen Gewichten.
    private val bigrams = mapOf(
        "er" to 40, "en" to 38, "ch" to 30, "de" to 26, "ei" to 24,
        "te" to 22, "in" to 22, "nd" to 20, "ie" to 19, "ge" to 16,
        "st" to 15, "ne" to 14, "be" to 13, "es" to 13, "un" to 12,
        "re" to 12, "an" to 11, "he" to 11, "se" to 10, "nt" to 10,
    )

    private fun travel(pos: Map<Char, Pair<Float, Float>>): Double =
        travel(pos, bigrams.mapKeys { (bg, _) -> bg[0] to bg[1] })

    /** Mittlere Fingerreise pro Anschlag über die gegebenen Bigramm-Gewichte. */
    private fun travel(pos: Map<Char, Pair<Float, Float>>, bg: Map<Pair<Char, Char>, Int>): Double {
        var sum = 0.0
        var total = 0
        for ((pair, w) in bg) {
            val a = pos[pair.first] ?: continue
            val b = pos[pair.second] ?: continue
            sum += w * hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble())
            total += w
        }
        return sum / total
    }

    /**
     * Frequenz-gewichtete Buchstaben-Bigramme aus dem **echten App-Korpus** ([GermanWordList])
     * — dieselbe Liste, die auch `tools/optimize_layout.py` optimiert. So pinnt der Test die
     * Reise gegen die tatsächlichen Daten, nicht gegen eine handgepflegte Mini-Tabelle.
     */
    private fun corpusBigrams(): Map<Pair<Char, Char>, Int> {
        val bg = HashMap<Pair<Char, Char>, Int>()
        for ((word, freq) in GermanWordList.words) {
            val w = word.lowercase()
            for (i in 0 until w.length - 1) {
                val key = w[i] to w[i + 1]
                bg[key] = (bg[key] ?: 0) + freq
            }
        }
        return bg
    }

    /** QWERTZ-CH in gleicher Koordinatenkonvention (Reihe 0/1/2, gestaffelt). */
    private fun qwertzPositions(): Map<Char, Pair<Float, Float>> {
        val rows = listOf(
            "qwertzuiopü" to 0.0f,
            "asdfghjklöä" to 0.25f,
            "yxcvbnm" to 0.75f,
        )
        val out = mutableMapOf<Char, Pair<Float, Float>>()
        rows.forEachIndexed { rowIdx, (chars, xoff) ->
            chars.forEachIndexed { col, ch -> out[ch] = (xoff + col) to rowIdx.toFloat() }
        }
        return out
    }

    @Test
    fun `optimiertes Layout reist deutlich kuerzer als QWERTZ`() {
        val optimized = travel(OptimizedLayout.deCh().letterPositions())
        val qwertz = travel(qwertzPositions())
        // Mindestens 15 % kürzer — in der Praxis (voller Korpus) deutlich mehr.
        assertTrue(
            "optimiert=$optimized sollte < 0.85 * qwertz=$qwertz sein",
            optimized < 0.85 * qwertz,
        )
    }

    @Test
    fun `optimiertes Layout reist auf dem echten Korpus klar kuerzer als QWERTZ`() {
        val bg = corpusBigrams()
        val optimized = travel(OptimizedLayout.deCh().letterPositions(), bg)
        val qwertz = travel(qwertzPositions(), bg)
        // Auf dem vollen GermanWordList-Korpus gemessen ~0.55 (≈45 % kürzer als QWERTZ-CH).
        // Schranke mit Reserve: deutlich unter 0.60 — schützt die Kern-Eigenschaft („~36 %
        // weniger Reise"), ohne bei kleinem Korpus-/Layout-Rauschen falsch-rot zu werden.
        // Anders als der Bigramm-Tabellen-Test oben pinnt das die ECHTEN Daten.
        assertTrue(
            "optimiert=$optimized sollte < 0.60 * qwertz=$qwertz sein (Korpus-Bigramme)",
            optimized < 0.60 * qwertz,
        )
    }

    @Test
    fun `alle 29 Buchstaben sind im Layout vorhanden`() {
        val chars = OptimizedLayout.deCh().letterPositions().keys
        assertTrue(chars.containsAll("abcdefghijklmnopqrstuvwxyzäöü".toList()))
    }

    @Test
    fun `die q-Taste ist die qu-Digraph-Taste mit einzelnem q als Alternative`() {
        val q = OptimizedLayout.deCh().rows.flatten()
            .filterIsInstance<CharKey>().first { it.char == 'q' }
        assertEquals("qu", q.output)
        assertEquals(listOf("q"), q.alternatives) // einzelnes q via Long-Press
    }

    @Test
    fun `das Telefon-Waehlfeld bietet Ziffern, Stern, Raute und Plus`() {
        val keys = OptimizedLayout.phone().rows.flatten()
        val chars = keys.filterIsInstance<CharKey>().map { it.output }.toSet()
        assertTrue(chars.containsAll("0123456789".map { it.toString() }))
        assertTrue(chars.containsAll(listOf("*", "#", "+")))
        // Kein verstecktes Long-Press auf dem Wählfeld (alles sichtbar beschriftet).
        assertTrue(keys.filterIsInstance<CharKey>().all { it.alternatives.isEmpty() })
        val actions = keys.filterIsInstance<FunctionKey>().map { it.action }
        assertTrue(actions.contains(KeyAction.ALPHA)) // zurück zum Alphabet
        assertTrue(actions.contains(KeyAction.BACKSPACE))
        assertTrue(actions.contains(KeyAction.RETURN))
    }

    @Test
    fun `das Ziffern-Pad bietet Ziffern und die Zahl-Separatoren, kein Telefonzeichen`() {
        val keys = OptimizedLayout.number().rows.flatten()
        val chars = keys.filterIsInstance<CharKey>().map { it.output }.toSet()
        assertTrue(chars.containsAll("0123456789".map { it.toString() }))
        assertTrue(chars.containsAll(listOf(",", ".", "-"))) // Beträge/Dezimal/Datum/Vorzeichen
        // Keine Telefonzeichen — die bleiben dem Wählfeld vorbehalten.
        assertTrue(chars.none { it in setOf("*", "#", "+") })
        // Kein verstecktes Long-Press (alles sichtbar beschriftet, wie das Wählfeld).
        assertTrue(keys.filterIsInstance<CharKey>().all { it.alternatives.isEmpty() })
        val actions = keys.filterIsInstance<FunctionKey>().map { it.action }
        assertTrue(actions.contains(KeyAction.ALPHA)) // zurück zum Alphabet
        assertTrue(actions.contains(KeyAction.BACKSPACE))
        assertTrue(actions.contains(KeyAction.RETURN))
    }

    @Test
    fun `Konsonanten-Cluster auf den Long-Press-Tasten`() {
        val keys = OptimizedLayout.deCh().rows.flatten().filterIsInstance<CharKey>()
        assertEquals(listOf("ch", "ck"), keys.first { it.char == 'c' }.alternatives)
        assertEquals(listOf("sch", "st", "sp"), keys.first { it.char == 's' }.alternatives)
        assertEquals(listOf("pf", "ph"), keys.first { it.char == 'p' }.alternatives)
    }
}
