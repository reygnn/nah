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
     * Reise-Metrik (Test) und später für das MissMap-Offset-Lernen — abgeleitet
     * aus derselben Reihen-Definition, nicht doppelt gepflegt.
     */
    fun letterPositions(): Map<Char, Pair<Float, Float>> {
        val out = mutableMapOf<Char, Pair<Float, Float>>()
        rows.forEachIndexed { rowIdx, row ->
            val chars = row.filterIsInstance<CharKey>()
            val n = chars.size
            chars.forEachIndexed { colIdx, key ->
                val x = colIdx - (n - 1) / 2f
                out[key.char] = x to rowIdx.toFloat()
            }
        }
        return out
    }
}
