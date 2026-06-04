package com.github.reygnn.nah.data.suggestions

/**
 * Schlanke Vorschlagsquelle für v1: ein lazy aus [GermanWordList] gefüllter [Trie],
 * kein Hilt, keine User-Words (Fast-Follow). Der Trie wird erst beim ersten
 * [suggest]-Aufruf gebaut — da Vorschläge standardmässig aus sind, kostet er im
 * Normalfall nichts.
 */
class SuggestionRepository : Suggester {

    private val trie by lazy {
        Trie().apply {
            GermanWordList.words.forEach { (word, frequency) -> insert(word, frequency) }
        }
    }

    override fun suggest(prefix: String): List<String> {
        if (prefix.length < MIN_PREFIX_LENGTH) return emptyList()
        return trie.getSuggestions(prefix, limit = MAX_SUGGESTIONS).map { it.first }
    }

    private companion object {
        const val MIN_PREFIX_LENGTH = 2
        const val MAX_SUGGESTIONS = 3
    }
}
