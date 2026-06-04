package com.github.reygnn.nah.layout

/**
 * Das travel-optimierte de-CH-Layout.
 *
 * Die Buchstaben-Anordnung wurde mit `tools/optimize_layout.py` per Simulated
 * Annealing über die bigramm-Häufigkeiten eines de-CH-Korpus (inkl. Space-
 * Übergänge an Wortgrenzen) bestimmt. Ziel: minimale Fingerreise für **einen**
 * Finger. Ergebnis dieser 4-Reihen-Form: ~38 % weniger Reisestrecke als
 * QWERTZ-CH. Vier Buchstabenreihen (statt drei) → weniger Tasten pro Reihe, also
 * breitere Tasten — schafft Platz für Totzonen ringsum (gegen Fehltipper), ohne
 * die Tasten zu verkleinern.
 *
 *   q ä c o b f ü j
 *   p v h u r a k ö
 *   x z s i e g l y
 *   w t n d m            (Shift davor, Backspace danach)
 *
 * Hochfrequente Buchstaben (a r e i n s t d) clustern zentral und unten (nah am
 * Space-Roundtrip). Kein scharfes S (de-CH, "ss"). Die Anordnung ist mit einem
 * grösseren Korpus regenerierbar — siehe das Tool — kostet dann aber Umlernen.
 */
object OptimizedLayout {

    private const val ROW0 = "qäcobfüj"
    private const val ROW1 = "pvhurakö"
    private const val ROW2 = "xzsiegly"
    private const val ROW3 = "wtndm"

    fun deCh(): KeyboardLayout = KeyboardLayout(
        rows = listOf(
            charRow(ROW0),
            charRow(ROW1),
            charRow(ROW2),
            buildList {
                add(FunctionKey(KeyAction.SHIFT, weight = 1.5f))
                addAll(charRow(ROW3))
                add(FunctionKey(KeyAction.BACKSPACE, weight = 1.5f))
            },
            listOf(
                FunctionKey(KeyAction.SYMBOLS, weight = 1.5f),
                FunctionKey(KeyAction.COMMA, weight = 1f),
                FunctionKey(KeyAction.SPACE, weight = 5f),
                FunctionKey(KeyAction.PERIOD, weight = 1f),
                FunctionKey(KeyAction.RETURN, weight = 1.5f),
            ),
        ),
    )

    fun symbols(): KeyboardLayout = KeyboardLayout(
        rows = listOf(
            charRow("1234567890"),
            charRow("@#&*-+=/()"),
            buildList {
                addAll(charRow("!?\"':;,."))
                add(FunctionKey(KeyAction.BACKSPACE, weight = 1.5f))
            },
            listOf(
                FunctionKey(KeyAction.ALPHA, weight = 1.5f),
                FunctionKey(KeyAction.SPACE, weight = 5f),
                FunctionKey(KeyAction.RETURN, weight = 1.5f),
            ),
        ),
    )

    private fun charRow(chars: String): List<KeyboardKey> =
        chars.map { CharKey(it) }
}
