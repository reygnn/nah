package com.github.reygnn.nah.layout

import org.junit.Assert.assertTrue
import kotlin.math.hypot
import org.junit.Test

/**
 * Pinnt die Kern-Eigenschaft des optimierten Layouts: die Fingerreise für einen
 * Finger liegt deutlich unter QWERTZ-CH. Schützt davor, die Anordnung versehentlich
 * zu verschlechtern. Verwendet eine kompakte Tabelle häufiger deutscher Bigramme
 * (Rankings sind sprachstabil); die volle Optimierung liegt in tools/optimize_layout.py.
 */
class OptimizedLayoutTravelTest {

    // Häufige deutsche Bigramme mit groben relativen Gewichten.
    private val bigrams = mapOf(
        "er" to 40, "en" to 38, "ch" to 30, "de" to 26, "ei" to 24,
        "te" to 22, "in" to 22, "nd" to 20, "ie" to 19, "ge" to 16,
        "st" to 15, "ne" to 14, "be" to 13, "es" to 13, "un" to 12,
        "re" to 12, "an" to 11, "he" to 11, "se" to 10, "nt" to 10,
    )

    private fun travel(pos: Map<Char, Pair<Float, Float>>): Double {
        var sum = 0.0
        var total = 0
        for ((bg, w) in bigrams) {
            val a = pos[bg[0]] ?: continue
            val b = pos[bg[1]] ?: continue
            sum += w * hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble())
            total += w
        }
        return sum / total
    }

    /** QWERTZ-CH in gleicher Koordinatenkonvention (Reihe 0/1/2, gestaffelt). */
    private fun qwertzPositions(): Map<Char, Pair<Float, Float>> {
        val rows = listOf(
            "qwertzuiopü" to 0.0f,
            "asdfghjklöä" to 0.25f,
            "yxcvbnm" to 0.75f,
        )
        val out = mutableMapOf<Char, Pair<Float, Float>>()
        rows.forEachIndexed { rowIdx, (chars, xoff) ->
            chars.forEachIndexed { col, ch -> out[ch] = (xoff + col) to rowIdx.toFloat() }
        }
        return out
    }

    @Test
    fun `optimiertes Layout reist deutlich kuerzer als QWERTZ`() {
        val optimized = travel(OptimizedLayout.deCh().letterPositions())
        val qwertz = travel(qwertzPositions())
        // Mindestens 15 % kürzer — in der Praxis (voller Korpus) deutlich mehr.
        assertTrue(
            "optimiert=$optimized sollte < 0.85 * qwertz=$qwertz sein",
            optimized < 0.85 * qwertz,
        )
    }

    @Test
    fun `alle 29 Buchstaben sind im Layout vorhanden`() {
        val chars = OptimizedLayout.deCh().letterPositions().keys
        assertTrue(chars.containsAll("abcdefghijklmnopqrstuvwxyzäöü".toList()))
    }
}
