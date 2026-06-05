package com.github.reygnn.nah.viewmodel

/**
 * Aus `EditorInfo` destillierte Feld-Eigenschaften, die das Tippverhalten steuern.
 * Reine Daten: der Service liest `EditorInfo` (Android-Kram), die Entscheidungen
 * bleiben im [KeyboardViewModel] und damit JVM-testbar. Bewusst klein gehalten —
 * weitere Felder (Vorschläge/Auto-Cap je nach Feldtyp) kämen hier dazu.
 */
data class FieldContext(
    /**
     * Die von der Return-Taste auszulösende Editor-Action (`EditorInfo.IME_ACTION_*`,
     * z. B. Suchen/Senden/Los/Weiter) — oder `null`, wenn das Feld keine verlangt und
     * Return ein echtes Enter (Zeilenumbruch) sein soll.
     */
    val imeAction: Int? = null,
)
