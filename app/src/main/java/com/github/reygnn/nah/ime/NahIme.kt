package com.github.reygnn.nah.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
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
import com.github.reygnn.nah.settings.SettingsActivity
import com.github.reygnn.nah.settings.SettingsRepository
import com.github.reygnn.nah.ui.KeyboardScreen
import com.github.reygnn.nah.viewmodel.FieldContext
import com.github.reygnn.nah.viewmodel.KeyboardViewModel
import kotlinx.coroutines.Dispatchers
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
            phoneLayout = OptimizedLayout.phone(),
            inputConnectionProvider = { currentInputConnection },
            suggester = suggester,
            clipboardTextProvider = ::clipboardText,
        )

        var builtInWarmStarted = false
        lifecycleScope.launch {
            settingsRepository.settings.collect { settings ->
                viewModel.applySettings(settings)
                // Eingebauten Trie im Hintergrund vorbauen, sobald die Liste das erste Mal
                // gebraucht wird — nie synchron auf dem UI-Thread beim ersten Tastendruck.
                // Genau einmal anstossen (warmUpBuiltIn ist zwar idempotent, aber ein neuer
                // Coroutine-Start pro Settings-Emission wäre unnötig).
                if (settings.suggestionsEnabled && !builtInWarmStarted) {
                    builtInWarmStarted = true
                    lifecycleScope.launch(Dispatchers.Default) { suggester.warmUpBuiltIn() }
                }
            }
        }
        lifecycleScope.launch(Dispatchers.Default) {
            // Den User-Trie im Hintergrund bauen — konsistent zum eingebauten Trie und
            // nie auf dem UI-Thread. setUserWords tauscht danach nur die fertig gebaute
            // Instanz atomar (@Volatile) ein, die auf dem UI-Thread gelesen wird.
            userWordRepository.words.collect { suggester.setUserWords(it) }
        }

        clipboard?.addPrimaryClipChangedListener(primaryClipChangedListener)
    }

    override fun onCreateInputView(): View {
        // Mindestens STARTED, damit der WindowRecomposer der ComposeView aufbaut.
        // Die volle Resume/Pause-Paarung läuft über onWindowShown/onWindowHidden.
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(this).apply {
            setContent { KeyboardScreen(viewModel = viewModel, onOpenSettings = ::openSettings) }
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
        viewModel.onStartInput(
            field = info?.let {
                FieldContext.fromEditorInfo(
                    imeOptions = it.imeOptions,
                    inputType = it.inputType,
                    initialSelStart = it.initialSelStart,
                    initialSelEnd = it.initialSelEnd,
                )
            } ?: FieldContext(),
            pasteAvailable = clipboardHasText(),
            restarting = restarting,
        )
    }

    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }

    // Hält die Einfügen-Taste live aktuell, wenn der Nutzer bei offener Tastatur etwas
    // kopiert (sonst aktualisierte sich der Zustand erst beim nächsten Feldwechsel). Liest
    // nur Metadaten (kein Toast). Registriert in onCreate, abgemeldet in onDestroy.
    private val primaryClipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        viewModel.onPasteAvailabilityChanged(clipboardHasText())
    }

    /** Hat die Zwischenablage Text? Prüft nur die Metadaten (ClipDescription) —
     *  liest NICHT den Inhalt, löst also keinen „Zwischenablage gelesen"-Toast aus. */
    private fun clipboardHasText(): Boolean =
        clipboard?.primaryClipDescription?.let {
            it.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                it.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        } ?: false

    /** Der Zwischenablage-Text fürs Einfügen. Liest den Inhalt (bewusst nur hier, bei
     *  einer expliziten Nutzeraktion) und coerced auch URIs/Intents zu Text. */
    private fun clipboardText(): String? {
        val item = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
        // Schnellpfad: liegt der Text bereits als CharSequence vor (der Normalfall), ihn direkt
        // nehmen — kein ContentResolver-Zugriff. coerceToText (das für Content-URIs synchron den
        // Resolver abfragen und so den UI-Thread blockieren könnte) bleibt nur der Rückfall für
        // Nicht-Text-Clips. clipboardHasText() lässt die Taste ohnehin nur bei Text-MIME zu.
        val text = item.text ?: item.coerceToText(this)
        return text?.toString()?.takeIf { it.isNotEmpty() }
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

    /** Öffnet die App-Einstellungen (vom Hamburger in der Vorschlagsleiste) und
     *  blendet dabei die Tastatur aus. */
    private fun openSettings() {
        startActivity(
            Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        requestHideSelf(0)
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
        clipboard?.removePrimaryClipChangedListener(primaryClipChangedListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
