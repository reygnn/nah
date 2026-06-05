package com.github.reygnn.nah.data.suggestions

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Präfix-Trie für Wortvorschläge. Vorsorglich thread-safe via [ReentrantReadWriteLock]:
 * aktuell laufen Lesen (Vorschläge) wie Schreiben (User-Wörter aktualisieren) auf dem
 * UI-Thread, aber die Struktur wird über einen Flow-Collector und die UI geteilt — das
 * Lock hält sie robust, falls ein Aufrufer auf einen Hintergrund-Dispatcher wechselt.
 * Portiert aus vuot.
 */
class Trie {
    private val root = TrieNode()
    private val lock = ReentrantReadWriteLock()

    fun insert(word: String, frequency: Int = 1) = lock.write {
        var node = root
        for (char in word.lowercase()) {
            node = node.children.getOrPut(char) { TrieNode() }
        }
        node.isWord = true
        node.frequency = frequency
        node.originalWord = word
    }

    fun getSuggestions(prefix: String, limit: Int): List<Pair<String, Int>> = lock.read {
        var node = root
        for (char in prefix.lowercase()) {
            node = node.children[char] ?: return emptyList()
        }
        val results = mutableListOf<Pair<String, Int>>()
        collectWords(node, results)
        // Sekundär alphabetisch: bei Frequenz-Gleichstand sonst HashMap-abhängig und
        // damit nicht reproduzierbar (passt nicht zum Determinismus-Anspruch).
        results.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(limit)
    }

    fun contains(word: String): Boolean = lock.read {
        var node = root
        for (char in word.lowercase()) {
            node = node.children[char] ?: return false
        }
        node.isWord
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
