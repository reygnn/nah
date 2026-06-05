# CLAUDE.md

Projektkonventionen für **nah** — eine Android-IME (Input Method Service) mit
einer **travel-optimierten de-CH-Tipp-Tastatur**. Gedacht als **Daily Driver**
für ein Pixel 9a, getippt **mit einem einzigen Zeigefinger**. Persönliche App,
kein Play Store, de-CH only (kein ß, „ss"). Claude liest diese Datei bei
Session-Start. Kurz und handlungsleitend halten.

> **Warum nah existiert.** Standard-QWERTZ wurde gebaut, um häufige Buchstaben
> fürs *Zehnfinger*-System auseinanderzureissen — für *einen* Finger maximiert
> das die Reisestrecke. nah ordnet die Buchstaben so an, dass häufige
> Buchstaben/Bigramme nah beieinander liegen (~36 % weniger Fingerreise als
> QWERTZ-CH, siehe `tools/optimize_layout.py`).

## Die vier harten Anforderungen (Reihenfolge ist Absicht)

1. **Fat-Finger-tauglich** — über Tasten*grösse*, nicht Tastenzahl. Volles
   Alphabet, grosse Tasten.
2. **KEIN Autocorrect.** Jeder Tap committet genau das getippte Zeichen. Die
   Tastatur ändert **niemals** ein fertiges Wort. Das ist das oberste Gesetz.
3. **Keine massive Lernkurve.** Sichtbare Labels, ab Tag eins per hunt-and-peck
   benutzbar; nur die Buchstaben*positionen* sind neu, nicht die Tastaturform.
4. **Daily Driver.** de-CH, Pixel 9a, ein Zeigefinger.

> **Designkontext (das Trilemma).** Wenige Tasten erzwingen Mehrdeutigkeit, die
> entweder die Maschine (Autocorrect — abgelehnt) oder der Mensch (Lernkurve —
> abgelehnt) auflöst. Für jemanden, der *beides* ablehnt, ist die Antwort:
> **volles Alphabet, deterministisch, optimal angeordnet.** Schwesterprojekte:
> `thumbprint` (tot — schwebende Tastatur killte das Muskelgedächtnis),
> `vuot` (lebt als Hobby-Gesten-Keyboard, *kein* Daily Driver — Lernkurve).

## Stack

- Kotlin 2.3.21, Jetpack Compose + Material 3, Compose BOM 2026.05.01
- AGP 9.2.1 + Gradle 9.5.1, **built-in Kotlin** (kein `org.jetbrains.kotlin.android`)
- min/target/compile SDK 36 (Android 16 only), `jvmTarget = JVM_21`
- Single-Module `:app`, **kein DI** (plain `ViewModel` + `StateFlow` + DataStore)
- Tests: JUnit 4, MockK, kotlinx-coroutines-test (kein Robolectric)

## Build & Test

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:bundleRelease     # unsigniertes AAB
```

Installieren über die Family-Konvention: aus `~/apk` heraus
`~/apk/install-aab.sh app/build/outputs/bundle/release/app-release.aab`
(signiert mit dem Family-Key; **kein Gradle-Signing**, siehe globale CLAUDE.md).

## Architektur

```
app/src/main/java/com/github/reygnn/nah/
  ime/         NahIme : InputMethodService — dünner Glue, ComposeView-Hosting
               (ViewTree-Owner via FrameLayout-Wrapper), safeIc-Disziplin.
  viewmodel/   KeyboardViewModel — StateFlow, ganze Tipp-State-Machine.
  layout/      KeyboardKey/CharKey/FunctionKey/KeyAction, KeyboardLayout
               (reihenbasiert, weight-basiert), OptimizedLayout (deCh + symbols).
  ui/          KeyboardScreen/KeyboardContent, TapKey, SuggestionBar, NahColors.
  settings/    Settings, SettingsRepository (DataStore), SettingsActivity,
               UserWordsActivity (eigene Wörter verwalten).
  data/suggestions/  Trie, GermanWordList, SuggestionRepository (Suggester:
               eingebaute Liste + unabhängig schaltbarer User-Trie),
               UserWordRepository (DataStore), UserWordValidation (rein).
tools/         optimize_layout.py — der Layout-Optimierer (Wegwerf, reproduzierbar).
```

## Hard rules

1. **Kein Autocorrect, keine Wortersetzung von fertigem Text.** `onSuggestionTap`
   ersetzt ausschliesslich das aktuelle, noch unfertige Präfix. Wenn du je in
   Versuchung kommst, ein committetes Wort zu ändern: lass es.
2. **Vorschlagsleiste ist standardmässig AUS** (`Settings.suggestionsEnabled =
   false`) und nicht-eingreifend (nur auf Antippen).
3. **Entscheidungen im ViewModel, nicht im Service.** `NahIme` ist Glue; jeder
   `InputConnection`-Call läuft durch `safeIc { }` (loggt `Log.w`, crasht nie).
4. **Settings fliessen durch `KeyboardViewModel.applySettings(...)`**, nicht über
   Konstruktor-Defaults. Neues Tunable → `SettingsRepository.toSettings()` UND
   `applySettings()` verdrahten.
5. **Die Buchstaben-Anordnung ist optimizer-generiert.** Sie änderst du über
   `tools/optimize_layout.py` und encodest das Ergebnis in `OptimizedLayout`.
   `KeyboardLayout.letterPositions()` ist die einzige Koordinatenquelle (Reise-
   Test + späteres MissMap). **Eine Layout-Änderung kostet Umlernen** — nicht
   leichtfertig.

## Fast-Follow (bewusst nicht in v1)

- **MissMap-Offset-Lernen** (nächster grosser Fat-Finger-Mehrwert): aus dem toten
  `thumbprint` transplantieren — `MissMap`/`MissLearner` (reines Kotlin),
  `TapResolver` **ohne** LM (nur Distanz²-Geometrie). Roh-Pointer → Resolver statt
  Compose-clickable; MissMap per Referenz Resolver↔Learner teilen; CSV-Persistenz
  mit Debounce. **Reine Geometrie + persönlicher Versatz, niemals Wortwahl.**
- Clipboard-Verlauf (Room), Theming-Auswahl, Long-Press-Akzente (à/é), echte
  Verdrahtung der `keyboardHeightFraction` an die Tastaturhöhe, grösseres
  Optimizer-Korpus.

## Git

Solo, keine PRs. Nicht-triviale Änderungen auf eigenen Branch
(`feature/`, `fix/`, `refactor/`, `chore/`, `test/`). Commit-Subject:
Schweizerdeutsch ok („ss"). Trailer:
`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
