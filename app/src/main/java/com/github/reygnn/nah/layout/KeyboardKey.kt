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
    override val weight: Float = 1f,
) : KeyboardKey {
    override val label: String get() = char.toString()
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
