package com.github.reygnn.nah.data.suggestions

/**
 * Vorschlagsquelle aus zwei unabhängig schaltbaren Tries: die eingebaute
 * [GermanWordList] (im Hintergrund vorgebaut, siehe [warmUpBuiltIn] — da Vorschläge
 * standardmässig aus sind, kostet sie im Normalfall nichts) und die benutzerdefinierten
 * Wörter aus [UserWordRepository] (per [setUserWords] gesetzt). Kein Hilt. Liefert nur
 * Vorschläge — fertiger Text wird nie angefasst.
 */
class SuggestionRepository : Suggester {

    @Volatile
    private var builtInTrie: Trie? = null

    @Volatile
    private var userTrie: Trie? = null

    /**
     * Baut den eingebauten de-CH-Trie — einmalig und idempotent. **Bewusst von einem
     * Hintergrund-Dispatcher aufzurufen** (siehe `NahIme`), damit der Aufbau (hunderte
     * Wörter) nie den UI-Thread beim ersten Tastendruck blockiert. Bis er fertig ist,
     * liefert [suggest] einfach keine eingebauten Vorschläge — nicht-eingreifend, der
     * Nutzer merkt die Verzögerung von wenigen Millisekunden nicht.
     */
    @Synchronized
    fun warmUpBuiltIn() {
        if (builtInTrie != null) return
        builtInTrie = Trie().apply {
            GermanWordList.words.forEach { (word, frequency) -> insert(word, frequency) }
        }
    }

    /**
     * Aktualisiert die benutzerdefinierten Wörter (vom IME beim Beobachten von
     * [UserWordRepository] aufgerufen). Eigener Trie → unabhängig von der
     * eingebauten Liste zu- und abschaltbar.
     */
    fun setUserWords(words: Set<String>) {
        userTrie = if (words.isEmpty()) {
            null
        } else {
            Trie().apply { words.forEach { insert(it, USER_WORD_FREQUENCY) } }
        }
    }

    override fun suggest(prefix: String, includeBuiltIn: Boolean, includeUser: Boolean): List<String> {
        if (prefix.length < MIN_PREFIX_LENGTH) return emptyList()

        // Nach kleingeschriebenem Wort dedupen; höhere Frequenz gewinnt, womit
        // User-Wörter (USER_WORD_FREQUENCY ≫ Listen-Spitze) vorne ranken.
        val merged = HashMap<String, Pair<String, Int>>()
        fun collect(list: List<Pair<String, Int>>) {
            for ((word, freq) in list) {
                val key = word.lowercase()
                val existing = merged[key]
                if (existing == null || freq > existing.second) merged[key] = word to freq
            }
        }

        if (includeUser) userTrie?.let { collect(it.getSuggestions(prefix, MAX_SUGGESTIONS)) }
        if (includeBuiltIn) builtInTrie?.let { collect(it.getSuggestions(prefix, MAX_SUGGESTIONS)) }

        // Sekundär alphabetisch → deterministische Reihenfolge bei gleicher Frequenz.
        return merged.values
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(MAX_SUGGESTIONS)
            .map { it.first }
    }

    private companion object {
        const val MIN_PREFIX_LENGTH = 2
        const val MAX_SUGGESTIONS = 3
        /** Deutlich über der Top-Frequenz der eingebauten Liste (≈1000) → User-Wörter zuerst. */
        const val USER_WORD_FREQUENCY = 10_000
    }
}
