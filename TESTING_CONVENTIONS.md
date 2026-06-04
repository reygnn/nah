# TESTING_CONVENTIONS

JVM-only Unit-Tests. JUnit 4 + MockK + kotlinx-coroutines-test. Kein Robolectric,
kein Mockito, kein androidTest-Source-Set.

## Nicht verhandelbar

- **`MainDispatcherRule` + `runTest(rule.testDispatcher)`** für jeden Test, der
  `Dispatchers.Main` berührt. Plain `runTest { }` baut einen eigenen Scheduler →
  flaky. (Der `KeyboardViewModel` nutzt v1 keine Coroutinen, daher laufen seine
  Tests plain; die Regel liegt für Fast-Follow-Code, der Main berührt, bereit.)
- **MockK, nicht Mockito.** Mock-Variablen nach dem benennen, was sie darstellen
  (kein `mock`-Präfix) — `mockk(...)` sagt schon, dass es ein Mock ist.
- **Pure Logik lebt ausserhalb der Android-Runtime-Klassen.** `KeyboardViewModel`,
  `OptimizedLayout`, `Trie`, `SuggestionRepository` sind JVM-testbar ohne
  Robolectric. Der IME-Service ist dünner Glue und wird nicht unit-getestet.
- `unitTests.isReturnDefaultValues = true` ist gesetzt (Framework-Klassen geben in
  JVM-Tests Defaults zurück).

## Muster

- **Fake-InputConnection**: ein `mockk<InputConnection>(relaxed = true)` mit einem
  echten `StringBuilder`-Puffer hinter `commitText` / `deleteSurroundingText` /
  `getTextBeforeCursor` (siehe `KeyboardViewModelTest`). So testet man das
  Tipp-Verhalten ohne Android-Runtime.
- **Layout-Reise-Property**: `OptimizedLayoutTravelTest` pinnt, dass die optimierte
  Anordnung deutlich kürzer reist als QWERTZ-CH — schützt vor Verschlechterung.
- **Kein-Autocorrect-Garantie testen**: `vorschlag-tap ersetzt nur das aktuelle
  Wort` belegt, dass fertiger Text nie angefasst wird. Diese Invariante immer
  mittesten, wenn du an der Vorschlags-/Commit-Logik schraubst.
