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
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fake-InputConnection mit echtem Text-Puffer UND einer Cursor-/Auswahl-Position
 * für die paar genutzten Methoden. commitText ersetzt die aktuelle Auswahl (oder
 * fügt am Cursor ein) — so wie die echte InputConnection, womit der selektions-
 * bewusste Backspace testbar wird.
 */
private class FakeIc {
    val buffer = StringBuilder()
    private var selStart = 0
    private var selEnd = 0

    /** Markiert eine Auswahl [start, end), wie es der Service via onUpdateSelection meldet. */
    fun select(start: Int, end: Int) {
        selStart = start
        selEnd = end
    }

    val ic: InputConnection = mockk(relaxed = true) {
        every { commitText(any(), any()) } answers {
            val text = firstArg<CharSequence>().toString()
            buffer.replace(selStart, selEnd, text)
            selStart += text.length
            selEnd = selStart
            true
        }
        every { deleteSurroundingText(any(), any()) } answers {
            val from = (selStart - firstArg<Int>()).coerceAtLeast(0)
            buffer.delete(from, selStart)
            selStart = from
            selEnd = from
            true
        }
        every { getTextBeforeCursor(any(), any()) } answers {
            buffer.substring((selStart - firstArg<Int>()).coerceAtLeast(0), selStart)
        }
        every { getTextAfterCursor(any(), any()) } answers {
            buffer.substring(selEnd, (selEnd + firstArg<Int>()).coerceAtMost(buffer.length))
        }
    }
}

class KeyboardViewModelTest {

    private val alpha: KeyboardLayout = OptimizedLayout.deCh()
    private val symbols: KeyboardLayout = OptimizedLayout.symbols()

    private fun vm(
        fake: FakeIc,
        suggester: Suggester? = null,
        clipboardTextProvider: () -> String? = { null },
    ) = KeyboardViewModel(
        alphaLayout = alpha,
        symbolsLayout = symbols,
        inputConnectionProvider = { fake.ic },
        suggester = suggester,
        clipboardTextProvider = clipboardTextProvider,
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
    fun `qu-taste committet beide Zeichen`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(CharKey('q', output = "qu"))
        assertEquals("qu", fake.buffer.toString())
    }

    @Test
    fun `qu-taste mit shift schreibt nur den ersten Buchstaben gross`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(CharKey('q', output = "qu"))
        assertEquals("Qu", fake.buffer.toString())
        assertEquals(ShiftState.OFF, vm.state.value.shift) // SHIFTED verbraucht
    }

    @Test
    fun `qu-taste mit caps schreibt alles gross`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // CAPS
        vm.onKey(CharKey('q', output = "qu"))
        assertEquals("QU", fake.buffer.toString())
    }

    @Test
    fun `paste committet den Zwischenablage-Text`() {
        val fake = FakeIc()
        val vm = vm(fake, clipboardTextProvider = { "kopiert" })
            .apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.PASTE))
        assertEquals("kopiert", fake.buffer.toString())
    }

    @Test
    fun `paste bei leerer Zwischenablage tut nichts`() {
        val fake = FakeIc()
        val vm = vm(fake, clipboardTextProvider = { null })
            .apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.PASTE))
        assertEquals("", fake.buffer.toString())
    }

    @Test
    fun `paste ist woertlich, ignoriert Caps-Lock`() {
        val fake = FakeIc()
        val vm = vm(fake, clipboardTextProvider = { "hallo" })
            .apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // CAPS
        vm.onKey(FunctionKey(KeyAction.PASTE))
        assertEquals("hallo", fake.buffer.toString()) // nicht HALLO
    }

    @Test
    fun `pasteAvailable kommt aus onStartInput`() {
        val fake = FakeIc()
        val vm = vm(fake)
        vm.onStartInput(pasteAvailable = true)
        assertTrue(vm.state.value.pasteAvailable)
    }

    @Test
    fun `alternative committet genau den gewaehlten Text`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onAlternative("sch")
        assertEquals("sch", fake.buffer.toString())
    }

    @Test
    fun `alternative mit shift schreibt nur den ersten Buchstaben gross`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onAlternative("sch")
        assertEquals("Sch", fake.buffer.toString())
        assertEquals(ShiftState.OFF, vm.state.value.shift)
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
    fun `backspace loescht eine aktive Auswahl statt eines Zeichens`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.type("hallo")
        fake.select(1, 4) // "all" markiert
        vm.onSelectionChanged(1, 4) // Service meldet die Auswahl
        vm.onKey(FunctionKey(KeyAction.BACKSPACE))
        // Die Auswahl wird entfernt, nicht das Zeichen vor ihr.
        assertEquals("ho", fake.buffer.toString())
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
    fun `shift-tap auf auto-armiertem Shift schaltet direkt aus, nicht auf CAPS`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput() // leerer Puffer → Auto-Cap armiert SHIFTED
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        // Die automatische Armierung wird direkt entwaffnet — kein Umweg über CAPS.
        assertEquals(ShiftState.OFF, vm.state.value.shift)
    }

    @Test
    fun `manuelles Shift zyklet weiterhin ueber CAPS`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // manuell armiert
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // → CAPS, nicht OFF
        assertEquals(ShiftState.CAPS, vm.state.value.shift)
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
    fun `return fuehrt die Editor-Action aus, wenn das Feld eine verlangt`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onStartInput(FieldContext(imeAction = 3)) // z. B. IME_ACTION_SEARCH
        vm.onKey(FunctionKey(KeyAction.RETURN))
        verify { fake.ic.performEditorAction(3) }
        verify(exactly = 0) { fake.ic.sendKeyEvent(any()) }
    }

    @Test
    fun `return ohne angeforderte Action schickt ein echtes Enter`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onStartInput(FieldContext()) // keine Action → Enter
        vm.onKey(FunctionKey(KeyAction.RETURN))
        verify(exactly = 0) { fake.ic.performEditorAction(any()) }
        verify { fake.ic.sendKeyEvent(any()) }
    }

    @Test
    fun `lern-farben spiegeln sich im State`() {
        val fake = FakeIc()
        val vm = vm(fake)
        vm.applySettings(Settings(letterColorHintsEnabled = true))
        assertTrue(vm.state.value.colorHints)
        vm.applySettings(Settings(letterColorHintsEnabled = false))
        assertFalse(vm.state.value.colorHints)
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
    fun `leiste bleibt reserviert wenn aktiviert aber gerade keine Vorschläge`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> emptyList() })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        // Nur ein Zeichen (< Mindestpräfix) → keine Vorschläge, aber die Leiste
        // belegt weiter Platz, damit die Tastatur nicht in der Höhe springt.
        vm.type("h")
        assertTrue(vm.state.value.suggestions.isEmpty())
        assertTrue(vm.state.value.suggestionBarVisible)
    }

    @Test
    fun `keine Leiste wenn die Funktion ganz aus ist`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("hallo") })
            .apply { applySettings(Settings()) } // beide Quellen aus
        vm.type("ha")
        assertFalse(vm.state.value.suggestionBarVisible)
    }

    @Test
    fun `mitten im Wort gibt es keine Vorschläge`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("hallo") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        vm.type("hallo")
        fake.select(2, 2) // Cursor mitten ins Wort: "ha|llo"
        vm.onSelectionChanged(2, 2)
        // Hinter dem Cursor steht noch "llo" → ein Tap würde das Wortende zerstückeln.
        assertTrue(vm.state.value.suggestions.isEmpty())
    }

    @Test
    fun `ziffern-praefix loest vorschlaege aus, auch auf der Symbolebene`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { prefix, _, _ ->
            if (prefix == "80") listOf("8050") else emptyList()
        }).apply { applySettings(Settings(userWordsEnabled = true, autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SYMBOLS)) // Ziffern liegen auf der Symbolebene
        vm.type("80")
        assertEquals(listOf("8050"), vm.state.value.suggestions)
        assertTrue(vm.state.value.suggestionBarVisible)
    }

    @Test
    fun `phrase-vorschlag fuegt die ganze Phrase ein, ersetzt nur das getippte Wort`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { prefix, _, _ ->
            if ("hauptstrasse 115".startsWith(prefix)) listOf("Hauptstrasse 115") else emptyList()
        }).apply { applySettings(Settings(userWordsEnabled = true, autoCapEnabled = false)) }
        vm.type("haupt")
        assertEquals(listOf("Hauptstrasse 115"), vm.state.value.suggestions)
        vm.onSuggestionTap("Hauptstrasse 115")
        // Nur das getippte „haupt" wird ersetzt, die ganze Phrase (inkl. Leerzeichen) eingefügt.
        assertEquals("Hauptstrasse 115 ", fake.buffer.toString())
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
