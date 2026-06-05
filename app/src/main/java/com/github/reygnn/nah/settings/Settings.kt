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
    /** Auto-Grossschreibung am Satzanfang (toggelbar). Kein Autocorrect. */
    val autoCapEnabled: Boolean = true,
)
