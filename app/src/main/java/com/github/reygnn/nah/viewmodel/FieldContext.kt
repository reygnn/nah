package com.github.reygnn.nah.viewmodel

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Aus `EditorInfo` destillierte Feld-Eigenschaften, die das Tippverhalten steuern.
 * Reine Daten: der Service liest `EditorInfo` (Android-Kram), die Entscheidungen
 * bleiben im [KeyboardViewModel] und damit JVM-testbar.
 */
data class FieldContext(
    /**
     * Die von der Return-Taste auszulösende Editor-Action (`EditorInfo.IME_ACTION_*`,
     * z. B. Suchen/Senden/Los/Weiter) — oder `null`, wenn das Feld keine verlangt und
     * Return ein echtes Enter (Zeilenumbruch) sein soll.
     */
    val imeAction: Int? = null,
    /**
     * Das Feld erwartet primär Ziffern (Zahl/Telefon/Datum) → direkt auf der
     * Symbol-/Ziffernebene starten, statt den Nutzer für eine PLZ/PIN/Betrag erst
     * `?123` drücken zu lassen. Das volle Alphabet bleibt einen Tap entfernt (`ABC`).
     */
    val numeric: Boolean = false,
    /**
     * Passwortfeld → Auto-Grossschreibung UND Vorschläge unterdrücken: ein
     * case-sensitives Passwort soll nicht versehentlich kapitalisiert werden, und
     * über der Eingabe sollen keine Präfix-Vorschläge erscheinen (Schulter-Surfen,
     * und nah „lernt" Passwörter ohnehin nie).
     */
    val isPassword: Boolean = false,
    /**
     * Cursor-/Auswahlposition beim Feldstart (aus `EditorInfo.initialSel*`). Ohne
     * diese wüsste der selektionsbewusste Backspace bis zum ersten `onUpdateSelection`
     * nichts von einer bereits im Zielfeld bestehenden Auswahl. Unbekannt (`-1`) → 0.
     */
    val initialSelStart: Int = 0,
    val initialSelEnd: Int = 0,
) {
    companion object {
        /**
         * Destilliert die fürs Tippverhalten relevanten Felder aus den rohen
         * `EditorInfo`-Werten. Bewusst über die nackten `Int`s statt über das ganze
         * [EditorInfo]-Objekt — so bleibt die Bitmasken-Logik rein und JVM-testbar
         * (die `IME_*`/`InputType.TYPE_*`-Konstanten sind Compile-Zeit-Konstanten,
         * brauchen also keine Android-Runtime). Der Service reicht nur die Werte herein.
         */
        fun fromEditorInfo(
            imeOptions: Int,
            inputType: Int,
            initialSelStart: Int,
            initialSelEnd: Int,
        ): FieldContext = FieldContext(
            imeAction = imeActionOrNull(imeOptions),
            numeric = isNumericInputType(inputType),
            isPassword = isPasswordInputType(inputType),
            initialSelStart = initialSelStart.coerceAtLeast(0),
            initialSelEnd = initialSelEnd.coerceAtLeast(0),
        )

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

        /** Zahl-, Telefon- oder Datums-/Zeitfeld → Ziffernebene zuerst. */
        private fun isNumericInputType(inputType: Int): Boolean =
            when (inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE,
                InputType.TYPE_CLASS_DATETIME,
                -> true
                else -> false
            }

        /**
         * Passwort-Varianten (Text sichtbar/unsichtbar/Web, sowie numerisches PIN-Feld) —
         * gleiche Klassifikation wie das Framework selbst (`TextView.isPasswordInputType`).
         */
        private fun isPasswordInputType(inputType: Int): Boolean {
            val cls = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val textPassword = cls == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                )
            val numberPassword = cls == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            return textPassword || numberPassword
        }
    }
}
