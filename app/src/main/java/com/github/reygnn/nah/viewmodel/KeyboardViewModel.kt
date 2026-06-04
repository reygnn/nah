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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ShiftState { OFF, SHIFTED, CAPS }

data class KeyboardUiState(
    val layout: KeyboardLayout,
    val shift: ShiftState = ShiftState.OFF,
    val suggestions: List<String> = emptyList(),
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
) : ViewModel() {

    private val _state = MutableStateFlow(KeyboardUiState(layout = alphaLayout))
    val state: StateFlow<KeyboardUiState> = _state.asStateFlow()

    private var settings = Settings()
    private val onAlpha get() = _state.value.layout === alphaLayout

    fun applySettings(newSettings: Settings) {
        settings = newSettings
        if (!newSettings.suggestionsEnabled) {
            clearSuggestions()
        } else {
            refreshSuggestions()
        }
    }

    /** Neues Eingabefeld beginnt: zurück zur Buchstabenebene, Auto-Cap neu bestimmen. */
    fun onStartInput() {
        _state.value = _state.value.copy(layout = alphaLayout)
        recomputeAutoCap()
        refreshSuggestions()
    }

    fun onKey(key: KeyboardKey) {
        when (key) {
            is CharKey -> onChar(key.char)
            is FunctionKey -> onFunction(key.action)
        }
    }

    private fun onChar(char: Char) {
        val shift = _state.value.shift
        val out = if (shift != ShiftState.OFF) char.uppercaseChar() else char
        safeIc { it.commitText(out.toString(), 1) }
        if (shift == ShiftState.SHIFTED) setShift(ShiftState.OFF)
        afterTextChanged()
    }

    private fun onFunction(action: KeyAction) {
        when (action) {
            KeyAction.SHIFT -> cycleShift()
            KeyAction.BACKSPACE -> {
                safeIc { it.deleteSurroundingText(1, 0) }
                afterTextChanged()
            }
            KeyAction.SPACE -> { commit(" "); afterTextChanged() }
            KeyAction.PERIOD -> { commit("."); afterTextChanged() }
            KeyAction.COMMA -> { commit(","); afterTextChanged() }
            KeyAction.RETURN -> {
                safeIc {
                    it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
                afterTextChanged()
            }
            KeyAction.SYMBOLS ->
                _state.value = _state.value.copy(layout = symbolsLayout, suggestions = emptyList())
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
        setShift(
            when (_state.value.shift) {
                ShiftState.OFF -> ShiftState.SHIFTED
                ShiftState.SHIFTED -> ShiftState.CAPS
                ShiftState.CAPS -> ShiftState.OFF
            },
        )
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
        if (!settings.suggestionsEnabled || s == null || !onAlpha) {
            clearSuggestions()
            return
        }
        val prefix = currentWord()
        val list = if (prefix.length >= 2) s.suggest(prefix.lowercase()) else emptyList()
        _state.value = _state.value.copy(suggestions = list)
    }

    private fun recomputeAutoCap() {
        if (!settings.autoCapEnabled) return
        if (_state.value.shift == ShiftState.CAPS) return
        val before = safeIc { it.getTextBeforeCursor(64, 0)?.toString() } ?: ""
        val trimmed = before.trimEnd()
        val shouldCap = trimmed.isEmpty() || trimmed.last() in SENTENCE_ENDERS
        setShift(if (shouldCap) ShiftState.SHIFTED else ShiftState.OFF)
    }

    private fun currentWord(): String {
        val before = safeIc { it.getTextBeforeCursor(48, 0)?.toString() } ?: return ""
        return before.takeLastWhile { it.isLetter() }
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
