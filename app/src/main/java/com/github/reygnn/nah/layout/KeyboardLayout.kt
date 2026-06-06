package com.github.reygnn.nah.layout

/**
 * Ein fixes Tastatur-Layout als Reihen von Tasten. Jede Reihe wird in der UI
 * über die volle Breite gelegt; die Tasten teilen sich die Breite per
 * [KeyboardKey.weight].
 */
data class KeyboardLayout(
    val rows: List<List<KeyboardKey>>,
) {
    /**
     * Grid-Koordinaten der Buchstaben-Tasten (Spaltenmitte je Reihe, horizontal
     * um 0 zentriert; y = Reihenindex). Einzige Quelle der Wahrheit für die
     * Reise-Metrik (Test) — abgeleitet aus derselben Reihen-Definition, nicht
     * doppelt gepflegt.
     *
     * **Einheiten-Caveat:** Die Metrik rechnet in Grid-Einheiten — ein Spaltenschritt
     * (Tastenbreite) zählt gleich viel wie ein Reihenschritt (Reihenhöhe). Physisch sind
     * die Tasten nicht quadratisch (am Pixel 9a ~51 dp breit vs. 58 dp hoch, plus Totzonen),
     * und ein realer Finger reist nicht Mitte-zu-Mitte. Die „~36 % weniger Reise" ist also
     * ein **relativer** Vergleich gegen QWERTZ-CH in derselben Konvention, keine gemessene
     * Strecke in mm. Für den Zweck (Anordnung optimieren/gegen Regression schützen) genügt
     * das; der Optimizer (`tools/optimize_layout.py`) nutzt dasselbe Modell, ist also in sich
     * konsistent.
     *
     * **Spalten-Hinweis:** Die Metrik setzt je Reihe gleich breite, zentrierte Spalten an
     * (`colIdx`) und ignoriert die [KeyboardKey.weight]e. Das deckt sich mit dem tatsächlichen
     * Bild, weil alle Buchstabenreihen — auch die unterste — dasselbe gleichmässige 7-Spalten-
     * Raster benutzen: Shift und Backspace haben dort bewusst `weight = 1f`, die Reihe hat also
     * genau 7 Tasten wie die Reihen darüber, und w/t/n/d/g fluchten exakt unter den Spalten
     * darüber (siehe `OptimizedLayout.deCh`). Nur die Funktionsreihe nutzt absichtlich breitere
     * Tasten — deren Tasten sind aber keine [CharKey]s und gehen nicht in die Metrik ein.
     */
    fun letterPositions(): Map<Char, Pair<Float, Float>> {
        val out = mutableMapOf<Char, Pair<Float, Float>>()
        rows.forEachIndexed { rowIdx, row ->
            val chars = row.filterIsInstance<CharKey>()
            val n = chars.size
            chars.forEachIndexed { colIdx, key ->
                val x = colIdx - (n - 1) / 2f
                // A duplicate letter would silently overwrite the earlier coordinate and yield a
                // WRONG travel metric that the regression test could not catch. Fail hard instead:
                // every letter must sit at exactly one coordinate for the metric to mean anything.
                check(key.char !in out) {
                    "Doppelter Buchstabe '${key.char}' im Layout — letterPositions ist mehrdeutig"
                }
                out[key.char] = x to rowIdx.toFloat()
            }
        }
        return out
    }
}
