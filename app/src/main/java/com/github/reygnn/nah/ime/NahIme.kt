package com.github.reygnn.nah.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.reygnn.nah.data.suggestions.SuggestionRepository
import com.github.reygnn.nah.data.suggestions.UserWordRepository
import com.github.reygnn.nah.layout.OptimizedLayout
import com.github.reygnn.nah.settings.SettingsRepository
import com.github.reygnn.nah.ui.KeyboardScreen
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
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()

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
                rootView.setViewTreeViewModelStoreOwner(this@NahIme)
                rootView.setViewTreeSavedStateRegistryOwner(this@NahIme)
            }
        }.apply {
            addView(composeView)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        viewModel.onStartInput()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        super.onDestroy()
    }
}
