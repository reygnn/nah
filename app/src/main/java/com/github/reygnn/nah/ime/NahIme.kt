package com.github.reygnn.nah.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.reygnn.nah.data.suggestions.SuggestionRepository
import com.github.reygnn.nah.data.suggestions.UserWordRepository
import com.github.reygnn.nah.layout.OptimizedLayout
import com.github.reygnn.nah.settings.SettingsRepository
import com.github.reygnn.nah.ui.KeyboardScreen
import com.github.reygnn.nah.viewmodel.FieldContext
import com.github.reygnn.nah.viewmodel.KeyboardViewModel
import kotlinx.coroutines.launch

/**
 * IME-Einstieg. Dünner Glue: hostet die Compose-UI in einer [ComposeView] und
 * verbindet [android.view.inputmethod.InputConnection] ↔ [KeyboardViewModel].
 * Alle Entscheidungen leben im ViewModel.
 */
class NahIme :
    InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private lateinit var viewModel: KeyboardViewModel
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var userWordRepository: UserWordRepository

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        settingsRepository = SettingsRepository(applicationContext)
        userWordRepository = UserWordRepository(applicationContext)
        // Lazy Trie — wird nur gebaut, wenn Vorschläge je aktiviert werden (Default aus).
        val suggester = SuggestionRepository()

        viewModel = KeyboardViewModel(
            alphaLayout = OptimizedLayout.deCh(),
            symbolsLayout = OptimizedLayout.symbols(),
            inputConnectionProvider = { currentInputConnection },
            suggester = suggester,
        )

        lifecycleScope.launch {
            settingsRepository.settings.collect { viewModel.applySettings(it) }
        }
        lifecycleScope.launch {
            userWordRepository.words.collect { suggester.setUserWords(it) }
        }
    }

    override fun onCreateInputView(): View {
        // Mindestens STARTED, damit der WindowRecomposer der ComposeView aufbaut.
        // Die volle Resume/Pause-Paarung läuft über onWindowShown/onWindowHidden.
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(this).apply {
            setContent { KeyboardScreen(viewModel = viewModel) }
        }

        // Bei einer IME ist die rootView des Fensters NICHT unsere ComposeView,
        // sondern ein framework-eigener Container. Ein FrameLayout-Wrapper setzt
        // in seinem onAttachedToWindow (feuert VOR dem der Children) die
        // ViewTree*Owner-Tags auf der dann bekannten rootView — erst danach baut
        // der WindowRecomposer der ComposeView sauber auf.
        return object : FrameLayout(this) {
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                rootView.setViewTreeLifecycleOwner(this@NahIme)
                rootView.setViewTreeSavedStateRegistryOwner(this@NahIme)
            }
        }.apply {
            addView(composeView)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        viewModel.onStartInput(FieldContext(imeAction = info?.imeActionOrNull()))
    }

    /** Tastatur wird sichtbar → RESUMED (no-op, falls bereits resumed). */
    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /**
     * Tastatur ist versteckt → bis CREATED zurück, damit der Recomposer pausiert
     * statt dauerhaft „resumed" zu laufen. Gegenstück zu [onWindowShown].
     */
    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    /**
     * Nie der Vollbild-Extract-Editor (Querformat): nah ist eine fixe Tastatur, die
     * Eingabe soll im echten Zielfeld sichtbar bleiben.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * Die von der Return-Taste auszulösende Editor-Action, oder `null`, wenn das Feld
     * keine verlangt (oder explizit `IME_FLAG_NO_ENTER_ACTION` setzt) — dann bleibt
     * Return ein echtes Enter.
     */
    private fun EditorInfo.imeActionOrNull(): Int? {
        if ((imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return null
        return when (val action = imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_UNSPECIFIED -> null
            else -> action
        }
    }

    /**
     * Cursor/Auswahl im Zielfeld haben sich geändert — auch durch direktes Antippen
     * im Text, nicht nur durch unsere Tasten. Ohne diesen Durchstich blieben Auto-Cap
     * und Vorschläge auf dem Stand des letzten Tastendrucks stehen.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd,
        )
        viewModel.onSelectionChanged(newSelStart, newSelEnd)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
