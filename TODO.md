# TODO

Offene, bewusst zurückgestellte Punkte. Erledigtes/Verworfenes steht in
`CLAUDE.md` (Abschnitt „Fast-Follow"), nicht hier.

---

## Handoff-Stand (Stand dieser Review-Session)

Drei flache adversarielle Review-Runden (13 → 2 → 0) plus ein **Fugen-Review**
(Edge×Edge, Trace-Konstruktion) sind durch. Alle gefundenen echten Defekte sind
gefixt und auf `main`: Korrektheit, IC-Robustheit, a11y, RTL, Font-Scale,
Paste-Guard, DataStore-Resilienz — und die zwei Fugen-Funde (s. u.).

Der Fugen-Review (`wh7db942v`) fand genau die gesuchte Edge-of-Edge-Klasse:
**1 bestätigten** Doppel-Edge (Paste-Epoch am falschen Lebenszyklus-Hook) **+ 1
Kandidaten** (Vorschlag-Tap über noch nicht gemeldete Live-Auswahl). Beide
verifiziert (letzterer per Negativ-Kontrolle) und gefixt.

---

## ✅ ERLEDIGT — die zwei Fugen-Funde (Branch `fix/seam-followups`)

1. **Paste-Commit ins fremde Feld (high, I4).** `fieldEpoch++` saß am falschen Hook
   (`onStartInputView`, fenster-gekoppelt), während `currentInputConnection` schon bei
   `onStartInput` umschaltet → bei einem Feldwechsel mit kurz verstecktem Fenster konnte
   ein langsamer Paste gegen die IC des **fremden** Feldes committen. **Fix:** Epoch in
   `onStartInput(!restarting)` + `onFinishInput()` hochzählen statt in `onStartInputView`.
   (Service-Logik → konventionsgemäss kein JVM-Test.)
2. **Vorschlag-Tap über noch nicht gemeldete Live-Auswahl (I1).** `commitText` hätte die
   Auswahl ersetzt und fertigen Text zerstört. **Fix:** `onSuggestionTap` bricht bei
   leerem Präfix bzw. real offener Auswahl (`getExtractedText`) ab. **Per Negativ-Kontrolle
   belegt** (Test rot ohne Guard, grün mit) und mit Regressionstest gepinnt.

(Der frühere Scratch `ReproTest.kt` ist ersetzt durch den ordentlichen Test in
`KeyboardViewModelTest`.)

---

## (a) Fugen-Review: Edge×Edge-Interaktionen (Subsystem-Paare, Trace-Konstruktion)

**Warum.** Die flachen „ein Skeptiker pro Dimension"-Runden finden Edge-Cases *in*
einem Subsystem, aber strukturell **nicht** den Doppel-Edge-Case in der *Naht
zwischen zweien* — zwei je-korrekte Mechaniken, deren Interleaving (Reihenfolge/
Timing) eine Invariante bricht. Methodik: Agenten **pro Subsystem-Paar**, jeder
konstruiert eine konkrete nummerierte Ereignis-Sequenz (inkl. umsortierter/
verspäteter Framework-Callbacks) und **simuliert die State-Machine Schritt für
Schritt von Hand**; nur ein durchsimulierter Invariantenbruch zählt.

**Harte Invarianten (Prüfziel):** I1 fertiger Text wird nie verändert · I2
Angezeigtes == Getipptes · I3 kein gestrandeter/falscher Shift-Zustand · I4 kein
Commit ins falsche Feld / kein Crash.

**Status.** Erste Iteration läuft als Workflow `wh7db942v` (8 Fugen). Falls 0
durchsimulierte Brüche → „Fugen-Siegel". Verbleibende, schwer JVM-abdeckbare Naht
ist primär die **Service-/Async-Naht** (Paste-Landung × Lifecycle × Echo-Ordering).

**Untersuchte/zu untersuchende Fugen:** paste×lifecycle (`fieldEpoch`) ·
echo-reorder×selektions-backspace (`afterTextChanged` setzt lokal `selEnd=selStart`
vor dem echten Echo) · `beginBatchEdit`×Echo-mitten-im-Batch×autoCap ·
autoCap×Ziffer-Guard×Vorschlag-Casing×Caps · Auswahl×Vorschlag×`atWordEnd` ·
Long-Press-Geste×Recomposition×Multi-Touch · Trie-Swap×`suggest`×Settings-Toggle ·
Restart×Shift-Erhalt×`pasteAvailable`.

**Wieder ausführbar:** `Workflow({scriptPath:
"…/workflows/scripts/nah-seam-review-wf_66b74ab4-9b0.js"})`.

## (b) Invarianten-Fuzzer — ✅ GEBAUT

Permanentes Regressions-Harness, das die harten Invarianten mechanisch abklopft, statt
sie einzeln per Review zu jagen. Umgesetzt als `invarianten-fuzzer …`-Test in
`KeyboardViewModelTest` (teilt sich `FakeIc`; `selectionStart`/`selectionEnd`-Accessoren
ergänzt). Würfelt seed-reproduzierbar (`Random(seed)`, 250 Seeds × 50 Ops = 12 500
Operationen) Tippen/Backspace/Space-Punkt-Komma/Vorschlag-Tap/Cursor/Auswahl/Ebenenwechsel/
Feld-Restart inkl. Echo-Naht (`onSelectionChanged` nach jeder Op) und prüft pro Schritt
INV-A (Tippen), INV-B (Backspace), INV-C (Vorschlag ersetzt nur das Präfix) + „wirft nie".
Deterministisch (fixe Seeds → kein flaky CI; tiefer suchen = Seed-Zahl erhöhen).
**Aktueller Lauf: 12 500 Ops, 0 Invariantenbrüche.**

---

## Barrierefreiheit: Long-Press-Alternativen für TalkBack — ENTSCHIEDEN (bewusst nicht unterstützt)

**Status: in dieser Session entschieden.** Die Basis-a11y wurde vervollständigt
(lokalisierte `contentDescription` für Backspace/Shift/Return/?123/ABC,
`stateDescription` der Shift-Taste, `disabled()`-Semantik der Paste-Taste — siehe
`ui/TapKey.kt`, `res/values*/strings.xml`). Die **Long-Press-Alternativen** bleiben
für TalkBack bewusst unerreichbar (keine `CustomAccessibilityAction`): Long-Press ist
eine sehende Komfortgeste, und **jede Basis-Ausgabe ist per Tap erreichbar** (einzelnes
`q`, Ziffern über `?123`, Akzente notfalls über die Symbolebene). Als dokumentierte
Grenze im Code festgehalten (`ui/TapKey.kt`, Kommentar an `longPressItems`).

→ Kann nach `CLAUDE.md` (Fast-Follow → „Erledigt/verworfen") wandern; hier nur noch
als Verweis.
