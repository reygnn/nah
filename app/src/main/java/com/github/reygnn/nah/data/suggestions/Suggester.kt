package com.github.reygnn.nah.data.suggestions

/**
 * Liefert Wortvorschläge für ein (noch unfertiges) Präfix. Bewusst minimal, damit
 * der ViewModel nicht an den Trie gekoppelt ist und in JVM-Tests trivial gefakt
 * werden kann. Vorschläge sind **nicht-eingreifend**: sie ersetzen nur das aktuelle
 * Präfix auf Antippen, niemals ein bereits abgeschlossenes Wort.
 */
fun interface Suggester {
    /**
     * @param includeBuiltIn die eingebaute de-CH-Wortliste einbeziehen
     * @param includeUser die benutzerdefinierten Wörter einbeziehen
     *
     * Beide Quellen sind unabhängig schaltbar — so kann man NUR die eigenen Wörter
     * (ohne das Dictionary-Rauschen) bekommen.
     */
    fun suggest(prefix: String, includeBuiltIn: Boolean, includeUser: Boolean): List<String>

    /**
     * Stammt [word] aus den benutzerdefinierten Wörtern? Solche Einträge (Vor-/Nachnamen,
     * Adressen, E-Mails) werden **wörtlich** committet — ihre Gross-/Kleinschreibung ist
     * massgeblich und darf NICHT an Shift/Caps-Lock angepasst werden, anders als
     * Wörterbuch-Vorschläge. Default `false` (Fakes/Tests verhalten sich wie Wörterbuch).
     */
    fun isUserWord(word: String): Boolean = false
}
