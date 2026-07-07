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
     * Das Feld erwartet primär Ziffern (Zahl/Telefon/Datum) → direkt auf einer
     * ziffern­zentrierten Ebene starten, statt den Nutzer für eine PLZ/PIN/Betrag erst
     * `SYM` drücken zu lassen. Das volle Alphabet bleibt einen Tap entfernt (`ABC`).
     */
    val numeric: Boolean = false,
    /**
     * Speziell ein Telefonfeld (`TYPE_CLASS_PHONE`) → eigenes Wähl­feld-Layout (Dialpad)
     * statt der allgemeinen Symbolebene. Impliziert [numeric]; reine Zahlen-/Datumsfelder
     * setzen nur [numeric].
     */
    val phone: Boolean = false,
    /**
     * Passwortfeld → Auto-Grossschreibung UND Vorschläge unterdrücken: ein
     * case-sensitives Passwort soll nicht versehentlich kapitalisiert werden, und
     * über der Eingabe sollen keine Präfix-Vorschläge erscheinen (Schulter-Surfen,
     * und nah „lernt" Passwörter ohnehin nie).
     */
    val isPassword: Boolean = false,
    /**
     * Das Feld setzt `TYPE_TEXT_FLAG_NO_SUGGESTIONS` (typisch für Kreditkartennummer-, OTP-/2FA-
     * Code- und Benutzernamenfelder) → Vorschlagsleiste unterdrücken, auch wenn es KEIN Passwortfeld
     * ist. Respektiert das ausdrückliche „hier keine Vorschläge"-Signal des Ziel-Editors; verhindert,
     * dass über einem sensiblen Feld eigene kuratierte Wörter (Namen/Adressen) als Vorschlag auftauchen.
     */
    val noSuggestions: Boolean = false,
    /**
     * URL- oder E-Mail-Feld (`TYPE_TEXT_VARIATION_URI` / `…_EMAIL_ADDRESS` / `…_WEB_EMAIL_ADDRESS`)
     * → Auto-Grossschreibung unterdrücken. In solchen Feldern ist der Punkt ein Domain-Trenner,
     * kein Satzende: die Satz-Heuristik würde sonst „google.Com" oder „john@gmail.Com" armieren.
     * Gleiche Haltung wie [isPassword]/[numeric] — nah bleibt Herr der Grossschreibung, macht aber
     * für Feldklassen, in denen sie nie erwünscht ist, eine Ausnahme (statt die `CAP_*`-Flags des
     * Feldes zu lesen, die nah bewusst ignoriert). Betrifft NUR die Auto-Cap, nicht die Vorschläge.
     */
    val uri: Boolean = false,
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
            phone = isPhoneInputType(inputType),
            isPassword = isPasswordInputType(inputType),
            noSuggestions = isNoSuggestionsInputType(inputType),
            uri = isUriInputType(inputType),
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

        /** Speziell ein Telefonfeld → eigenes Wählfeld statt der allgemeinen Symbolebene. */
        private fun isPhoneInputType(inputType: Int): Boolean =
            (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE

        /**
         * Das Feld bittet ausdrücklich um keine Vorschläge (`TYPE_TEXT_FLAG_NO_SUGGESTIONS`). Nur für
         * die Textklasse ausgewertet — das Flag-Bit ist nur dort definiert; in anderen Klassen
         * (Number/Phone/Datetime) bedeutet dasselbe Bit etwas anderes und darf nicht mitgelesen werden.
         */
        private fun isNoSuggestionsInputType(inputType: Int): Boolean =
            (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
                (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0

        /**
         * URL- oder E-Mail-Feld. Nur in der Textklasse ausgewertet (die Variation-Bits sind nur
         * dort so definiert), gleiche Masken-Logik wie [isPasswordInputType]. Der Web-URL-Typ hat
         * keine eigene Variation — `<input type="url">` kommt als `TYPE_TEXT_VARIATION_URI` an —,
         * darum reicht diese eine URI-Variation neben den beiden E-Mail-Varianten.
         */
        private fun isUriInputType(inputType: Int): Boolean {
            if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
            return when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                -> true
                else -> false
            }
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
