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
        node.isWord = true
        node.frequency = frequency
        node.originalWord = word
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
