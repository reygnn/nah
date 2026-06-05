package com.github.reygnn.nah.ui

import androidx.compose.ui.graphics.Color

/**
 * Fixe „Stützräder"-Farben für die Lernhilfe (optionaler Schalter). Bewusst NICHT
 * Material You: ein Muskelgedächtnis-Anker muss stabil sein und darf nicht mit dem
 * Wallpaper driften. Dunkle, getönte Container, damit die hellen Labels lesbar
 * bleiben und sich beide Hinweisfarben klar voneinander und von den neutralen Tasten
 * abheben. Greift nur, wenn [com.github.reygnn.nah.settings.Settings.letterColorHintsEnabled].
 */
object NahColors {

    /** Vokale — warmer Ton. */
    val VowelKey = Color(0xFF5A4216)
    val VowelOn = Color(0xFFFFE2A8)

    /** Häufigste Konsonanten — kühler Ton. */
    val ConsonantKey = Color(0xFF12414F)
    val ConsonantOn = Color(0xFFAFE5F5)

    enum class Hint { Vowel, Consonant }

    private val vowels = "aeiouäöü".toSet()

    /** Die häufigsten Konsonanten — im Vokal-Cluster-Layout grenzen s/r/h direkt an die
     *  Vokale, n/t/d liegen in der unteren Reihe. Bewusst klein gehalten — zu viele Farben
     *  wären kein Anker mehr. */
    private val keyConsonants = "srntdh".toSet()

    /** Welche Hinweisfarbe gilt für diesen Buchstaben (oder `null` = neutral)? Rein,
     *  case-insensitiv — JVM-testbar. */
    fun hintFor(char: Char): Hint? = when (char.lowercaseChar()) {
        in vowels -> Hint.Vowel
        in keyConsonants -> Hint.Consonant
        else -> null
    }
}
