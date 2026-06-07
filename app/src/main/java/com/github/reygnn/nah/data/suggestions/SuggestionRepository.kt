package com.github.reygnn.nah.data.suggestions

/**
 * Vorschlagsquelle aus zwei unabhängig schaltbaren [WordIndex]en: die eingebaute
 * [GermanWordList] (im Hintergrund vorgebaut, siehe [warmUpBuiltIn] — da Vorschläge
 * standardmässig aus sind, kostet sie im Normalfall nichts) und die benutzerdefinierten
 * Wörter aus [UserWordRepository] (per [setUserWords] gesetzt). Kein Hilt. Liefert nur
 * Vorschläge — fertiger Text wird nie angefasst.
 */
class SuggestionRepository : Suggester {

    @Volatile
    private var builtInIndex: WordIndex? = null

    @Volatile
    private var userIndex: WordIndex? = null

    /**
     * Baut den eingebauten de-CH-Index — einmalig und idempotent. **Bewusst von einem
     * Hintergrund-Dispatcher aufzurufen** (siehe `NahIme`), damit der Aufbau (hunderte
     * Wörter) nie den UI-Thread beim ersten Tastendruck blockiert. Bis er fertig ist,
     * liefert [suggest] einfach keine eingebauten Vorschläge — nicht-eingreifend, der
     * Nutzer merkt die Verzögerung von wenigen Millisekunden nicht.
     */
    @Synchronized
    fun warmUpBuiltIn() {
        if (builtInIndex != null) return
        builtInIndex = WordIndex().apply {
            GermanWordList.words.forEach { (word, frequency) -> insert(word, frequency) }
        }
    }

    /**
     * Aktualisiert die benutzerdefinierten Wörter (vom IME beim Beobachten von
     * [UserWordRepository] aufgerufen). Eigener [WordIndex] → unabhängig von der
     * eingebauten Liste zu- und abschaltbar.
     *
     * `@Synchronized` wie [warmUpBuiltIn]: serialisiert konkurrierende Aufrufe, damit „letzter gewinnt"
     * wohldefiniert ist (Veröffentlichung atomar übers `@Volatile`-Feld).
     */
    @Synchronized
    fun setUserWords(words: Set<String>) {
        userIndex = if (words.isEmpty()) {
            null
        } else {
            // sorted() vor dem Einfügen: stünden zwei Eigenwörter auf demselben Key mit gleicher
            // Frequenz (z. B. „Müller"/„müller"), entschiede sonst die undefinierte Set-Reihenfolge, welches
            // Casing gewinnt. Welches genau, ist egal — nur stabil muss es sein.
            WordIndex().apply { words.sorted().forEach { insert(it, USER_WORD_FREQUENCY) } }
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

        if (includeUser) userIndex?.let { collect(it.getSuggestions(prefix, MAX_SUGGESTIONS)) }
        if (includeBuiltIn) builtInIndex?.let { collect(it.getSuggestions(prefix, MAX_SUGGESTIONS)) }

        // Sekundär case-insensitiv alphabetisch → deterministisch bei Frequenz-Gleichstand (wie
        // WordIndex.getSuggestions).
        return merged.values
            .sortedWith(
                compareByDescending<Pair<String, Int>> { it.second }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first },
            )
            .take(MAX_SUGGESTIONS)
            .map { it.first }
    }

    /**
     * Liegt [word] im User-Index? Dann wird es wörtlich committet (siehe [Suggester.isUserWord]).
     * [WordIndex.contains] prüft den exakten Eintrag case-insensitiv — der Vorschlagstext ist die
     * gespeicherte Originalform, trifft also seinen eigenen Eintrag.
     */
    override fun isUserWord(word: String): Boolean = userIndex?.contains(word) ?: false

    private companion object {
        const val MIN_PREFIX_LENGTH = 2
        const val MAX_SUGGESTIONS = 3
        /** Deutlich über der Top-Frequenz der eingebauten Liste (≈1000) → User-Wörter zuerst. */
        const val USER_WORD_FREQUENCY = 10_000
    }
}
