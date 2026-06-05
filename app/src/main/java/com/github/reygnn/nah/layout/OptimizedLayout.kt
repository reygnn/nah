package com.github.reygnn.nah.layout

/**
 * Das travel-optimierte de-CH-Layout.
 *
 * Die **Vokale sind bewusst zentral gebündelt** (o/u/i in der Mitte-links-Spalte,
 * a/e daneben; ä/ö/ü rechts gestapelt) — eine Lernbarkeits-Entscheidung: ein
 * zusammenhängender Vokal-Block ist leichter zu merken und lässt sich als Lern-Farbe
 * sauber einfärben. Die **Konsonanten** hat dann der Optimizer (`tools/optimize_layout.py`,
 * Simulated Annealing über de-CH-Bigramme inkl. Space-/Shift-Übergänge) rund um diesen
 * fixen Vokal-Block optimal gesetzt. Ergebnis: **~36 % weniger Fingerreise als QWERTZ-CH**
 * — praktisch gleich wie das frei optimierte Optimum, der Vokal-Cluster kostet also so
 * gut wie nichts. Vier Buchstabenreihen → breite Tasten mit Totzonen ringsum.
 *
 *   x  qu k  o  p  j  y  ä
 *   v  c  h  u  a  l  f  ö
 *   z  m  s  i  e  r  b  ü
 *      w  t  n  d  g          (Shift davor, Backspace danach)
 *
 * Die „q"-Taste **committet `qu`**: q steht im Deutschen praktisch immer vor u, also
 * ist die Taste ehrlich als Digraph beschriftet — kein Autocorrect, sie tut genau, was
 * draufsteht. Einzelnes q: qu tippen, u mit Backspace weg. Kein scharfes S (de-CH, "ss").
 * Eine Layout-Änderung kostet Umlernen — mit grösserem Korpus regenerierbar, siehe Tool.
 */
object OptimizedLayout {

    private const val ROW0 = "xqkopjyä"
    private const val ROW1 = "vchualfö"
    private const val ROW2 = "zmsierbü"
    private const val ROW3 = "wtndg"

    fun deCh(): KeyboardLayout = KeyboardLayout(
        rows = listOf(
            charRow(ROW0).withQu(),
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

    // Symbol/Zahlen-Layer: 5 Reihen wie der Buchstaben-Layer, damit die Tastatur
    // beim Wechsel ?123 ↔ ABC NICHT in der Höhe springt. Funktionsreihe identisch
    // zum Buchstaben-Layer ( , / Space / . / ⏎ an denselben Positionen), nur der
    // Toggle links zeigt ABC statt ?123. Darum sind , und . NICHT in den
    // Inhaltsreihen — sie leben in der Funktionsreihe.
    fun symbols(): KeyboardLayout = KeyboardLayout(
        rows = listOf(
            charRow("1234567890"),
            charRow("@#€$%&*-+="),
            charRow("()[]{}<>/\\"),
            buildList {
                addAll(charRow("!?:;'\"_~|"))
                add(FunctionKey(KeyAction.BACKSPACE, weight = 1.5f))
            },
            listOf(
                FunctionKey(KeyAction.ALPHA, weight = 1.5f),
                FunctionKey(KeyAction.COMMA, weight = 1f),
                FunctionKey(KeyAction.SPACE, weight = 5f),
                FunctionKey(KeyAction.PERIOD, weight = 1f),
                FunctionKey(KeyAction.RETURN, weight = 1.5f),
            ),
        ),
    )

    private fun charRow(chars: String): List<KeyboardKey> =
        chars.map { CharKey(it) }

    /** Ersetzt die q-Taste durch die Digraph-Taste „qu" (committet zwei Zeichen). */
    private fun List<KeyboardKey>.withQu(): List<KeyboardKey> =
        map { if (it is CharKey && it.char == 'q') CharKey('q', output = "qu") else it }
}
