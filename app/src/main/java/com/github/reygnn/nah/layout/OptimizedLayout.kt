package com.github.reygnn.nah.layout

/**
 * Das travel-optimierte de-CH-Layout.
 *
 * Die Buchstaben-Anordnung wurde mit `tools/optimize_layout.py` per Simulated
 * Annealing über die bigramm-Häufigkeiten eines de-CH-Korpus (inkl. Space-
 * Übergänge an Wortgrenzen) bestimmt. Ziel: minimale Fingerreise für **einen**
 * Finger. Ergebnis dieser 3-Reihen-Form: ~36 % weniger Reisestrecke als
 * QWERTZ-CH, bei konventioneller Tastaturhöhe — nur die Buchstabenpositionen
 * sind neu, die Gesamtform bleibt vertraut.
 *
 *   j k b o l h c p ö x
 *   ü m a r e i g z y ä
 *   f u d n s t w v q
 *
 * Hochfrequente Buchstaben (a r e i n s t d) clustern im Zentrum. Kein ß
 * (de-CH, "ss"). Die Anordnung ist mit einem grösseren Korpus regenerierbar —
 * siehe das Tool — kostet dann aber Umlernen.
 */
object OptimizedLayout {

    private const val ROW0 = "jkbolhcpöx"
    private const val ROW1 = "ümareigzyä"
    private const val ROW2 = "fudnstwvq"

    fun deCh(): KeyboardLayout = KeyboardLayout(
        rows = listOf(
            charRow(ROW0),
            charRow(ROW1),
            buildList {
                add(FunctionKey(KeyAction.SHIFT, weight = 1.5f))
                addAll(charRow(ROW2))
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
