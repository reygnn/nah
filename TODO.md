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

## (b) Invarianten-Fuzzer als permanentes Test-Harness

**Warum.** Die maschinelle Variante von (a): statt Traces von Hand zu enumerieren,
**zufällige, seed-reproduzierbare Operationssequenzen** gegen `KeyboardViewModel` +
`FakeIc` würfeln und nach *jedem* Schritt die harten Invarianten prüfen. Findet
Doppel-Edges, die kein Mensch aufzählt, und **bleibt als Regressionswächter** bei
jedem `./gradlew test`. Passt zum Test-Ethos (JVM-rein, kein Robolectric).

**Scope.** `KeyboardViewModel`-State-Machine inkl. der Echo-Naht (`onSelectionChanged`
als Framework-Echo nach jeder Edit-Op). Die reine Service-/Async-Naht bleibt (a)
vorbehalten (nicht JVM-simulierbar).

**Design (so umsetzbar):**
- Neue Datei `app/src/test/java/.../viewmodel/KeyboardViewModelInvariantFuzzTest.kt`
  (oder Methode in `KeyboardViewModelTest`, um `FakeIc` zu teilen).
- `FakeIc` minimal erweitern: `selStart`/`selEnd` (bzw. `cursor`) als lesbare
  Accessor exponieren — der Fuzzer braucht die Position vor jeder Op fürs Erwartete.
- Seeded `kotlin.random.Random(seed)`, ~400 Seeds × ~60 Ops. Op-Menge: Tippen
  (zufälliger CharKey aus dem deCh-Layout), Shift-Tap, Backspace, Space/Period/Comma,
  Vorschlag-Tap (nur wenn `state.suggestions` nicht leer), Layer-Wechsel,
  Cursor-Move (`fake.select(p,p)` + `onSelectionChanged`), Select (`a≤b`),
  `onStartInput` (restarting zufällig; `initialSel` = aktuelle Fake-Position).
  **Nach jeder Edit-Op das Framework-Echo treiben:** `onSelectionChanged(fake.selStart,
  fake.selEnd)`.
- Suggester deterministisch injizieren (z. B. `prefix + "x"` ab Länge 2).
- Per-Op-Invarianten (Snapshot Puffer/Cursor/Shift VOR der Op):
  - **INV-A (I1/I2 Tippen):** Puffer danach == `before[0,selStart) +
    shiftBefore.applyTo(c) + before[selEnd,)` — sonst hat ein Tap mehr als das
    getippte Zeichen verändert.
  - **INV-B (I1 Backspace):** ohne Auswahl genau ein Code-Point vor dem Cursor
    entfernt (BMP: 1 Zeichen); mit Auswahl genau `[selStart,selEnd)` entfernt; bei
    Cursor 0 ohne Auswahl unverändert.
  - **INV-C (I1 Vorschlag):** `after.startsWith(before[0, wortStart))` UND
    `after.endsWith(before[cursor,))` — nur das aktuelle alnum-Präfix wurde ersetzt,
    fertiger Text drumherum bleibt unangetastet (Casing der Mitte NICHT vorhersagen).
  - **Generisch:** keine Exception bei irgendeiner Op/Echo-Reihenfolge.
- Bei rotem Lauf den Seed + Op-Index loggen → exakte Reproduktion des Doppel-Edge.

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
