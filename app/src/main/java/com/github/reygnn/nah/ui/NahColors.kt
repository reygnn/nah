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

    /** Sehr seltene Buchstaben — kalt und zurückgenommen, damit das Auge nicht über
     *  sie stolpert: dunkler, desaturierter Container mit dimmem (kontrastarmem) Label. */
    val RareKey = Color(0xFF23272D)
    val RareOn = Color(0xFF59626C)

    enum class Hint { Vowel, Consonant, Rare }

    private val vowels = "aeiouäöü".toSet()

    /** Die häufigsten Konsonanten — im Vokal-Cluster-Layout grenzen s/r/h direkt an die
     *  Vokale, n/t/d liegen in der unteren Reihe. Bewusst klein gehalten — zu viele Farben
     *  wären kein Anker mehr. */
    private val keyConsonants = "srntdh".toSet()

    /** Praktisch nie gebraucht (Korpus: x = 0 %, y = 0.23 %). Bewusst nur diese zwei —
     *  w/z sind häufiger als sie wirken und bleiben darum normal. */
    private val rareLetters = "xy".toSet()

    /** Welche Hinweisfarbe gilt für diesen Buchstaben (oder `null` = neutral)? Rein,
     *  case-insensitiv — JVM-testbar. */
    fun hintFor(char: Char): Hint? = when (char.lowercaseChar()) {
        in vowels -> Hint.Vowel
        in keyConsonants -> Hint.Consonant
        in rareLetters -> Hint.Rare
        else -> null
    }
}
