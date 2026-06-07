package com.github.reygnn.nah.data.suggestions

/**
 * Präfix-Trie für Wortvorschläge. **Bewusst nicht thread-safe**: gelesen wird auf dem UI-Thread, und
 * jede Instanz wird fertig gebaut, bevor [SuggestionRepository] sie atomar über ein `@Volatile`-Feld
 * veröffentlicht — dieselbe Instanz wird also nie nebenläufig mutiert *und* gelesen.
 */
class Trie {
    private val root = TrieNode()

    fun insert(word: String, frequency: Int) {
        var node = root
        for (char in word.lowercase()) {
            node = node.children.getOrPut(char) { TrieNode() }
        }
        // Case-insensitive Kollision (z. B. „morgen" 830 vs. „Morgen" 670 → derselbe Pfad): die HÖHERE
        // Frequenz gewinnt, nicht der letzte Eintrag — sonst verdeckte die seltenere Schreibweise stumm
        // die häufigere und „morge" käme als „Morgen" heraus. Gleichstand behält den ersten Eintrag
        // (`>`, nicht `>=`) → deterministisch. Akzeptierte Grenze: echte Gross-/Klein-Homographen
        // („essen"/„Essen") teilen sich den Pfad, der seltenere verschwindet — bei nicht-eingreifenden
        // Vorschlägen kostet das höchstens einen Vorschlag, nie ein falsch ersetztes Wort.
        // `originalWord != null` ist zugleich die Wortend-Markierung (kein separates isWord-Flag).
        if (node.originalWord == null || frequency > node.frequency) {
            node.frequency = frequency
            node.originalWord = word
        }
    }

    fun getSuggestions(prefix: String, limit: Int): List<Pair<String, Int>> {
        var node = root
        for (char in prefix.lowercase()) {
            node = node.children[char] ?: return emptyList()
        }
        val results = mutableListOf<Pair<String, Int>>()
        collectWords(node, results)
        // Sekundär case-insensitiv alphabetisch (String.CASE_INSENSITIVE_ORDER): macht Frequenz-
        // Gleichstände reproduzierbar statt HashMap-abhängig, ohne pro Vergleich zu allokieren, und
        // ordnet nicht ASCIIbetisch (sonst stünde „Zürich" vor „apfel").
        return results
            .sortedWith(
                compareByDescending<Pair<String, Int>> { it.second }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first },
            )
            .take(limit)
    }

    fun contains(word: String): Boolean {
        var node = root
        for (char in word.lowercase()) {
            node = node.children[char] ?: return false
        }
        return node.originalWord != null
    }

    /**
     * Sammelt ALLE Wörter im Teilbaum — ohne vorzeitigen Abbruch, weil das häufigste Wort beliebig tief
     * liegen kann und [getSuggestions] erst danach nach Frequenz sortiert/kürzt. Beim Korpus dieser App
     * vernachlässigbar (Messung: `tools/trie_benchmark.md`).
     */
    private fun collectWords(node: TrieNode, results: MutableList<Pair<String, Int>>) {
        // `originalWord != null` markiert ein Wortende — das `?.let` ist die Prüfung, kein Defensiv-Code.
        node.originalWord?.let { results.add(it to node.frequency) }
        for (child in node.children.values) {
            collectWords(child, results)
        }
    }

    private class TrieNode {
        val children = HashMap<Char, TrieNode>()
        // Nicht-null genau für Wortenden: Markierung UND die zu committende Originalform (siehe insert).
        var frequency = 0
        var originalWord: String? = null
    }
}
