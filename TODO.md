# TODO

Offene, bewusst zurückgestellte Punkte. Erledigtes/Verworfenes steht in
`CLAUDE.md` (Abschnitt „Fast-Follow"), nicht hier.

---

## Handoff-Stand (Stand dieser Review-Session)

Drei flache adversarielle Review-Runden (13 → 2 → 0) plus ein **Fugen-Review**
(Edge×Edge, Trace-Konstruktion) sind durch. Alle gefundenen echten Defekte sind
gefixt und auf `main`: Korrektheit, IC-Robustheit, a11y (später wieder komplett
entfernt — sehende App, s. u.), RTL, Font-Scale, Paste-Guard, DataStore-Resilienz
— und die zwei Fugen-Funde (s. u.).

Der Fugen-Review (`wh7db942v`) fand genau die gesuchte Edge-of-Edge-Klasse:
**1 bestätigten** Doppel-Edge (Paste-Epoch am falschen Lebenszyklus-Hook) **+ 1
Kandidaten** (Vorschlag-Tap über noch nicht gemeldete Live-Auswahl). Beide
verifiziert (letzterer per Negativ-Kontrolle) und gefixt.

Zusätzlich abgesichert:
- **Invarianten-Fuzzer** (siehe (b)) — 12 500 zufällige Op-Sequenzen, 0 Brüche.
- **Paste-Guard-Logik** in die reine Einheit `ime/PasteGuard` extrahiert und
  deterministisch JVM-getestet (`PasteGuardTest`): echter Feldwechsel/Feld-Ende
  verwirft, reiner Restart hält. Damit ist die Service-/Async-Naht auf der
  **Entscheidungs-Ebene** bewiesen statt nur durchargumentiert. Was prinzipiell
  ungetestet bleibt, ist allein die Framework-Message-Reihenfolge (Timing-Race) —
  die ist nirgends deterministisch testbar (kein androidTest im Projekt, bewusst).

---

## Seither (Folge-Sessions, Analyse-Runden)

- **Auto-Cap × Settings-Toggle (gefixt, v0.7.26).** Auto-Cap ausschalten, während ein
  AUTO-armiertes SHIFTED stand, schrieb den nächsten Buchstaben noch gross. `applySettings`
  entwaffnet diesen einen Übergang jetzt sofort (manuelles SHIFTED/CAPS bleiben unberührt,
  nichts wird neu armiert). Drei Regressionstests.
- **`pendingSelfEcho` = bewusste reine Dedup-Optimierung, KEIN Bug.** Schon analysiert
  (`5117861`, `3a14dca`) und per Invarianten-Test gepinnt; eine veraltete Vorhersage kostet
  höchstens EIN Recompute, der synchrone Read in `afterTextChanged` trägt die Korrektheit.
  Nicht erneut als Befund aufkochen.
- **WordIndex-Vorschlagskosten quantifiziert** → `tools/word_index_benchmark.md`. ~0,1 µs/Wort
  im Präfix-Bereich; grün bis ~50k Wörter (<~1 ms/Tastendruck am Gerät). Der „grösseres
  Korpus"-Fast-Follow braucht bis dahin **keine** Code-Änderung. Heute (~1500 Wörter):
  wenige µs, irrelevant.

---

## Vorschlags-Ranking nach Nutzung (Idee, Fast-Follow)

Angeregt vom Kolibri_Launcher-Appdrawer (sortiert Apps nach Usage). Übertragbar auf
nah — aber **nur auf die Vorschlagsleiste**, nie auf Tasten/Long-Press/Layout:
bewegliche Tasten sind exakt der `thumbprint`-Tod (Muskelgedächtnis), und das Layout
ist eingefroren. Die Leiste ist dagegen das eine „weiche" Subsystem (opt-in, default
aus, nicht-eingreifend, ändert nie fertigen Text) — Adaptivität dort bricht keine harte
Regel.

**Passt mechanisch gut:** Das Ranking hängt schon an einer Integer-Frequenz
(`WordIndex` speichert pro Wort eine Frequenz, `SuggestionRepository.suggest()` sortiert
per `SUGGESTION_ORDER`, User-Wörter via `USER_WORD_FREQUENCY` nach vorn gepinnt). Eine
Usage-Formel ist also kein neuer Mechanismus, sondern ein **gelernter Frequenz-Boost**:
beim Vorschlag-Tap (oder Wort-Commit) einen Zähler hoch, beim Ranken einmischen.

**Randbedingungen, falls gebaut:**
- Eigener Settings-Toggle, unabhängig wie die getrennten Built-in/User-Schalter; sauber
  durch `applySettings(...)` verdrahtet (Hard rule #4), nicht per Konstruktor-Default.
- Persistenz via DataStore (Konvention), keyed pro Wort. Hauptkostenpunkt: die Tabelle
  wächst sonst unbegrenzt → **Cap + Pruning** nötig.
- Reine Frequency reicht wohl; Frecency (Recency-Decay) braucht eine Clock und
  verkompliziert die JVM-Testbarkeit (`runTest`/rein) — eher nicht den Aufwand wert.
- **Strikt vom Optimizer trennen.** `GermanWordList` ist Suggestion-Quelle *und*
  Optimizer-Input; der Usage-Store darf **nie** nach `optimize_layout.py` zurückfliessen
  (sonst rechtfertigt er indirekt das eingefrorene Layout). Rein fürs Vorschlags-Ranking.
- Privacy bleibt konsistent (alles lokal, kein Netz) — nur mehr lokaler State.

Steht weder auf der „Fast-Follow"- noch der „verworfen"-Liste in `CLAUDE.md` — frische
Idee, kein wiederaufgewärmter Streitpunkt. Bewusst zurückgestellt, kein Layout-Eingriff.

---

## Compose-Performance — geräte-gebunden, grösstenteils offen

Ehemals `temp/OPTIMIZE.md` (Code-Durchsicht nach Praxis-Test). Kurzfassung: die
**Logik-Seite ist bereits optimiert** (off-main Index-Aufbau, ein `getTextBeforeCursor`
pro Schritt, WordIndex als benchmarktes sortiertes Array). Das real Unbeleuchtete liegt
auf der **Compose-Laufzeit-Seite** — und die misst das Projekt nirgends. Leitprinzip:
**erst messen, dann optimieren.** Die Mess-Schritte brauchen das Pixel 9a (instrumentiert,
nicht in der JVM-Suite verifizierbar).

- **§4 Macrobenchmark (zuerst) — die fehlende Messung.** Neues `:macrobenchmark`-Modul
  (`com.android.test`), das (a) Tastatur-Einblende-Latenz und (b) Frame-Jank während einer
  gescripteten Tippsequenz auf dem Gerät erfasst. Bricht den Single-Module-Default bewusst.
  Ist die Baseline, an der §1 und §2 sich erst zeigen. **Fakt heute: kein Modul.**
- **§1 `TapKey`-Skippability — ✅ VERIFIZIERT (2026-06-09, logcat-Sonde am Pixel 9a).** `@Immutable`
  auf `CharKey`/`FunctionKey`/`KeyboardKey`/`KeyboardLayout` (+ `KeyboardUiState`) ist gesetzt
  (Commit `ef44f39`). Verifikation per Wegwerf-Sonde (`SideEffect`-Zähler pro `TapKey`/`SuggestionBar`,
  `Log.d` → `adb logcat -s nahRecompose`): **Befund stärker als die Hypothese.** Beim reinen Tippen
  rekomponiert **gar nichts** — weder die ~35 Tasten noch die `SuggestionBar`. Tasten rekomponieren
  *ausschliesslich* an Ebenen-/Shift-Grenzen, gebündelt in einem Pass (ein Zeitstempel), und dort
  zu Recht (andere Tasten/Labels). Der Performance-Held beim Tippen ist nicht das Compose-Skipping,
  sondern die **`StateFlow`-Konflation**: Tippen ändert nahs UI-State nicht (Text geht ins Host-Feld),
  also emittiert der Flow keinen neuen Wert → der Eltern-Composable rekomponiert nicht → der Skip-Pfad
  wird beim Tippen nie betreten. `@Immutable` bleibt richtige Versicherung für die Fälle, *wo* der
  Parent rekomponiert (Ebenen-/Shift-Wechsel). **Keine Performance-Sorge beim Tippen.** Sonde war
  Branch `test/recomposition-probe` (verworfen, nie gemergt).
- **§2 Baseline Profile — first-frame-Jank.** `androidx.baselineprofile`-Plugin +
  Generator über den Pfad „Feld fokussieren → Tastatur sichtbar → erster Tap". Einziger
  verbliebene Startup-Hebel (R8 ist aus). Gegen §4 gegenmessen. **Fakt heute: keins.**
- **§3 R8 / Minify — bleibt AUS (dokumentierte Nicht-Entscheidung).** Keine Reflection,
  persönliche Single-User-App, kein Store → marginal kleineres AAB rechtfertigt das
  Compose/IME-Keep-Regel-Tuning + Testrisiko nicht. Falls je erwogen: eigener Branch,
  Smoke-Test über ALLE Ebenen auf echtem Release-Build (Unit-Tests fangen R8-Regressionen
  nicht). **Nicht reflexhaft „zur Optimierung" einschalten.**
- **§5 Kleinkram — faktisch erledigt durch §1.** `onGloballyPositioned` läuft für jede Taste
  (Fenster-X nur bei Long-Press gebraucht) · `longPressItems` wird pro `TapKey`-Ausführung neu
  gebaut. Beides kostet nur, *wenn* `TapKey` rekomponiert — und §1 hat gemessen, dass das beim
  Tippen nie passiert. Damit gegenstandslos; nicht weiter verfolgen.

**Reihenfolge:** §1 ✅ + §5 ✅ erledigt (Tippen ist recomposition-frei, s. o.). Offen bleibt
§4 → §2 (Macrobenchmark als Baseline, dann Baseline Profile); §3 (R8) bleibt aus. Jeweils eigener
Branch (`test/`, `chore/`, `perf/`). Das Layout bleibt dabei eingefroren — keine Optimizer-Läufe
(CLAUDE.md).

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
Long-Press-Geste×Recomposition×Multi-Touch · WordIndex-Swap×`suggest`×Settings-Toggle ·
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

## Barrierefreiheit — ENTSCHIEDEN (bewusst NICHT unterstützt, komplett entfernt)

**Status: entschieden & umgesetzt.** Screenreader-/TalkBack-Support wurde später wieder
**komplett entfernt** (Commit `197f97d`): keine `contentDescription`/`stateDescription`/
`semantics` mehr, die Tasten tragen keine a11y-Knoten. (Eine frühere Iteration hatte
Basis-a11y hinzugefügt — das ist Geschichte, nicht der aktuelle Stand; vgl. die
„a11y"-Erwähnung im Handoff oben.) nah ist eine bewusst **sehende** Einfinger-Tastatur,
getippt nach Augenmass; die sichtbaren Long-Press-Menüs hängen ohnehin an einer Schiebe-
Geste, die ein Screenreader nicht treiben kann, und jede Basis-Ausgabe ist per Tap
erreichbar. Als dokumentierte Grenze im Code (`ui/TapKey.kt`) und im README festgehalten.

→ Erledigt/verworfen — gehört nach `CLAUDE.md` (Fast-Follow), hier nur noch als Verweis.
