package com.github.reygnn.nah.layout

/**
 * Eine Taste in einem festen, reihenbasierten Layout. Anders als bei der toten
 * Schwebe-Tastatur (thumbprint) gibt es keinen Anker und keine lokalen Pixel-
 * Koordinaten: die Tastatur ist fix, jede Reihe füllt die volle Breite, und die
 * Tasten teilen sich die Reihenbreite per [weight]. Das macht die Tasten gross
 * (Fat-Finger) und ihre Position stabil (Muskelgedächtnis).
 *
 * Geschlossener Typ: [CharKey] committet genau ein Zeichen, [FunctionKey] löst
 * eine [KeyAction] aus. Keine Mehrdeutigkeit, kein Autocorrect.
 */
sealed interface KeyboardKey {
    /** Relatives Breitengewicht innerhalb der Reihe. */
    val weight: Float
    val label: String
}

data class CharKey(
    val char: Char,
    /** Was beim Tippen committet wird. Default = der Buchstabe selbst; abweichend nur
     *  bei Digraph-Tasten wie „qu" (committet zwei Zeichen — ehrlich so beschriftet,
     *  kein Autocorrect). [char] bleibt der Buchstabe für Reise-Metrik und Farb-Hinweis. */
    val output: String = char.toString(),
    override val weight: Float = 1f,
) : KeyboardKey {
    override val label: String get() = output
}

data class FunctionKey(
    val action: KeyAction,
    override val weight: Float = 1f,
) : KeyboardKey {
    override val label: String get() = action.label
}

enum class KeyAction(val label: String) {
    SHIFT("⇧"),
    BACKSPACE("⌫"),
    SPACE(" "),
    RETURN("⏎"),
    SYMBOLS("?123"),
    ALPHA("ABC"),
    PERIOD("."),
    COMMA(","),
}
