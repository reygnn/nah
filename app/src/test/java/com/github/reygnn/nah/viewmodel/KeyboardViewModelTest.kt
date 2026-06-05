package com.github.reygnn.nah.viewmodel

import android.view.inputmethod.InputConnection
import com.github.reygnn.nah.data.suggestions.Suggester
import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.FunctionKey
import com.github.reygnn.nah.layout.KeyAction
import com.github.reygnn.nah.layout.KeyboardLayout
import com.github.reygnn.nah.layout.OptimizedLayout
import com.github.reygnn.nah.settings.Settings
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake-InputConnection mit echtem Text-Puffer für die paar genutzten Methoden. */
private class FakeIc {
    val buffer = StringBuilder()
    val ic: InputConnection = mockk(relaxed = true) {
        every { commitText(any(), any()) } answers {
            buffer.append(firstArg<CharSequence>()); true
        }
        every { deleteSurroundingText(any(), any()) } answers {
            repeat(firstArg<Int>()) { if (buffer.isNotEmpty()) buffer.deleteCharAt(buffer.length - 1) }
            true
        }
        every { getTextBeforeCursor(any(), any()) } answers {
            buffer.takeLast(firstArg<Int>())
        }
    }
}

class KeyboardViewModelTest {

    private val alpha: KeyboardLayout = OptimizedLayout.deCh()
    private val symbols: KeyboardLayout = OptimizedLayout.symbols()

    private fun vm(fake: FakeIc, suggester: Suggester? = null) = KeyboardViewModel(
        alphaLayout = alpha,
        symbolsLayout = symbols,
        inputConnectionProvider = { fake.ic },
        suggester = suggester,
    )

    private fun KeyboardViewModel.type(text: String) {
        text.forEach { onKey(CharKey(it)) }
    }

    @Test
    fun `tippen committet genau die Zeichen, kein Autocorrect`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.type("hallo")
        assertEquals("hallo", fake.buffer.toString())
    }

    @Test
    fun `backspace loescht ein Zeichen`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.type("ab")
        vm.onKey(FunctionKey(KeyAction.BACKSPACE))
        assertEquals("a", fake.buffer.toString())
    }

    @Test
    fun `shift schreibt genau einen Buchstaben gross und faellt dann zurueck`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        vm.type("ab")
        assertEquals("Ab", fake.buffer.toString())
        assertEquals(ShiftState.OFF, vm.state.value.shift)
    }

    @Test
    fun `shift dreimal tippen zyklet OFF SHIFTED CAPS`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        assertEquals(ShiftState.CAPS, vm.state.value.shift)
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        assertEquals(ShiftState.OFF, vm.state.value.shift)
    }

    @Test
    fun `caps lock haelt ueber mehrere Buchstaben`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // CAPS
        vm.type("abc")
        assertEquals("ABC", fake.buffer.toString())
        assertEquals(ShiftState.CAPS, vm.state.value.shift)
    }

    @Test
    fun `auto-cap aktiviert shift am Satzanfang`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput() // leerer Puffer → Satzanfang
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        vm.type("h")
        assertEquals("H", fake.buffer.toString())
    }

    @Test
    fun `layer-wechsel zu Symbolen und zurueck`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings()) }
        vm.onKey(FunctionKey(KeyAction.SYMBOLS))
        assertSame(symbols, vm.state.value.layout)
        vm.onKey(FunctionKey(KeyAction.ALPHA))
        assertSame(alpha, vm.state.value.layout)
    }

    @Test
    fun `vorschlaege sind standardmaessig aus`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("hallo") })
            .apply { applySettings(Settings()) } // beide Quellen aus (Default)
        vm.type("ha")
        assertTrue(vm.state.value.suggestions.isEmpty())
    }

    @Test
    fun `vorschlaege erscheinen wenn aktiviert`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("hallo", "haben") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        vm.type("ha")
        assertEquals(listOf("hallo", "haben"), vm.state.value.suggestions)
    }

    @Test
    fun `nur eigene Woerter aktiviert zeigt User-Vorschlaege ohne die Liste`() {
        val fake = FakeIc()
        // Fake unterscheidet die Quellen über die Flags, die der ViewModel durchreicht.
        val suggester = Suggester { _, builtIn, user ->
            buildList {
                if (builtIn) add("hallo")
                if (user) add("reygnn")
            }
        }
        val vm = vm(fake, suggester = suggester).apply {
            applySettings(Settings(suggestionsEnabled = false, userWordsEnabled = true, autoCapEnabled = false))
        }
        vm.type("re")
        assertEquals(listOf("reygnn"), vm.state.value.suggestions)
    }

    @Test
    fun `vorschlag-tap ersetzt nur das aktuelle Wort, nie fertigen Text`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("welt") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        vm.type("hallo")
        vm.onKey(FunctionKey(KeyAction.SPACE))
        vm.type("we")
        vm.onSuggestionTap("welt")
        // "hallo " bleibt unberührt, nur "we" → "welt " ersetzt
        assertEquals("hallo welt ", fake.buffer.toString())
    }
}
