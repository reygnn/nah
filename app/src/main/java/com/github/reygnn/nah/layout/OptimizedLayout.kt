package com.github.reygnn.nah.layout

/**
 * Das travel-optimierte de-CH-Layout.
 *
 * Die **Vokale sind bewusst zentral gebündelt** (o/u/i in der Mitte-links-Spalte,
 * a/e daneben) — eine Lernbarkeits-Entscheidung: ein zusammenhängender Vokal-Block ist
 * leichter zu merken und lässt sich als Lern-Farbe sauber einfärben. Die **Konsonanten**
 * hat dann der Optimizer (`tools/optimize_layout.py`, Simulated Annealing über de-CH-
 * Bigramme inkl. Space-/Shift-Übergänge) rund um diesen fixen Vokal-Block optimal gesetzt.
 * Ergebnis: **~36 % weniger Fingerreise als QWERTZ-CH** — praktisch gleich wie das frei
 * optimierte Optimum, der Vokal-Cluster kostet also so gut wie nichts. Vier Buchstaben-
 * reihen → breite Tasten mit Totzonen ringsum.
 *
 * Die **Umlaute ä/ö/ü** haben keine eigene Taste: sie liegen per Long-Press auf ihrem
 * Grundvokal (a→ä, o→ö, u→ü, siehe [KeyAlternatives]). Das spart die rechte Spalte und
 * macht jede Taste breiter (Fat-Finger), ohne den optimierten Rest zu verschieben — die
 * Umlaute waren ohnehin in der äussersten Spalte gepinnt.
 *
 *   x  qu k  o  p  j  y
 *   v  c  h  u  a  l  f
 *   z  m  s  i  e  r  b
 *      w  t  n  d  g          (Shift davor, Backspace danach)
 *
 * Die „q"-Taste **committet `qu`**: q steht im Deutschen praktisch immer vor u, also
 * ist die Taste ehrlich als Digraph beschriftet — kein Autocorrect, sie tut genau, was
 * draufsteht. Einzelnes q: qu tippen, u mit Backspace weg. Kein scharfes S (de-CH, "ss").
 * Eine Layout-Änderung kostet Umlernen — mit grösserem Korpus regenerierbar, siehe Tool.
 */
object OptimizedLayout {

    private const val ROW0 = "xqkopjy"
    private const val ROW1 = "vchualf"
    private const val ROW2 = "zmsierb"
    private const val ROW3 = "wtndg"

    fun deCh(): KeyboardLayout = KeyboardLayout(
        rows = listOf(
            charRow(ROW0).withQu(),
            charRow(ROW1),
            charRow(ROW2),
            // Shift/Backspace bewusst weight 1f (NICHT breiter): so hat diese Reihe genau 7 Tasten
            // wie die drei Buchstabenreihen darüber → alle vier Reihen teilen dasselbe gleichmässige
            // 7-Spalten-Raster. w/t/n/d/g sitzen exakt unter den Spalten darüber (saubere Flucht,
            // Muskelgedächtnis), und die spaltenbasierte Reise-Metrik (letterPositions) deckt sich
            // mit dem tatsächlichen Bild. Breitere Shift/Backspace (1.5f) würden die Reihe auf
            // Gesamtgewicht 8 bringen und die Buchstaben schmaler + versetzt rendern.
            buildList {
                add(FunctionKey(KeyAction.SHIFT))
                addAll(charRow(ROW3))
                add(FunctionKey(KeyAction.BACKSPACE))
            },
            functionRow(KeyAction.SYMBOLS),
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
            // Backspace weight 1f → 9 Symbole + Backspace = 10 Tasten wie die Reihen darüber,
            // gleiche Spaltenbreite, kein Versatz (gleiche Logik wie die Buchstabenebene).
            buildList {
                addAll(charRow("!?:;'\"_~|"))
                add(FunctionKey(KeyAction.BACKSPACE))
            },
            functionRow(KeyAction.ALPHA),
        ),
    )

    /**
     * Telefon-Wählfeld: das klassische 3-spaltige Dialpad (Muskelgedächtnis) mit grossen
     * Tasten — Fat-Finger-tauglich, weil nur drei Tasten pro Reihe die volle Breite teilen.
     * Nur der **Startview** für Telefonfelder (`TYPE_CLASS_PHONE`); reine Zahlen-/Datumsfelder
     * bleiben auf der allgemeinen Symbolebene. Alle Tasten sichtbar beschriftet (keine Lernwand):
     * `+`, `*`, `#` liegen offen, kein verstecktes Long-Press. Über `ABC` geht es zum vollen
     * Alphabet (z. B. Vanity-Nummern, Durchwahl-Buchstaben), `?123` von dort zur Symbolebene.
     *
     * Die **Einfügen-Taste sitzt sichtbar ganz links** (wie auf der Alpha-/Symbolebene), damit
     * sich eine kopierte Nummer direkt einfügen lässt, ohne erst über `ABC` auszuweichen — sie
     * dimmt automatisch bei leerer Zwischenablage (Deaktivier-Logik in `KeyboardContent`).
     *
     *   1 2 3
     *   4 5 6
     *   7 8 9
     *   * 0 #
     *   ⧉ ABC + ⌫ ⏎        (fünf Reihen wie die anderen Ebenen → keine Höhensprünge)
     */
    fun phone(): KeyboardLayout = KeyboardLayout(
        rows = listOf(
            charRow("123"),
            charRow("456"),
            charRow("789"),
            charRow("*0#"),
            listOf(
                FunctionKey(KeyAction.PASTE, weight = 1f),
                FunctionKey(KeyAction.ALPHA, weight = 1.5f),
                CharKey('+'),
                FunctionKey(KeyAction.BACKSPACE, weight = 1.5f),
                FunctionKey(KeyAction.RETURN, weight = 1.5f),
            ),
        ),
    )

    /**
     * Allgemeines Ziffern-Pad: dasselbe 3-spaltige Grosstasten-Raster wie das Telefon-
     * Wählfeld (Fat-Finger), nur mit den Separatoren für Zahlen/Beträge/Datum (`, . -`)
     * statt der Telefonzeichen (`* # +`). Startview für reine Zahl-/Zahl-Passwort-/
     * Datumsfelder (`numeric`, aber NICHT `phone`) — eine PIN oder ein Betrag wird so auf
     * den grossen Tasten getippt, nicht auf der zehn-Tasten-breiten Symbolreihe (die ein
     * Number-Feld bisher bekam und die der Fat-Finger-Anforderung widerspricht). de-CH-
     * Datum „31.12." nutzt den Punkt; `:` / `/` (Zeit, Bruch) liegen einen `ABC`→`?123`-Hop
     * entfernt. Alle Tasten sichtbar beschriftet (kein verstecktes Long-Press, wie das
     * Wählfeld). Fünf Reihen wie überall → keine Höhensprünge.
     *
     * Die **Einfügen-Taste sitzt sichtbar ganz links** (wie auf der Alpha-/Symbolebene), damit
     * sich ein kopierter Betrag/Code/eine PLZ direkt einfügen lässt, ohne erst über `ABC`
     * auszuweichen — sie dimmt automatisch bei leerer Zwischenablage (s. `KeyboardContent`).
     *
     *   1 2 3
     *   4 5 6
     *   7 8 9
     *   , 0 .
     *   ⧉ ABC - ⌫ ⏎
     */
    fun number(): KeyboardLayout = KeyboardLayout(
        rows = listOf(
            charRow("123"),
            charRow("456"),
            charRow("789"),
            charRow(",0."),
            listOf(
                FunctionKey(KeyAction.PASTE, weight = 1f),
                FunctionKey(KeyAction.ALPHA, weight = 1.5f),
                CharKey('-'),
                FunctionKey(KeyAction.BACKSPACE, weight = 1.5f),
                FunctionKey(KeyAction.RETURN, weight = 1.5f),
            ),
        ),
    )

    /** Untere Funktionsreihe, auf beiden Ebenen identisch (nur der Toggle links
     *  unterscheidet sich: ?123 vs ABC). Einfügen-Taste ganz links — immer sichtbar.
     *
     *  Die ?123-Taste (nur sie, nicht das ABC der Symbolebene) bietet per Long-Press die
     *  Grosstasten-Pads an: so kommt man aus dem Alphabet (wo man nach einem ABC-Tap in
     *  einem Zahl-/Telefonfeld landet) wieder aufs grosse Ziffern-Pad bzw. Wählfeld zurück,
     *  statt nur auf die schmale Symbol-Ziffernreihe — ohne dafür eine sichtbare Taste zu
     *  opfern. Tap bleibt unverändert (→ Symbolebene); der Pad-Zugang ist rein additiv. */
    private fun functionRow(toggle: KeyAction): List<KeyboardKey> = listOf(
        FunctionKey(KeyAction.PASTE, weight = 1f),
        if (toggle == KeyAction.SYMBOLS) {
            FunctionKey(toggle, weight = 1.5f, longPressActions = listOf(KeyAction.NUMPAD, KeyAction.DIALPAD))
        } else {
            FunctionKey(toggle, weight = 1.5f)
        },
        FunctionKey(KeyAction.COMMA, weight = 1f),
        FunctionKey(KeyAction.SPACE, weight = 4f),
        // Auf der Buchstabenebene (toggle == SYMBOLS) bietet die Punkt-Taste ? und ! per
        // Long-Press an — so braucht es für ein Frage-/Ausrufezeichen keinen Wechsel auf die
        // Symbolebene. Auf der Symbolebene selbst liegen ? und ! ohnehin offen → dort kein
        // (redundantes) Long-Press, also auch kein Eck-Marker.
        if (toggle == KeyAction.SYMBOLS) {
            FunctionKey(
                KeyAction.PERIOD,
                weight = 1f,
                longPressActions = listOf(KeyAction.QUESTION, KeyAction.EXCLAMATION),
            )
        } else {
            FunctionKey(KeyAction.PERIOD, weight = 1f)
        },
        FunctionKey(KeyAction.RETURN, weight = 1.5f),
    )

    private fun charRow(chars: String): List<KeyboardKey> =
        chars.map { CharKey(it, alternatives = KeyAlternatives.forChar(it)) }

    /** Ersetzt die q-Taste durch die Digraph-Taste „qu" (committet zwei Zeichen);
     *  ihre Alternative (einzelnes „q") kommt aus [KeyAlternatives]. */
    private fun List<KeyboardKey>.withQu(): List<KeyboardKey> =
        map { if (it is CharKey && it.char == 'q') it.copy(output = "qu") else it }
}
