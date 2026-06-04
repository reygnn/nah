package com.github.reygnn.nah.data.suggestions

/**
 * Liefert Wortvorschläge für ein (noch unfertiges) Präfix. Bewusst minimal, damit
 * der ViewModel nicht an den Trie gekoppelt ist und in JVM-Tests trivial gefakt
 * werden kann. Vorschläge sind **nicht-eingreifend**: sie ersetzen nur das aktuelle
 * Präfix auf Antippen, niemals ein bereits abgeschlossenes Wort.
 */
fun interface Suggester {
    fun suggest(prefix: String): List<String>
}
