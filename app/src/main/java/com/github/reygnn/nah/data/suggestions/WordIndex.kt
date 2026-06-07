package com.github.reygnn.nah.data.suggestions

/**
 * Vorschlags-Ordnung: höchste Frequenz zuerst, bei Gleichstand case-insensitiv alphabetisch
 * (String.CASE_INSENSITIVE_ORDER) — NICHT ASCIIbetisch, sonst stünde „Zürich" vor „apfel". EINE
 * Definition, geteilt von [WordIndex.getSuggestions] und [SuggestionRepository.suggest], damit die
 * Tie-Break-Regel zwischen beiden nicht auseinanderdriften kann.
 */
internal val SUGGESTION_ORDER: Comparator<Pair<String, Int>> =
    compareByDescending<Pair<String, Int>> { it.second }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first }

/**
 * Präfix-Index für Wortvorschläge. Bei der Korpusgrösse dieser App (~1440 Wörter, siehe
 * `tools/word_index_benchmark.md`) ist ein **sortiertes Array + Binärsuche** einfacher und
 * speicherärmer als ein Trie, bei gleicher Abfragezeit: der Präfix-Bereich ist nach `key`
 * zusammenhängend, also genügt ein `lowerBound` plus Linearscan über genau die Treffer.
 *
 * **Bewusst nicht thread-safe**: jede Instanz wird per [build] fertig materialisiert, BEVOR
 * [SuggestionRepository] sie atomar über ein `@Volatile`-Feld veröffentlicht — danach folgt kein
 * `insert` mehr, also wird dieselbe Instanz nie nebenläufig mutiert *und* gelesen. Die
 * `@Volatile`-Veröffentlichung etabliert die happens-before-Kante, über die der per [build]
 * geschriebene (nicht-volatile) `sorted`-Cache für alle Leser sichtbar wird.
 */
class WordIndex {
    /**
     * Kleingeschriebener Pfad → zu committende Originalform + Frequenz. Die Map dient zugleich
     * [contains] (O(1)) und ist die Quelle für das sortierte Array. Case-insensitive
     * Kollision (z. B. „morgen" 830 vs. „Morgen" 670 → derselbe Key): die HÖHERE Frequenz gewinnt,
     * nicht der letzte Eintrag — sonst verdeckte die seltenere Schreibweise stumm die häufigere.
     * Gleichstand behält den ersten Eintrag (`>`, nicht `>=`) → deterministisch. Echte
     * Gross-/Klein-Homographen („essen"/„Essen") teilen sich den Key, der seltenere verschwindet —
     * bei nicht-eingreifenden Vorschlägen kostet das höchstens einen Vorschlag, nie ein falsch
     * ersetztes Wort.
     *
     * [Entry] hält `key` (= lowercase) zusätzlich zur Originalform: bewusster Zeit-für-Platz-Tausch
     * — der eine zusätzliche String-Verweis je Eintrag spart beim Sortieren und in [lowerBound] das
     * wiederholte `lowercase()` pro Vergleich. Für grossgeschriebene Einträge sind das zwei Strings
     * statt einem; bei ~1440 Wörtern vernachlässigbar und immer noch schlanker als ein Trie.
     */
    private val byKey = HashMap<String, Entry>()

    // Nach `key` sortiert, bei jedem insert invalidiert, per build() (oder als Fallback beim ersten
    // getSuggestions) materialisiert. Aufbau-dann-Abfrage ist der Normalfall: ein Rebuild kostet
    // einmalig ein sortedBy, pro Abfrage fällt nichts an.
    private var sorted: List<Entry>? = null

    fun insert(word: String, frequency: Int) {
        val key = word.lowercase()
        val existing = byKey[key]
        if (existing == null || frequency > existing.frequency) {
            byKey[key] = Entry(key, word, frequency)
            sorted = null
        }
    }

    /**
     * Materialisiert das sortierte Array. Vom Erbauer nach dem letzten [insert] aufzurufen, BEVOR die
     * Instanz veröffentlicht wird (siehe [SuggestionRepository]): danach ist sie fertig gebaut und
     * über die `@Volatile`-Veröffentlichung sicher von jedem Thread lesbar. Fehlt der Aufruf (z. B.
     * im Test), baut der erste [getSuggestions] das Array lazy — dann aber nur Single-Thread-sicher.
     */
    fun build() {
        sortedEntries()
    }

    /**
     * Treffer für [prefix], nach [SUGGESTION_ORDER] sortiert und auf [limit] gekappt. Ein leeres oder
     * sehr kurzes Präfix matcht entsprechend breit (das leere Präfix die globale Top-[limit]); das
     * Ergebnis bleibt durch [limit] beschränkt, der Linearscan über den Trefferblock aber nicht. Eine
     * **Mindestpräfixlänge ist Aufrufer-Policy**, kein Index-Belang — der einzige Produktiv-Aufrufer
     * setzt sie (siehe [SuggestionRepository.suggest] / `MIN_PREFIX_LENGTH`).
     */
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
        return results.sortedWith(SUGGESTION_ORDER).take(limit)
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
