package com.github.reygnn.nah.viewmodel

import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import androidx.lifecycle.ViewModel
import com.github.reygnn.nah.data.suggestions.Suggester
import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.FunctionKey
import com.github.reygnn.nah.layout.KeyAction
import com.github.reygnn.nah.layout.KeyboardKey
import com.github.reygnn.nah.layout.KeyboardLayout
import com.github.reygnn.nah.settings.Settings
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ShiftState {
    OFF,
    SHIFTED,
    CAPS;

    /**
     * Wendet die Schreibweise dieses Shift-Zustands auf [text] an: [SHIFTED] schreibt nur
     * den ersten Buchstaben gross („qu"→„Qu", „sch"→„Sch"), [CAPS] alles, [OFF] nichts.
     * **Einzige Quelle** für das Commit ([KeyboardViewModel]) UND die Tastenbeschriftung
     * (`ui/TapKey`), damit Angezeigtes und Getipptes nie auseinanderdriften — genau das
     * wäre ein stiller Bruch der „kein Autocorrect"-Garantie. Locale-unabhängig (ROOT),
     * damit derselbe Tap auf jedem Gerät dasselbe Zeichen liefert (kein türkisches i/İ).
     */
    fun applyTo(text: String): String = when (this) {
        OFF -> text
        SHIFTED -> text.replaceFirstChar { it.uppercaseChar() }
        CAPS -> text.uppercase(Locale.ROOT)
    }
}

data class KeyboardUiState(
    val layout: KeyboardLayout,
    val shift: ShiftState = ShiftState.OFF,
    val suggestions: List<String> = emptyList(),
    /** Ob die Vorschlagsleiste Platz belegt (feste Höhe), auch wenn [suggestions]
     *  gerade leer ist. So springt die Tastatur beim Erscheinen/Verschwinden der
     *  Vorschläge nicht in der Höhe. Aus, wenn die Funktion ganz deaktiviert ist. */
    val suggestionBarVisible: Boolean = false,
    /** „Stützräder": Vokale/häufige Konsonanten farbig einfärben (Lernhilfe). */
    val colorHints: Boolean = false,
    /** Hat die Zwischenablage Text? Steuert, ob die Einfügen-Taste aktiv ist. */
    val pasteAvailable: Boolean = false,
)

/**
 * Orchestriert das Tippen. Reine Entscheidungslogik + [StateFlow]; der IME-Service
 * ist dünner Glue. **Kein Autocorrect, keine Wortersetzung von fertigem Text** —
 * jeder Tap committet genau das getippte Zeichen. Vorschläge (falls aktiviert)
 * ersetzen nur das aktuelle, noch unfertige Präfix und nur auf Antippen.
 */
class KeyboardViewModel(
    private val alphaLayout: KeyboardLayout,
    private val symbolsLayout: KeyboardLayout,
    private val inputConnectionProvider: () -> InputConnection?,
    private val suggester: Suggester? = null,
    /** Liefert den aktuellen Zwischenablage-Text (oder `null`/leer). Wird NUR beim
     *  tatsächlichen Einfügen gelesen (Inhalt-Zugriff → System-Toast), nicht laufend. */
    private val clipboardTextProvider: () -> String? = { null },
) : ViewModel() {

    private val _state = MutableStateFlow(KeyboardUiState(layout = alphaLayout))
    val state: StateFlow<KeyboardUiState> = _state.asStateFlow()

    private var settings = Settings()

    // Vom Service über onSelectionChanged gepflegt. Eine aktive Auswahl (selStart !=
    // selEnd) muss Backspace löschen können; deleteSurroundingText würde sie nicht
    // anfassen.
    private var selStart = 0
    private var selEnd = 0
    private val hasSelection get() = selStart != selEnd

    // Eigenschaften des aktuellen Eingabefelds (z. B. die gewünschte Return-Action),
    // vom Service in onStartInput gesetzt.
    private var field = FieldContext()

    // Wurde der aktuelle SHIFTED-Zustand von der Auto-Großschreibung gesetzt (nicht
    // vom Nutzer)? Dann hebt ein Shift-Tap ihn direkt auf (→ OFF), statt erst über
    // CAPS zu zyklen — sonst bräuchte man zwei Taps mit Caps-Lock dazwischen, um die
    // automatische Armierung wieder loszuwerden.
    private var autoCapArmed = false

    fun applySettings(newSettings: Settings) {
        settings = newSettings
        if (_state.value.colorHints != newSettings.letterColorHintsEnabled) {
            _state.value = _state.value.copy(colorHints = newSettings.letterColorHintsEnabled)
        }
        refreshSuggestions() // gated intern — kümmert sich selbst ums Leeren
    }

    /**
     * Neues Eingabefeld beginnt: Feld-Eigenschaften übernehmen, zurück zur
     * Buchstabenebene, Auto-Cap neu bestimmen.
     */
    fun onStartInput(field: FieldContext = FieldContext(), pasteAvailable: Boolean = false) {
        this.field = field
        selStart = 0
        selEnd = 0
        _state.value = _state.value.copy(layout = alphaLayout, pasteAvailable = pasteAvailable)
        recomputeAutoCap()
        refreshSuggestions()
    }

    /**
     * Cursor oder Auswahl haben sich geändert (Service-Callback `onUpdateSelection`).
     * Hält Auto-Cap und Vorschläge zum neuen Cursor konsistent — sonst blieben sie
     * stehen, wenn der Nutzer mitten in den Text tippt — und merkt sich eine aktive
     * Auswahl für den selektionsbewussten Backspace.
     */
    fun onSelectionChanged(newSelStart: Int, newSelEnd: Int) {
        selStart = newSelStart
        selEnd = newSelEnd
        refreshSuggestions()
        recomputeAutoCap()
    }

    fun onKey(key: KeyboardKey) {
        when (key) {
            is CharKey -> onChar(key)
            is FunctionKey -> onFunction(key.action)
        }
    }

    private fun onChar(key: CharKey) = commitWithShift(key.output)

    /**
     * Eine im Long-Press-Popup gewählte Alternative committen (z. B. „sch", „é", das
     * einzelne „q"). Läuft durch dieselbe Shift-Casing-Logik wie ein normaler Tap —
     * kein Autocorrect, committet genau das Gewählte.
     */
    fun onAlternative(text: String) = commitWithShift(text)

    /** Committet [text] mit Shift-Casing: SHIFTED grossschreibt nur den ersten
     *  Buchstaben („qu"→„Qu", „sch"→„Sch"), CAPS alles. */
    private fun commitWithShift(text: String) {
        val shift = _state.value.shift
        val out = shift.applyTo(text)
        safeIc { it.commitText(out, 1) }
        if (shift == ShiftState.SHIFTED) setShift(ShiftState.OFF)
        afterTextChanged()
    }

    private fun onFunction(action: KeyAction) {
        when (action) {
            KeyAction.SHIFT -> cycleShift()
            KeyAction.BACKSPACE -> {
                // Bei aktiver Auswahl diese löschen — commitText("") ersetzt die
                // Selektion durch nichts. deleteSurroundingText würde eine Auswahl
                // NICHT anfassen, sondern stattdessen das Zeichen davor löschen.
                if (hasSelection) {
                    safeIc { it.commitText("", 1) }
                } else {
                    safeIc { it.deleteSurroundingText(1, 0) }
                }
                afterTextChanged()
            }
            KeyAction.SPACE -> { commit(" "); afterTextChanged() }
            KeyAction.PERIOD -> { commit("."); afterTextChanged() }
            KeyAction.COMMA -> { commit(","); afterTextChanged() }
            KeyAction.PASTE -> {
                // Zwischenablage-Text wörtlich einfügen — kein Shift-Casing, kein Autocorrect.
                val text = clipboardTextProvider()
                if (!text.isNullOrEmpty()) {
                    safeIc { it.commitText(text, 1) }
                    afterTextChanged()
                }
            }
            KeyAction.RETURN -> {
                // Verlangt das Feld eine Editor-Action (Suchen/Senden/Los/Weiter),
                // diese auslösen statt blind ein Enter zu schicken — sonst landet in
                // einem Such-/Sendefeld ein Zeilenumbruch statt des Absendens. Ohne
                // angeforderte Action bleibt es ein echtes Enter (mehrzeilige Felder).
                val action = field.imeAction
                if (action != null) {
                    safeIc { it.performEditorAction(action) }
                } else {
                    safeIc {
                        it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    }
                }
                afterTextChanged()
            }
            KeyAction.SYMBOLS -> {
                // Vorschläge laufen auch hier weiter (Ziffern liegen auf dieser Ebene —
                // so wird z. B. eine eigene PLZ vorgeschlagen). Die reservierte Leiste
                // bleibt auf beiden Ebenen, damit die Höhe beim Wechsel nicht springt.
                _state.value = _state.value.copy(layout = symbolsLayout)
                refreshSuggestions()
            }
            KeyAction.ALPHA -> {
                _state.value = _state.value.copy(layout = alphaLayout)
                refreshSuggestions()
            }
        }
    }

    /** Vorschlag angetippt: ersetzt NUR das aktuelle unfertige Präfix, nie fertigen Text. */
    fun onSuggestionTap(word: String) {
        val prefix = currentWord()
        safeIc { ic ->
            if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
            ic.commitText("$word ", 1)
        }
        setShift(ShiftState.OFF)
        clearSuggestions()
        recomputeAutoCap()
    }

    // --- intern ---

    private fun commit(text: String) {
        safeIc { it.commitText(text, 1) }
    }

    private fun cycleShift() {
        val next = when (_state.value.shift) {
            ShiftState.OFF -> ShiftState.SHIFTED
            // Auto-armiert → der Tap will entwaffnen (OFF); manuell armiert → weiter
            // zu Caps-Lock. So bleibt der manuelle Zyklus OFF→SHIFTED→CAPS→OFF erhalten.
            ShiftState.SHIFTED -> if (autoCapArmed) ShiftState.OFF else ShiftState.CAPS
            ShiftState.CAPS -> ShiftState.OFF
        }
        autoCapArmed = false // ein Shift-Tap ist immer manuell
        setShift(next)
    }

    private fun setShift(s: ShiftState) {
        if (_state.value.shift != s) _state.value = _state.value.copy(shift = s)
    }

    private fun afterTextChanged() {
        refreshSuggestions()
        if (_state.value.shift == ShiftState.OFF) recomputeAutoCap()
    }

    private fun clearSuggestions() {
        if (_state.value.suggestions.isNotEmpty()) {
            _state.value = _state.value.copy(suggestions = emptyList())
        }
    }

    private fun refreshSuggestions() {
        val s = suggester
        val anySource = settings.suggestionsEnabled || settings.userWordsEnabled
        if (s == null || !anySource) {
            // Funktion ganz aus → gar keine Leiste (kein verschwendeter Platz).
            _state.value = _state.value.copy(suggestions = emptyList(), suggestionBarVisible = false)
            return
        }
        // Ab hier ist die Leiste reserviert (feste Höhe), auch ohne aktuelle Vorschläge.
        val prefix = currentWord()
        val list = if (prefix.length >= 2 && atWordEnd()) {
            s.suggest(prefix.lowercase(), settings.suggestionsEnabled, settings.userWordsEnabled)
        } else {
            emptyList()
        }
        _state.value = _state.value.copy(suggestions = list, suggestionBarVisible = true)
    }

    /**
     * Steht direkt hinter dem Cursor noch ein Buchstabe, sind wir mitten im Wort —
     * dort werden keine Vorschläge angeboten: ein Antippen würde nur das Präfix vor
     * dem Cursor ersetzen (siehe [onSuggestionTap]) und so das Wortende zerstückeln.
     */
    private fun atWordEnd(): Boolean {
        val after = safeIc { it.getTextAfterCursor(1, 0)?.toString() } ?: return true
        return after.isEmpty() || !after[0].isLetterOrDigit()
    }

    private fun recomputeAutoCap() {
        if (!settings.autoCapEnabled) return
        if (_state.value.shift == ShiftState.CAPS) return
        val before = safeIc { it.getTextBeforeCursor(64, 0)?.toString() } ?: ""
        val trimmed = before.trimEnd()
        val shouldCap = trimmed.isEmpty() || trimmed.last() in SENTENCE_ENDERS
        // Merken, dass DIESER SHIFTED-Zustand automatisch kam (siehe cycleShift).
        autoCapArmed = shouldCap
        setShift(if (shouldCap) ShiftState.SHIFTED else ShiftState.OFF)
    }

    private fun currentWord(): String {
        val before = safeIc { it.getTextBeforeCursor(48, 0)?.toString() } ?: return ""
        // Buchstaben UND Ziffern: so wird auch ein numerisches Token (z. B. eine
        // eigene PLZ) als Präfix erkannt und kann vorgeschlagen werden.
        return before.takeLastWhile { it.isLetterOrDigit() }
    }

    private inline fun <T> safeIc(block: (InputConnection) -> T): T? {
        val ic = inputConnectionProvider() ?: return null
        return try {
            block(ic)
        } catch (e: Exception) {
            Log.w(TAG, "InputConnection-Aufruf fehlgeschlagen: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "NahIme"
        val SENTENCE_ENDERS = setOf('.', '!', '?')
    }
}
