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
    /** Tastaturhöhe als Anteil der Bildschirmhöhe. */
    val keyboardHeightFraction: Float = 0.34f,
    /** Auto-Grossschreibung am Satzanfang (toggelbar). Kein Autocorrect. */
    val autoCapEnabled: Boolean = true,
    /** Platzhalter für den Fast-Follow (MissMap-Offset-Lernen). v1 ungenutzt. */
    val missMapLearningEnabled: Boolean = false,
)
