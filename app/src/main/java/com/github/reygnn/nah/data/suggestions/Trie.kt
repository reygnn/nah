package com.github.reygnn.nah.data.suggestions

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Präfix-Trie für Wortvorschläge. Thread-safe (Lese vom UI-Thread, Schreiben vom
 * Hintergrund) via [ReentrantReadWriteLock]. Portiert aus vuot.
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
        results.sortedByDescending { it.second }.take(limit)
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
