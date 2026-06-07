package com.github.reygnn.nah.data.suggestions

/**
 * Präfix-Trie für Wortvorschläge. **Bewusst nicht thread-safe** — und braucht es nicht:
 * Gelesen (Vorschläge) wird auf dem UI-Thread; der User-Trie wird in [SuggestionRepository]
 * als ganze, fertig gebaute Instanz atomar über ein `@Volatile`-Feld getauscht, der
 * eingebaute Trie einmal im Hintergrund gebaut und danach nur noch gelesen. Dieselbe
 * Instanz wird also nie nebenläufig mutiert *und* gelesen. Portiert aus vuot — dort lag
 * vorsorglich ein `ReentrantReadWriteLock` drum; hier kostete das nur pro Tastendruck eine
 * Lock-Akquise, ohne je einen echten Wettlauf abzusichern.
 */
class Trie {
    private val root = TrieNode()

    fun insert(word: String, frequency: Int = 1) {
        var node = root
        for (char in word.lowercase()) {
            node = node.children.getOrPut(char) { TrieNode() }
        }
        // Case-insensitive Kollision (z. B. „morgen" 830 vs. „Morgen" 670 — beide landen auf
        // demselben kleingeschriebenen Pfad): die HÖHERE Frequenz behalten, nicht den zuletzt
        // eingefügten Eintrag gewinnen lassen. Sonst verdeckte die spätere, seltenere Schreibweise
        // stumm die häufigere und der Vorschlag käme falsch gecast heraus (klein getipptes „morge"
        // → „Morgen") — genau die stille Fehlschreibung, die nah ablehnt. Gleichstand behält den
        // ersten Eintrag (deterministisch).
        if (!node.isWord || frequency > node.frequency) {
            node.isWord = true
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
        // Sekundär alphabetisch: bei Frequenz-Gleichstand sonst HashMap-abhängig und
        // damit nicht reproduzierbar (passt nicht zum Determinismus-Anspruch).
        return results.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(limit)
    }

    fun contains(word: String): Boolean {
        var node = root
        for (char in word.lowercase()) {
            node = node.children[char] ?: return false
        }
        return node.isWord
    }

    /**
     * Sammelt ALLE Wörter im Teilbaum unter [node], bevor [getSuggestions] nach Frequenz
     * sortiert und auf das Limit kürzt. Bewusst ohne vorzeitigen Abbruch: das häufigste Wort
     * kann beliebig tief im Teilbaum liegen, für eine korrekte Top-K-Auswahl muss der ganze
     * Teilbaum besucht werden. Beim aktuellen Korpus (gut 1400 Wörter) ist das
     * vernachlässigbar — selbst der kürzeste erlaubte Präfix (2 Zeichen) spannt nur einen
     * kleinen Teilbaum auf. Erst ein um Grössenordnungen grösseres Wörterbuch würde Branch-and-
     * Bound lohnen (max. Teilbaum-Frequenz je Knoten cachen und nicht-konkurrenzfähige Äste kappen).
     *
     * Quantifiziert in `tools/trie_benchmark.md`: gemessen ~0,1–0,2 µs/Wort im Teilbaum; grün bis
     * ~50k Wörter (<~1 ms/Tastendruck am Gerät), Branch-and-Bound lohnt erst ab ~100k–200k Wörtern.
     * Heute (~1440 Wörter): ~7 µs — irrelevant.
     */
    private fun collectWords(node: TrieNode, results: MutableList<Pair<String, Int>>) {
        if (node.isWord) {
            node.originalWord?.let { results.add(it to node.frequency) }
        }
        for (child in node.children.values) {
            collectWords(child, results)
        }
    }

    private class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var isWord = false
        var frequency = 0
        var originalWord: String? = null
    }
}
