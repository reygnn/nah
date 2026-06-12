package com.github.reygnn.nah.settings

/**
 * Laufzeit-Einstellungen. Fliessen über [com.github.reygnn.nah.viewmodel.KeyboardViewModel.applySettings]
 * in die laufende Tastatur — nicht über Konstruktor-Defaults, damit kein Tunable
 * "konfiguriert, aber nie gelesen" endet.
 */
data class Settings(
    /** Vorschlagsleiste. Standard AUS — Vorschläge ersetzen nie ein fertiges Wort,
     *  aber wer sie gar nicht im Augenwinkel will, lässt sie aus. */
    val suggestionsEnabled: Boolean = false,
    /** Benutzerdefinierte Wörter vorschlagen. Unabhängig von [suggestionsEnabled],
     *  damit man NUR die eigenen Wörter (ohne die de-CH-Liste) bekommen kann. Standard AUS. */
    val userWordsEnabled: Boolean = false,
    /** Beim Tippen gelernte Wörter (das Lesezeichen-Chip) vorschlagen. Eigener Schalter, unabhängig
     *  von [userWordsEnabled] (kuratiert) und [suggestionsEnabled] (Liste). Standard **AN**: was man
     *  bewusst speichert, soll auch ohne weiteres Zutun vorgeschlagen werden. Anders als die beiden
     *  „immer-an"-Quellen reserviert diese Quelle die Leiste NICHT dauerhaft — sie blendet sich für
     *  einen Treffer nur on-demand ein (wie das Save-Chip), damit kein leeres Leistenband per Default
     *  dauerhaft Platz frisst. Über sensiblen Feldern bleibt sie aus (siehe `computeSuggestions`). */
    val learnedWordsEnabled: Boolean = true,
    /** Leiste immer reserviert (feste Höhe), auch ohne aktive Vorschlagsquelle — so springt sie beim
     *  Tippen nicht, wenn das Live-„speichern"-Chip erscheint/verschwindet. Standard AUS (ist eine
     *  Vorschlagsquelle an, ist die Leiste ohnehin reserviert). Über sensiblen Feldern bleibt sie aus. */
    val barAlwaysVisible: Boolean = false,
    /** Auto-Grossschreibung am Satzanfang (toggelbar). Kein Autocorrect. */
    val autoCapEnabled: Boolean = true,
    /** „Stützräder": Vokale und die häufigsten Konsonanten farbig einfärben, bis das
     *  Muskelgedächtnis sitzt. Fixe Farben (kein Material You) — stabiler Lern-Anker.
     *  Standard AUS. */
    val letterColorHintsEnabled: Boolean = false,
)
