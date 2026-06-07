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
- Tests: JUnit 4, MockK, kotlinx-coroutines-test; Robolectric nur für echtes
  Android-Runtime (DataStore → `DojoStatsRepositoryTest`), sonst JVM-only

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
  viewmodel/   KeyboardViewModel — StateFlow, ganze Tipp-State-Machine;
               FieldContext (destilliertes EditorInfo).
  layout/      KeyboardKey/CharKey (output + alternatives)/FunctionKey/KeyAction,
               KeyboardLayout (reihenbasiert, weight-basiert), OptimizedLayout
               (deCh + symbols, qu-Digraph), KeyAlternatives (Long-Press-Tabelle).
  ui/          KeyboardScreen/KeyboardContent, TapKey (inkl. Long-Press-Popup),
               SuggestionBar, NahColors (Lern-Farben), NahIcons.
  settings/    Settings, SettingsRepository (DataStore), SettingsActivity,
               UserWordsActivity (eigene Wörter verwalten).
  data/suggestions/  WordIndex (sortiertes Array + Binärsuche), GermanWordList,
               SuggestionRepository (Suggester: eingebaute Liste + unabhängig
               schaltbarer User-Index), UserWordRepository (DataStore),
               UserWordValidation (rein).
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
5. **Das Layout ist EINGEFROREN. Es wird NUR auf ausdrücklichen Befehl des
   Nutzers geändert.** Die aktuelle Buchstaben-Anordnung (`OptimizedLayout` /
   `CURRENT_ROWS` in `tools/optimize_layout.py`) gefällt dem Nutzer und sitzt im
   Muskelgedächtnis — **eine Änderung kostet Umlernen**. Darum:
   - **`optimize_layout.py` NIE eigenmächtig laufen lassen** und das Ergebnis NIE
     eigenmächtig nach `OptimizedLayout` übernehmen — auch nicht, wenn das Korpus
     (`GermanWordList`) wächst und ein „besseres" Layout möglich wäre. Das Korpus
     ist Suggestion-Quelle UND Optimizer-Input; es darf wachsen, ohne dass das
     Layout angefasst wird.
   - Eine Neu-Optimierung ist ein **separater, ausdrücklich vom Nutzer
     angeforderter** Schritt. Niemals als Nebeneffekt einer anderen Aufgabe,
     niemals „weil es sich anbietet", niemals ohne explizite Ansage.
   - `KeyboardLayout.letterPositions()` bleibt die einzige Koordinatenquelle
     (für den Reise-Test).

## Fast-Follow (bewusst nicht in v1)

- Clipboard-Verlauf (Room), Theming-Auswahl, grösseres Optimizer-Korpus.

**Erledigt / verworfen** (nicht mehr offen — nicht erneut vorschlagen):

- **Long-Press-Akzente/Digraphen**: umgesetzt — `KeyAlternatives`-Tabelle +
  sichtbares Popup in `TapKey` (schieben/loslassen, kein Swipe).
- **MissMap-Offset-Lernen**: **verworfen**. Grosse Tasten + Totzonen verhindern
  Fehltipper in der Praxis bereits; der Mehraufwand lohnt nicht.
- **`keyboardHeightFraction`**: entfernt (war ein nie verdrahtetes Tunable).

## Git

Solo, keine PRs. Nicht-triviale Änderungen auf eigenen Branch
(`feature/`, `fix/`, `refactor/`, `chore/`, `test/`). Commit-Subject:
Schweizerdeutsch ok („ss"). Trailer:
`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
