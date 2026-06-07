package com.github.reygnn.nah.data.suggestions

/**
 * Präfix-Index für Wortvorschläge. Bei der Korpusgrösse dieser App (~1440 Wörter, siehe
 * `tools/word_index_benchmark.md`) ist ein **sortiertes Array + Binärsuche** einfacher und
 * speicherärmer als ein Trie, bei gleicher Abfragezeit: der Präfix-Bereich ist nach `key`
 * zusammenhängend, also genügt ein `lowerBound` plus Linearscan über genau die Treffer.
 *
 * **Bewusst nicht thread-safe**: gelesen wird auf dem UI-Thread, und jede Instanz wird fertig
 * gebaut, bevor [SuggestionRepository] sie atomar über ein `@Volatile`-Feld veröffentlicht —
 * dieselbe Instanz wird also nie nebenläufig mutiert *und* gelesen.
 */
class WordIndex {
    /**
     * Kleingeschriebener Pfad → zu committende Originalform + Frequenz. Die Map dient zugleich
     * [contains] (O(1)) und ist die Quelle für das lazy sortierte Array. Case-insensitive
     * Kollision (z. B. „morgen" 830 vs. „Morgen" 670 → derselbe Key): die HÖHERE Frequenz gewinnt,
     * nicht der letzte Eintrag — sonst verdeckte die seltenere Schreibweise stumm die häufigere.
     * Gleichstand behält den ersten Eintrag (`>`, nicht `>=`) → deterministisch. Echte
     * Gross-/Klein-Homographen („essen"/„Essen") teilen sich den Key, der seltenere verschwindet —
     * bei nicht-eingreifenden Vorschlägen kostet das höchstens einen Vorschlag, nie ein falsch
     * ersetztes Wort.
     */
    private val byKey = HashMap<String, Entry>()

    // Nach `key` sortiert, lazy gebaut und bei jedem insert invalidiert. Aufbau-dann-Abfrage ist der
    // Normalfall (ein Rebuild kostet einmalig ein sortedBy), pro Abfrage fällt nichts an.
    private var sorted: List<Entry>? = null

    fun insert(word: String, frequency: Int) {
        val key = word.lowercase()
        val existing = byKey[key]
        if (existing == null || frequency > existing.frequency) {
            byKey[key] = Entry(key, word, frequency)
            sorted = null
        }
    }

    fun getSuggestions(prefix: String, limit: Int): List<Pair<String, Int>> {
        val key = prefix.lowercase()
        val list = sortedEntries()
        val results = mutableListOf<Pair<String, Int>>()
        var i = lowerBound(list, key)
        // Alle Keys mit dem Präfix liegen ab lowerBound zusammenhängend (ein Key, der den Präfix
        // trägt, ist ≥ Präfix; der erste ≥-Eintrag ohne den Präfix beendet den Block).
        while (i < list.size && list[i].key.startsWith(key)) {
            results.add(list[i].word to list[i].frequency)
            i++
        }
        // Sekundär case-insensitiv alphabetisch (String.CASE_INSENSITIVE_ORDER): macht Frequenz-
        // Gleichstände reproduzierbar und ordnet nicht ASCIIbetisch (sonst stünde „Zürich" vor „apfel").
        return results
            .sortedWith(
                compareByDescending<Pair<String, Int>> { it.second }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first },
            )
            .take(limit)
    }

    fun contains(word: String): Boolean = byKey.containsKey(word.lowercase())

    private fun sortedEntries(): List<Entry> =
        sorted ?: byKey.values.sortedBy { it.key }.also { sorted = it }

    /** Erster Index mit `list[i].key >= key` (oder size). Dieselbe natürliche String-Ordnung wie
     *  [sortedEntries], damit Suche und Sortierung konsistent sind. */
    private fun lowerBound(list: List<Entry>, key: String): Int {
        var lo = 0
        var hi = list.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].key < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private class Entry(val key: String, val word: String, val frequency: Int)
}
