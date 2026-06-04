# Test-Source-Set — Orientierung

Vollständige Referenz: `../../../TESTING_CONVENTIONS.md` im Projektwurzel.

Kurz:
- JUnit 4 + MockK + kotlinx-coroutines-test. Kein Robolectric, kein Mockito.
- `MainDispatcherRule` + `runTest(rule.testDispatcher)` für alles, was
  `Dispatchers.Main` berührt.
- Pure Logik (ViewModel, Layout, Trie) JVM-testen; den IME-Service nicht.
- Beim Anfassen der Commit-/Vorschlagslogik **immer** die „kein Autocorrect"-
  Invariante mittesten (Vorschlag ersetzt nur das aktuelle unfertige Wort).
