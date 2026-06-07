# Trie-Suggestion-Benchmark

Reproduzierbare Messung der **UI-Thread-Kosten pro Tastendruck** von
`Trie.getSuggestions` bzw. `SuggestionRepository.suggest`. Hintergrund: der Trie
besucht bei jedem Tastendruck den **ganzen Teilbaum** unter dem Präfix (bewusst ohne
Branch-and-Bound, siehe `Trie.collectWords`). Diese Doku beantwortet quantitativ, **ab
welcher Korpusgrösse** das den 60-fps-Frame (16,6 ms) gefährdet — damit ein künftiger
„grösseres Optimizer-Korpus"-Schritt (CLAUDE.md → Fast-Follow) nicht im Blindflug passiert.

## Kernaussage (TL;DR)

- **Heute (~1440 Wörter): irrelevant** — Worst-Case ~7 µs (Desktop), ~58 µs (Gerät geschätzt).
- **Grün bis ~50 000 Wörter** — Worst-Case bleibt unter ~1 ms auf dem Gerät.
- **Eng ab ~100k–200k Wörtern** — mehrere ms auf dem UI-Thread am seltenen 2-Zeichen-Präfix.
  **Das** ist die Schwelle, ab der sich die in `Trie.collectWords` skizzierte
  Branch-and-Bound-Optimierung (max. Teilbaum-Frequenz je Knoten cachen, chancenlose Äste
  kappen) lohnt — vorher nachweislich nicht.

→ **Kein Handlungsbedarf**, solange der Korpus unter ~50k Wörtern bleibt (ein realistisches
de-CH-Frequenzwörterbuch liegt bei einigen Tausend bis ~30k).

## Messergebnis

JVM (Desktop, single-thread, JIT-warm), worst-case = der 2-Zeichen-Präfix mit dem
**grössten** Teilbaum (Vorschläge feuern erst ab `MIN_PREFIX_LENGTH = 2`, 2 Zeichen ist also
der realistische Worst Case). 50 000 Warmup- + 200 000 Mess-Iterationen je Zeile.

| Korpus              | grösster 2-Zeichen-Teilbaum | `Trie.getSuggestions` | `suggest()` (voller Pfad) |
| ------------------- | --------------------------- | --------------------- | ------------------------- |
| **REAL (1441)**     | „ge", 61 Wörter             | **7,2 µs**            | 6,1 µs                    |
| SYN 5 000           | 99 Wörter                   | 11,0 µs               | 10,7 µs                   |
| SYN 50 000          | 851 Wörter                  | 121 µs                | 115 µs                    |
| SYN 200 000         | 3 246 Wörter                | 572 µs                | 580 µs                    |

Skaliert ~linear mit der Wortzahl im Teilbaum (≈0,1–0,2 µs/Wort) — wie für „besuche den
ganzen Teilbaum + sortiere" erwartet. Der `suggest()`-Merge-Overhead (≤3+≤3 Resultate
dedupen/sortieren) ist vernachlässigbar.

### Hochrechnung aufs Gerät

Pixel 9a (Tensor G3) ist für diese Pointer-Chasing-/Allokations-Last grob **~3–8×**
langsamer als die Desktop-JVM. Konservativ ×8:

| Korpus            | geschätzt/Tastendruck (worst case) | Anteil 16,6-ms-Frame |
| ----------------- | ---------------------------------- | -------------------- |
| **REAL (heute)**  | **~58 µs**                         | 0,35 % — unsichtbar  |
| 5 000             | ~88 µs                             | 0,5 %                |
| 50 000            | ~1,0 ms                            | 6 %                  |
| 200 000           | **~4,6 ms**                        | 28 %                 |

Der **typische** Tastendruck (Präfix ≥ 3 Zeichen, kleinerer Teilbaum) ist deutlich
billiger als diese 2-Zeichen-Worst-Cases.

## Reproduzieren

Wegwerf-Benchmark (wie `optimize_layout.py`: reproduzierbar, **nicht** eingecheckt — würde
sonst die Test-Suite um ~7 min verlängern). Als
`app/src/test/java/com/github/reygnn/nah/data/suggestions/TrieBenchmarkThrowaway.kt`
anlegen, mit `./gradlew :app:testDebugUnitTest --tests "*TrieBenchmarkThrowaway"` laufen
lassen, Ergebnis aus `/tmp/trie_bench.txt` lesen, danach **wieder löschen**.

```kotlin
package com.github.reygnn.nah.data.suggestions

import java.io.File
import kotlin.random.Random
import org.junit.Test

/** WEGWERF — Benchmark für die UI-Thread-Kosten von Trie.getSuggestions / suggest.
 *  Schreibt nach /tmp/trie_bench.txt. Nach dem Lauf wieder löschen. */
class TrieBenchmarkThrowaway {

    private fun worstPrefixKey(words: List<String>): String =
        words.filter { it.length >= 2 }
            .groupingBy { it.substring(0, 2).lowercase() }
            .eachCount().maxByOrNull { it.value }!!.key

    private fun worstPrefix(words: List<String>): String =
        worstPrefixKey(words).let { k ->
            "$k (${words.count { it.length >= 2 && it.substring(0, 2).lowercase() == k }} Wörter)"
        }

    /** Erste zwei Buchstaben aus dem häufigen Kopf des Alphabets → starke Teilbäume (worst case). */
    private fun genWords(n: Int, seed: Int): List<String> {
        val rng = Random(seed)
        val alphabet = "etaoinshrdlcumwfgypbvkjxqzäöü"
        val set = HashSet<String>(n * 2)
        while (set.size < n) {
            val len = 3 + rng.nextInt(10)
            val sb = StringBuilder(len)
            repeat(len) { sb.append(alphabet[rng.nextInt(if (sb.length < 2) 8 else alphabet.length)]) }
            set.add(sb.toString())
        }
        return set.toList()
    }

    private fun bench(label: String, out: StringBuilder, words: List<String>, trie: Trie) {
        val key = worstPrefixKey(words)
        repeat(50_000) { trie.getSuggestions(key, 3) }
        var sink = 0
        val iters = 200_000
        val t0 = System.nanoTime()
        repeat(iters) { sink += trie.getSuggestions(key, 3).size }
        val nsTrie = (System.nanoTime() - t0).toDouble() / iters

        val repo = SuggestionRepository().apply { setUserWords(words.toSet()) }
        repeat(50_000) { repo.suggest(key, false, true) }
        val t2 = System.nanoTime()
        repeat(iters) { sink += repo.suggest(key, false, true).size }
        val nsSuggest = (System.nanoTime() - t2).toDouble() / iters

        out.appendLine(
            "%-10s | Wörter=%-7d | worst '%s' | getSuggestions=%6.1f µs | suggest=%6.1f µs (sink=%d)"
                .format(label, words.size, worstPrefix(words), nsTrie / 1000, nsSuggest / 1000, sink and 1),
        )
    }

    @Test
    fun benchmark() {
        val out = StringBuilder()
        val realTrie = Trie().apply { GermanWordList.words.forEach { (w, f) -> insert(w, f) } }
        bench("REAL", out, GermanWordList.words.map { it.first }, realTrie)
        for ((label, n) in listOf("SYN 5k" to 5_000, "SYN 50k" to 50_000, "SYN 200k" to 200_000)) {
            val words = genWords(n, seed = 42)
            bench(label, out, words, Trie().apply { words.forEach { insert(it, 1) } })
        }
        File("/tmp/trie_bench.txt").writeText(out.toString())
    }
}
```

_Messung: Opus-4.8-Session, 2026-06-07, gegen den Korpus dieses Commits (1441 Wörter; nach dem
Listen-Ausbau von 363 → ~1440 neu gemessen). Desktop-JVM; Gerätezahlen sind eine konservative
×8-Hochrechnung, keine On-device-Messung._
