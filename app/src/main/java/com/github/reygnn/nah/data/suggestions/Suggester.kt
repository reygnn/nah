package com.github.reygnn.nah.data.suggestions

/**
 * Liefert Wortvorschläge für ein (noch unfertiges) Präfix. Bewusst minimal, damit
 * der ViewModel nicht an den WordIndex gekoppelt ist und in JVM-Tests trivial gefakt
 * werden kann. Vorschläge sind **nicht-eingreifend**: sie ersetzen nur das aktuelle
 * Präfix auf Antippen, niemals ein bereits abgeschlossenes Wort.
 */
fun interface Suggester {
    /**
     * @param includeBuiltIn die eingebaute de-CH-Wortliste einbeziehen
     * @param includeUser die **kuratierten** benutzerdefinierten Wörter einbeziehen
     * @param includeLearned die beim Tippen **gelernten** Wörter einbeziehen (eigener Schalter)
     *
     * Alle drei Quellen sind unabhängig schaltbar — so kann man z. B. NUR die eigenen Wörter
     * (ohne das Dictionary-Rauschen) bekommen, oder gelernte ohne kuratierte. (Kein Default-Wert:
     * eine `fun interface`-SAM-Methode erlaubt keinen — die Aufrufer reichen alle drei Flags durch.)
     */
    fun suggest(
        prefix: String,
        includeBuiltIn: Boolean,
        includeUser: Boolean,
        includeLearned: Boolean,
    ): List<String>

    /**
     * Stammt [word] aus den benutzerdefinierten Wörtern? Solche Einträge (Vor-/Nachnamen,
     * Adressen, E-Mails) werden **wörtlich** committet — ihre Gross-/Kleinschreibung ist
     * massgeblich und darf NICHT an Shift/Caps-Lock angepasst werden, anders als
     * Wörterbuch-Vorschläge. Das betrifft ausschliesslich den **angetippten Vorschlag**
     * (siehe `KeyboardViewModel.onSuggestionTap`); von Hand ausbuchstabierte Zeichen folgen
     * weiterhin Shift/Caps-Lock (normales Tippen über `commitWithShift`). Default `false`
     * (Fakes/Tests verhalten sich wie Wörterbuch).
     */
    fun isUserWord(word: String): Boolean = false

    /**
     * Stammt [word] aus den **beim Tippen gelernten** Wörtern (siehe `LearnedWordRepository`)? Solche
     * Einträge werden — anders als die kuratierten eigenen Wörter ([isUserWord]) — **wie Wörterbuch-
     * Wörter** committet, also an Shift/Caps-Lock des Präfix angepasst (`casedLikePrefix`), nicht
     * wörtlich. Nur genutzt, um beim Live-Speichern ein schon gelerntes Wort nicht erneut anzubieten
     * (siehe `KeyboardViewModel.computeSaveWord`); fürs Casing genügt, dass [isUserWord] hier `false`
     * bleibt. Default `false` (Fakes/Tests verhalten sich wie Wörterbuch).
     */
    fun isLearnedWord(word: String): Boolean = false

    /**
     * Steht [word] in der **eingebauten** de-CH-Wortliste (`GermanWordList`)? Nur genutzt, um beim
     * Live-Speichern ein Wort NICHT als „gelernt" anzubieten, das die eingebaute Liste — **sofern sie
     * als Vorschlagsquelle aktiv ist** — ohnehin schon (gleich gecast) vorschlägt; ein gelernter Eintrag
     * wäre dann ein reines Duplikat (siehe `KeyboardViewModel.computeSaveWord`). Bewusst wirkungslos,
     * solange die Liste als Quelle aus ist: dann ist „als gelerntes Wort speichern" der einzige Weg, das
     * Wort vorgeschlagen zu bekommen. Liefert vor dem (lazy) Aufbau des Index `false` — er wird hierfür
     * NIE eigens materialisiert. Default `false` (Fakes/Tests verhalten sich wie ohne eingebaute Liste).
     */
    fun isBuiltInWord(word: String): Boolean = false
}
