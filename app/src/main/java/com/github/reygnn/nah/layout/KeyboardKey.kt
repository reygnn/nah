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
    /** Alternativen für das Long-Press-Popup (sichtbar, schieben+loslassen): z. B.
     *  `c` → ch/ck/sch, die qu-Taste → einzelnes `q`, Vokale → Akzente. Leer = nur Tap. */
    val alternatives: List<String> = emptyList(),
    override val weight: Float = 1f,
) : KeyboardKey {
    override val label: String get() = output
}

data class FunctionKey(
    val action: KeyAction,
    override val weight: Float = 1f,
    /** Per Long-Press erreichbare Alternativ-Aktionen (sichtbares Popup, schieben+loslassen
     *  — dieselbe Geste wie [CharKey.alternatives], nur löst ein Chip eine [KeyAction] aus
     *  statt ein Zeichen zu committen). Genutzt von den Ebenen-Umschalttasten (SYM/ABC), um die
     *  anderen Ebenen ([KeyAction.NUMPAD]/[KeyAction.DIALPAD]/[KeyAction.SYMBOLS]) und die
     *  Einstellungen ([KeyAction.SETTINGS]) erreichbar zu machen. Leer = nur Tap. */
    val longPressActions: List<KeyAction> = emptyList(),
) : KeyboardKey {
    override val label: String get() = action.label
}

enum class KeyAction(val label: String) {
    SHIFT("⇧"),
    BACKSPACE("⌫"),
    SPACE(" "),
    RETURN("⏎"),
    // Einheitliches 3-Buchstaben-Schema für die Ebenen (ABC/SYM/TEL/NUM): „SYM" (statt des
    // mehrdeutigen „?123") = die Symbol-/Zeichenebene. So kollidiert kein Label mehr mit „NUM".
    SYMBOLS("SYM"),
    ALPHA("ABC"),
    PERIOD("."),
    COMMA(","),
    PASTE("Insert"), // clipboard paste (icon key; the label is unused — rendered as an icon)
    // Long-Press-Ziele der Ebenen-Umschalttaste (nie eigene Tastenfläche) — der [label] ist zugleich
    // der Chip-Text. Einheitliches 3-Buchstaben-Schema: NUM = grosses Ziffern-Pad (, . -),
    // TEL = Wählfeld (* # +). Zusammen mit SYM/ABC erreicht man so von jeder Ebene jede andere.
    NUMPAD("NUM"),
    DIALPAD("TEL"),
    // Nur als Long-Press-Ziel der Punkt-Taste (Buchstabenebene) verwendet, nie als eigene
    // Tastenfläche — der [label] ist zugleich der Chip-Text. So setzt man ein Frage-/Ausrufe-
    // zeichen, ohne erst auf die Symbolebene zu wechseln.
    QUESTION("?"),
    EXCLAMATION("!"),
    // Nur als Long-Press-Ziel der Ebenen-Umschalttaste verwendet, nie als eigene Tastenfläche — der
    // [label] „OPT" (Optionen, passt ins 3-Buchstaben-Schema) ist zugleich der Chip-Text. Öffnet die
    // App-Einstellungen und ist damit von JEDER Ebene erreichbar, auch wenn die Vorschlagsleiste
    // (samt früherem Hamburger) ausgeblendet ist. Der Service verdrahtet die Aktion über onSettingsRequested.
    SETTINGS("OPT"),
}
