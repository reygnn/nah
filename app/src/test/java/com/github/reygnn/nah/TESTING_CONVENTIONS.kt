package com.github.reygnn.nah

/**
 * # TESTING_CONVENTIONS
 *
 * JVM-only Unit-Tests als Default. JUnit 4 + MockK + kotlinx-coroutines-test. Kein
 * Mockito, kein androidTest-Source-Set. **Robolectric** kommt nur dort dazu, wo ein
 * Test echtes Android-Runtime braucht (DataStore) — aktuell die beiden DataStore-
 * Round-Trip-Tests `DojoStatsRepositoryTest` und `DojoBestPersistenceTest`
 * (`@RunWith(RobolectricTestRunner)`, `@Config(sdk = [36])`). Reine Logik bleibt JVM-only.
 *
 * ## Nicht verhandelbar
 *
 * - **`MainDispatcherRule` + `runTest(rule.testDispatcher)`** für jeden Test, der
 *   `Dispatchers.Main` berührt. Plain `runTest { }` baut einen eigenen Scheduler →
 *   flaky. (Der `KeyboardViewModel` nutzt v1 keine Coroutinen, daher laufen seine
 *   Tests plain; die Regel liegt für Fast-Follow-Code, der Main berührt, bereit.)
 * - **MockK, nicht Mockito.** Mock-Variablen nach dem benennen, was sie darstellen
 *   (kein `mock`-Präfix) — `mockk(...)` sagt schon, dass es ein Mock ist.
 * - **Pure Logik lebt ausserhalb der Android-Runtime-Klassen.** `KeyboardViewModel`,
 *   `OptimizedLayout`, `WordIndex`, `SuggestionRepository`, `DojoViewModel` sind JVM-testbar
 *   ohne Robolectric. Der IME-Service ist dünner Glue und wird nicht unit-getestet.
 * - **Robolectric nur für echtes Android-Runtime.** Wo ein Test wirklich die Plattform
 *   braucht (DataStore-Round-Trip in `DojoStatsRepositoryTest`/`DojoBestPersistenceTest`),
 *   ist Robolectric mit `@Config(sdk = [36])` erlaubt — aber die Ausnahme, nicht der
 *   Default. Den Context im Test über `RuntimeEnvironment.getApplication()` holen (kein
 *   `androidx.test:core` nötig).
 * - `unitTests.isReturnDefaultValues = true` ist gesetzt (Framework-Klassen geben in
 *   JVM-Tests Defaults zurück).
 *
 * ## Muster
 *
 * - **Fake-InputConnection**: ein `mockk<InputConnection>(relaxed = true)` mit einem
 *   echten `StringBuilder`-Puffer hinter `commitText` / `deleteSurroundingText` /
 *   `getTextBeforeCursor` (siehe `KeyboardViewModelTest`). So testet man das
 *   Tipp-Verhalten ohne Android-Runtime.
 * - **Layout-Reise-Property**: `OptimizedLayoutTravelTest` pinnt, dass die optimierte
 *   Anordnung deutlich kürzer reist als QWERTZ-CH — schützt vor Verschlechterung.
 * - **Kein-Autocorrect-Garantie testen**: `vorschlag-tap ersetzt nur das aktuelle
 *   Wort` belegt, dass fertiger Text nie angefasst wird. Diese Invariante immer
 *   mittesten, wenn du an der Vorschlags-/Commit-Logik schraubst.
 */
