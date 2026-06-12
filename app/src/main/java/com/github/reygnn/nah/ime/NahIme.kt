package com.github.reygnn.nah.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
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
import com.github.reygnn.nah.R
import com.github.reygnn.nah.data.suggestions.LearnedWordRepository
import com.github.reygnn.nah.data.suggestions.SuggestionRepository
import com.github.reygnn.nah.data.suggestions.UserWordError
import com.github.reygnn.nah.data.suggestions.UserWordRepository
import com.github.reygnn.nah.data.suggestions.UserWordValidation
import com.github.reygnn.nah.layout.OptimizedLayout
import com.github.reygnn.nah.settings.SettingsActivity
import com.github.reygnn.nah.settings.SettingsRepository
import com.github.reygnn.nah.ui.KeyboardScreen
import com.github.reygnn.nah.viewmodel.FieldContext
import com.github.reygnn.nah.viewmodel.KeyboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private lateinit var learnedWordRepository: LearnedWordRepository

    // Schützt einen asynchron aufgelösten Einfüge-Inhalt davor, nach einem Feldwechsel ins fremde Feld
    // zu committen (Fehlcommit + Privacy-Leck). Reine, JVM-getestete Entscheidungslogik (siehe PasteGuard).
    private val pasteGuard = PasteGuard()

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        settingsRepository = SettingsRepository(applicationContext)
        userWordRepository = UserWordRepository(applicationContext)
        learnedWordRepository = LearnedWordRepository(applicationContext)
        // Lazy Index — wird nur gebaut, wenn Vorschläge je aktiviert werden (Default aus).
        val suggester = SuggestionRepository()

        viewModel = KeyboardViewModel(
            alphaLayout = OptimizedLayout.deCh(),
            symbolsLayout = OptimizedLayout.symbols(),
            numberLayout = OptimizedLayout.number(),
            phoneLayout = OptimizedLayout.phone(),
            inputConnectionProvider = { currentInputConnection },
            suggester = suggester,
            onPasteRequested = ::requestPaste,
            onSettingsRequested = ::openSettings,
            onSaveWordRequested = ::saveLearnedWord,
        )

        var builtInWarmStarted = false
        lifecycleScope.launch {
            settingsRepository.settings.collect { settings ->
                viewModel.applySettings(settings)
                // Eingebauten Index im Hintergrund vorbauen, sobald die Liste das erste Mal
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
            // Den User-Index im Hintergrund bauen (nie auf dem UI-Thread) und IMMER vorhalten —
            // anders als der eingebaute Index, der erst beim Einschalten der Vorschläge lazy
            // gebaut wird (er ist gross). Der User-Index ist klein, das ständige Vorhalten kostet
            // kaum etwas. OB er einfliesst, entscheidet allein der ViewModel über
            // settings.userWordsEnabled — und zwar für Vorschläge UND die Wörtlich-Sonder-
            // behandlung (siehe onSuggestionTap), nicht hier. setUserWords tauscht danach nur die
            // fertig gebaute Instanz atomar (@Volatile) ein, die auf dem UI-Thread gelesen wird.
            userWordRepository.words.collect { suggester.setUserWords(it) }
        }
        lifecycleScope.launch(Dispatchers.Default) {
            // Die beim Tippen gelernten Wörter genauso im Hintergrund vorhalten (eigener Index, wird
            // wie ein Wörterbuch-Wort gecast statt wörtlich — siehe SuggestionRepository.isLearnedWord).
            learnedWordRepository.words.collect { suggester.setLearnedWords(it) }
        }

        clipboard?.addPrimaryClipChangedListener(primaryClipChangedListener)
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

    /**
     * Den Paste-Epoch HIER hochzählen (nicht in [onStartInputView]): [onStartInput] feuert synchron
     * mit der `currentInputConnection`-Umschaltung des Frameworks — auch wenn das Eingabe-View gerade
     * verborgen ist. [onStartInputView] ist dagegen an die View-Sichtbarkeit gekoppelt und liefe bei
     * einem Feldwechsel mit kurz verstecktem Fenster ZU SPÄT: ein dazwischen landender, off-main
     * aufgelöster Paste-Commit würde sonst gegen die schon umgeschaltete IC des FREMDEN Feldes laufen
     * (Fehlcommit + Privacy-Leck). Nur ein ECHTER Feldwechsel (restarting == false) entwertet; ein
     * reiner Restart desselben Feldes (Config-Change/Editor-restartInput) lässt einen in-flight Paste
     * korrekt überleben.
     */
    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        pasteGuard.onFieldStarted(restarting)
    }

    /** Das Feld endet → ein noch laufender Paste-Commit gehört in kein Feld mehr und wird entwertet
     *  (er würde sonst gegen eine bereits umgeschaltete/ungültige IC committen). */
    override fun onFinishInput() {
        super.onFinishInput()
        pasteGuard.onFieldFinished()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Epoch-Pflege liegt in onStartInput/onFinishInput (s. dort) — hier nur View/Settings.
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

    // Hält die Einfügen-Taste aktuell, wenn der Nutzer bei offener Tastatur etwas kopiert.
    // Best-Effort: Android liefert Clipboard-Änderungen nur der fokussierten App und schränkt
    // sie für nicht-fokussierte Apps ein — der Callback feuert also nicht in jedem Fall. Der
    // verlässliche Stand kommt ohnehin aus clipboardHasText() beim onStartInputView
    // (Feldwechsel); dieser Listener ist nur die Kür obendrauf. Liest nur Metadaten (kein
    // Toast). Registriert in onCreate, abgemeldet in onDestroy.
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

    /**
     * Einfügen angefordert (Einfügen-Taste). Den Zwischenablage-Inhalt auf einem
     * Hintergrund-Dispatcher auflösen — [clipboardText]s `coerceToText` kann für einen
     * Content-URI-Clip synchron den `ContentResolver` (fremder Provider-Prozess) abfragen
     * und würde auf dem UI-Thread bis zum ANR blockieren. Das Ergebnis dann auf dem
     * Main-Thread committen. Inhalt-Zugriff weiterhin NUR hier, bei der expliziten Geste.
     */
    private fun requestPaste() {
        val token = pasteGuard.beginPaste()
        lifecycleScope.launch(Dispatchers.Default) {
            val text = clipboardText()
            withContext(Dispatchers.Main) {
                // Nur committen, wenn seit dem Tap kein Feldwechsel stattfand — sonst schriebe ein
                // langsam aufgelöster URI-Clip in ein inzwischen fremdes Feld. Lieber den Paste
                // fallen lassen (Nutzer tippt erneut) als ihn ins falsche Feld zu setzen.
                if (pasteGuard.mayCommit(token)) viewModel.commitClipboardText(text)
            }
        }
    }

    /** Der Zwischenablage-Text fürs Einfügen. Liest den Inhalt (bewusst nur bei der
     *  expliziten Einfüge-Geste) und coerced auch URIs/Intents zu Text. **Off-main
     *  aufzurufen** (siehe [requestPaste]) — coerceToText kann blockieren. */
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
     * Speichert das in der Vorschlagsleiste angetippte Wort als **gelerntes** Wort
     * ([LearnedWordRepository]) und bestätigt per Toast. Aus `KeyboardViewModel.onSaveWordTap` über den
     * `onSaveWordRequested`-Callback aufgerufen — der ViewModel kennt weder DataStore noch Android.
     * Bewusst der Lern-Store (nicht der kuratierte [UserWordRepository]): live gespeicherte Wörter sollen
     * sich beim Vorschlagen an Shift/Caps orientieren (wie Wörterbuch-Wörter), nicht wörtlich committet
     * werden. `add` validiert erneut gegen den aktuellen Stand (Duplikat ohne Race); die nicht-Duplikat-
     * Fehler sind hier praktisch unerreichbar (der ViewModel bietet nur 2–50-Zeichen-Wörter ohne
     * Steuerzeichen an), werden aber der Vollständigkeit halber gemeldet. Das Wort fliesst über den
     * `words`-Flow zurück in den Suggester und erscheint im „Gelernte Wörter"-Abschnitt der Verwaltung.
     */
    private fun saveLearnedWord(word: String) {
        lifecycleScope.launch {
            val message = when (learnedWordRepository.add(word)) {
                null -> getString(R.string.word_saved, word)
                UserWordError.AlreadyExists -> getString(R.string.word_save_exists, word)
                UserWordError.TooShort -> getString(R.string.user_word_error_too_short, UserWordValidation.MIN_LENGTH)
                UserWordError.TooLong -> getString(R.string.user_word_error_too_long, UserWordValidation.MAX_LENGTH)
                UserWordError.InvalidCharacters -> getString(R.string.user_word_error_invalid)
            }
            Toast.makeText(this@NahIme, message, Toast.LENGTH_SHORT).show()
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
        clipboard?.removePrimaryClipChangedListener(primaryClipChangedListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
