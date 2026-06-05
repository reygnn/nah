package com.github.reygnn.nah.viewmodel

import android.view.inputmethod.EditorInfo

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
) {
    companion object {
        /**
         * Destilliert die fürs Tippverhalten relevanten Felder aus den rohen
         * [EditorInfo.imeOptions]. Bewusst über das `Int` statt über das ganze
         * [EditorInfo]-Objekt — so bleibt die Bitmasken-Logik rein und JVM-testbar
         * (die `IME_*`-Konstanten sind Compile-Zeit-Konstanten, brauchen also keine
         * Android-Runtime). Der Service reicht nur `info.imeOptions` herein.
         */
        fun fromImeOptions(imeOptions: Int): FieldContext =
            FieldContext(imeAction = imeActionOrNull(imeOptions))

        /**
         * Die auszulösende Editor-Action, oder `null`, wenn das Feld keine verlangt (oder
         * explizit `IME_FLAG_NO_ENTER_ACTION` setzt) — dann bleibt Return ein echtes Enter.
         */
        private fun imeActionOrNull(imeOptions: Int): Int? {
            if ((imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return null
            return when (val action = imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_UNSPECIFIED -> null
                else -> action
            }
        }
    }
}
