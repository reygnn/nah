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

    @Volatile
    private var learnedIndex: WordIndex? = null

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
            build() // fertig materialisieren, bevor das @Volatile-Feld die Instanz veröffentlicht
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
            WordIndex().apply {
                words.sorted().forEach { insert(it, USER_WORD_FREQUENCY) }
                build() // fertig materialisieren vor der @Volatile-Veröffentlichung
            }
        }
    }

    /**
     * Aktualisiert die **gelernten** Wörter (vom IME beim Beobachten von [LearnedWordRepository]).
     * Eigener [WordIndex], damit sie unabhängig zu- und abschaltbar sind UND — über [isLearnedWord]
     * statt [isUserWord] — wie Wörterbuch-Wörter (an Shift/Caps angepasst) statt wörtlich committet
     * werden. `@Synchronized`/`sorted()` wie [setUserWords].
     */
    @Synchronized
    fun setLearnedWords(words: Set<String>) {
        learnedIndex = if (words.isEmpty()) {
            null
        } else {
            WordIndex().apply {
                words.sorted().forEach { insert(it, LEARNED_WORD_FREQUENCY) }
                build()
            }
        }
    }

    override fun suggest(
        prefix: String,
        includeBuiltIn: Boolean,
        includeUser: Boolean,
        includeLearned: Boolean,
    ): List<String> {
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

        // Kuratierte und gelernte Wörter sind getrennt schaltbar (eigene Flags), teilen sich aber das
        // Ranking: ihr Casing-Unterschied liegt allein in isUserWord/isLearnedWord, nicht in der
        // Frequenz. Gelernte ranken knapp unter den kuratierten (LEARNED < USER), beide über der Liste.
        if (includeUser) userIndex?.let { collect(it.getSuggestions(prefix, MAX_SUGGESTIONS)) }
        if (includeLearned) learnedIndex?.let { collect(it.getSuggestions(prefix, MAX_SUGGESTIONS)) }
        if (includeBuiltIn) builtInIndex?.let { collect(it.getSuggestions(prefix, MAX_SUGGESTIONS)) }

        // Geteilte Ordnung mit WordIndex.getSuggestions (SUGGESTION_ORDER): höchste Frequenz zuerst,
        // bei Gleichstand case-insensitiv alphabetisch. Jeder Index hat bereits seine Top-N
        // vorsortiert; nach dem Merge zweier solcher Listen (≤ 2·MAX) gilt dieselbe Regel erneut.
        return merged.values
            .sortedWith(SUGGESTION_ORDER)
            .take(MAX_SUGGESTIONS)
            .map { it.first }
    }

    /**
     * Liegt [word] im User-Index? Dann wird es wörtlich committet (siehe [Suggester.isUserWord]).
     * [WordIndex.contains] prüft den exakten Eintrag case-insensitiv — der Vorschlagstext ist die
     * gespeicherte Originalform, trifft also seinen eigenen Eintrag.
     */
    override fun isUserWord(word: String): Boolean = userIndex?.contains(word) ?: false

    /**
     * Liegt [word] im Lern-Index? Dann wird es **nicht** wörtlich committet (anders als [isUserWord]),
     * sondern wie ein Wörterbuch-Wort an Shift/Caps angepasst — fürs Casing genügt, dass [isUserWord]
     * false bleibt. Genutzt, um beim Live-Speichern ein schon gelerntes Wort nicht erneut anzubieten.
     */
    override fun isLearnedWord(word: String): Boolean = learnedIndex?.contains(word) ?: false

    companion object {
        private const val MIN_PREFIX_LENGTH = 2
        /** Obergrenze der angezeigten Vorschläge — der einzige Wert dieser Art. Nicht nur intern
         *  gekappt (hier), sondern auch von der Ui geteilt (`SuggestionBar` rendert höchstens so
         *  viele Chips), damit beide nie auseinanderlaufen. Die Leiste ist horizontal scrollbar
         *  (`LazyRow`), darum quetscht ein höherer Wert die Chips nicht — er erfordert nur Scrollen. */
        const val MAX_SUGGESTIONS = 6
        /** Deutlich über der Top-Frequenz der eingebauten Liste (≈1000) → User-Wörter zuerst. */
        private const val USER_WORD_FREQUENCY = 10_000
        /** Wie [USER_WORD_FREQUENCY] über der Liste, aber knapp darunter → bei Gleich-Wort ranken
         *  kuratierte (wörtliche) Wörter vor gelernten. Beide stehen vor den eingebauten Vorschlägen. */
        private const val LEARNED_WORD_FREQUENCY = 9_000
    }
}
