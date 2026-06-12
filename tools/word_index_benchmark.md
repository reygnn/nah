# WordIndex-Suggestion-Benchmark

Reproduzierbare Messung der **UI-Thread-Kosten pro Tastendruck** von
`WordIndex.getSuggestions` bzw. `SuggestionRepository.suggest`. Hintergrund: der
[WordIndex](../app/src/main/java/com/github/reygnn/nah/data/suggestions/WordIndex.kt)
ist ein sortiertes Array; pro Tastendruck eine Binärsuche (`lowerBound`) plus Linearscan
über **genau die Treffer** des Präfixes, danach Sortierung dieses Bereichs nach Frequenz.
Kostet also ~linear in der Zahl der Wörter unter dem Präfix. Diese Doku beantwortet
quantitativ, **ab welcher Korpusgrösse** das den 60-fps-Frame (16,6 ms) gefährdet — damit
ein künftiger „grösseres Optimizer-Korpus"-Schritt (CLAUDE.md → Fast-Follow) nicht im
Blindflug passiert.

> **Vorgeschichte.** Bis Version 0.8.x war das ein Trie. Bei dieser Korpusgrösse kaufte die
> Baumstruktur nichts (gleiche „besuche-den-Bereich-und-sortiere"-Kosten, aber Pointer-Chasing
> + eine `HashMap` pro Knoten). Der Wechsel auf sortiertes Array + Binärsuche ist einfacher,
> speicherärmer und messbar **~2–7× schneller** (Vergleich unten).

## Kernaussage (TL;DR)

- **Heute (~1440 Wörter): irrelevant** — Worst-Case ~3 µs (Desktop), ~27 µs (Gerät geschätzt).
- **Grün bis weit über 200 000 Wörter** — Worst-Case bleibt unter ~1 ms auf dem Gerät.
- Erst jenseits ~1 Mio. Wörtern (kein realistisches de-CH-Wörterbuch) würde der Bereichsscan
  am seltenen 2-Zeichen-Präfix mehrere ms kosten. **Das** wäre die Schwelle, ab der sich ein
  vorberechnetes Per-Präfix-Top-k (oder ein Kappen des Scan-Bereichs) lohnt — vorher nicht.

→ **Kein Handlungsbedarf** in jeder absehbaren Korpusgrösse (ein realistisches de-CH-
Frequenzwörterbuch liegt bei einigen Tausend bis ~30k).

## Messergebnis

JVM (Desktop, single-thread, JIT-warm), worst-case = der 2-Zeichen-Präfix mit dem
**grössten** Trefferbereich (Vorschläge feuern erst ab `MIN_PREFIX_LENGTH = 2`, 2 Zeichen ist
also der realistische Worst Case). 50 000 Warmup- + 200 000 Mess-Iterationen je Zeile.

| Korpus              | grösster 2-Zeichen-Bereich | `WordIndex.getSuggestions` | `suggest()` (voller Pfad) |
| ------------------- | -------------------------- | -------------------------- | ------------------------- |
| **REAL (1441)**     | „ge", 61 Wörter            | **3,4 µs**                 | 1,6 µs                    |
| SYN 5 000           | „se", 99 Wörter            | 2,7 µs                     | 2,7 µs                    |
| SYN 50 000          | „se", 851 Wörter           | 21,6 µs                    | 25,2 µs                   |
| SYN 200 000         | „hs", 3 246 Wörter         | 83,6 µs                    | 107 µs                    |

Skaliert ~linear mit der Wortzahl im Trefferbereich (≈0,03–0,04 µs/Wort) — wie für
„scanne den Bereich + sortiere" erwartet, aber mit deutlich kleinerer Konstante als der
frühere Trie (≈0,1–0,2 µs/Wort). Der `suggest()`-Merge-Overhead (≤3+≤3 Resultate
dedupen/sortieren) ist vernachlässigbar.

### Zum Vergleich: der frühere Trie (gleiche Maschine, gleicher Korpus)

| Korpus          | Trie `getSuggestions` | WordIndex `getSuggestions` | Faktor |
| --------------- | --------------------- | -------------------------- | ------ |
| REAL (1441)     | 7,2 µs                | 3,4 µs                     | ~2,1×  |
| SYN 50 000      | 121 µs                | 21,6 µs                    | ~5,6×  |
| SYN 200 000     | 572 µs                | 83,6 µs                    | ~6,8×  |

### Hochrechnung aufs Gerät

Pixel 9a (Tensor G3) ist für diese Last grob **~3–8×** langsamer als die Desktop-JVM.
Konservativ ×8:

| Korpus            | geschätzt/Tastendruck (worst case) | Anteil 16,6-ms-Frame |
| ----------------- | ---------------------------------- | -------------------- |
| **REAL (heute)**  | **~27 µs**                         | 0,16 % — unsichtbar  |
| 5 000             | ~22 µs                             | 0,13 %               |
| 50 000            | ~0,2 ms                            | 1,2 %                |
| 200 000           | **~0,9 ms**                        | 5 %                  |

Der **typische** Tastendruck (Präfix ≥ 3 Zeichen, kleinerer Bereich) ist deutlich
billiger als diese 2-Zeichen-Worst-Cases.

## Reproduzieren

Wegwerf-Benchmark (wie `optimize_layout.py`: reproduzierbar, **nicht** eingecheckt — würde
sonst die Test-Suite um ~7 min verlängern). Als
`app/src/test/java/com/github/reygnn/nah/data/suggestions/WordIndexBenchmarkThrowaway.kt`
anlegen, mit `./gradlew :app:testDebugUnitTest --tests "*WordIndexBenchmarkThrowaway"` laufen
lassen, Ergebnis aus `/tmp/word_index_bench.txt` lesen, danach **wieder löschen**.

```kotlin
package com.github.reygnn.nah.data.suggestions

import java.io.File
import kotlin.random.Random
import org.junit.Test

/** WEGWERF — Benchmark für die UI-Thread-Kosten von WordIndex.getSuggestions / suggest.
 *  Schreibt nach /tmp/word_index_bench.txt. Nach dem Lauf wieder löschen. */
class WordIndexBenchmarkThrowaway {

    private fun worstPrefixKey(words: List<String>): String =
        words.filter { it.length >= 2 }
            .groupingBy { it.substring(0, 2).lowercase() }
            .eachCount().maxByOrNull { it.value }!!.key

    private fun worstPrefix(words: List<String>): String =
        worstPrefixKey(words).let { k ->
            "$k (${words.count { it.length >= 2 && it.substring(0, 2).lowercase() == k }} Wörter)"
        }

    /** Erste zwei Buchstaben aus dem häufigen Kopf des Alphabets → starke Präfix-Bereiche (worst case). */
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

    private fun bench(label: String, out: StringBuilder, words: List<String>, index: WordIndex) {
        val key = worstPrefixKey(words)
        repeat(50_000) { index.getSuggestions(key, 3) }
        var sink = 0
        val iters = 200_000
        val t0 = System.nanoTime()
        repeat(iters) { sink += index.getSuggestions(key, 3).size }
        val nsIndex = (System.nanoTime() - t0).toDouble() / iters

        val repo = SuggestionRepository().apply { setUserWords(words.toSet()) }
        repeat(50_000) { repo.suggest(key, false, true, false) }
        val t2 = System.nanoTime()
        repeat(iters) { sink += repo.suggest(key, false, true, false).size }
        val nsSuggest = (System.nanoTime() - t2).toDouble() / iters

        out.appendLine(
            "%-10s | Wörter=%-7d | worst '%s' | getSuggestions=%6.1f µs | suggest=%6.1f µs (sink=%d)"
                .format(label, words.size, worstPrefix(words), nsIndex / 1000, nsSuggest / 1000, sink and 1),
        )
    }

    @Test
    fun benchmark() {
        val out = StringBuilder()
        val realIndex = WordIndex().apply { GermanWordList.words.forEach { (w, f) -> insert(w, f) } }
        bench("REAL", out, GermanWordList.words.map { it.first }, realIndex)
        for ((label, n) in listOf("SYN 5k" to 5_000, "SYN 50k" to 50_000, "SYN 200k" to 200_000)) {
            val words = genWords(n, seed = 42)
            bench(label, out, words, WordIndex().apply { words.forEach { insert(it, 1) } })
        }
        File("/tmp/word_index_bench.txt").writeText(out.toString())
    }
}
```

_Messung: Opus-4.8-Session, 2026-06-07, gegen den Korpus dieses Commits (1441 Wörter), nach
dem Wechsel Trie → WordIndex neu gemessen. Desktop-JVM; Gerätezahlen sind eine konservative
×8-Hochrechnung, keine On-device-Messung. Die Trie-Vergleichszahlen stammen aus der vorigen
Messung (2026-06-07) gegen denselben Korpus._
