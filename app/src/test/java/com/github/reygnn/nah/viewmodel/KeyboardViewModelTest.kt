package com.github.reygnn.nah.viewmodel

import android.view.inputmethod.InputConnection
import com.github.reygnn.nah.data.suggestions.Suggester
import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.FunctionKey
import com.github.reygnn.nah.layout.KeyAction
import com.github.reygnn.nah.layout.KeyboardLayout
import com.github.reygnn.nah.layout.OptimizedLayout
import com.github.reygnn.nah.settings.Settings
import io.mockk.clearMocks
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
        // Code-Point-basiert: läuft pro Schritt über ein volles Surrogat-Paar (ein Emoji
        // = zwei UTF-16-Units) hinweg, statt es zu zerlegen — wie die echte InputConnection.
        every { deleteSurroundingTextInCodePoints(any(), any()) } answers {
            var from = selStart
            repeat(firstArg<Int>()) {
                if (from <= 0) return@repeat
                from -= if (from >= 2 && Character.isSurrogatePair(buffer[from - 2], buffer[from - 1])) 2 else 1
            }
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
    private val number: KeyboardLayout = OptimizedLayout.number()
    private val phone: KeyboardLayout = OptimizedLayout.phone()

    private fun vm(
        fake: FakeIc,
        suggester: Suggester? = null,
        clipboardText: () -> String? = { null },
    ): KeyboardViewModel {
        // Im Service löst der Paste-Pfad den Inhalt asynchron off-main auf und reicht ihn
        // über commitClipboardText zurück; im Test bilden wir genau diesen Rückweg synchron
        // nach (clipboardText() liefern → committen).
        lateinit var vm: KeyboardViewModel
        vm = KeyboardViewModel(
            alphaLayout = alpha,
            symbolsLayout = symbols,
            numberLayout = number,
            phoneLayout = phone,
            inputConnectionProvider = { fake.ic },
            suggester = suggester,
            onPasteRequested = { vm.commitClipboardText(clipboardText()) },
        )
        return vm
    }

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
        val vm = vm(fake, clipboardText = { "kopiert" })
            .apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.PASTE))
        assertEquals("kopiert", fake.buffer.toString())
    }

    @Test
    fun `paste bei leerer Zwischenablage tut nichts`() {
        val fake = FakeIc()
        val vm = vm(fake, clipboardText = { null })
            .apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.PASTE))
        assertEquals("", fake.buffer.toString())
    }

    @Test
    fun `paste ist woertlich, ignoriert Caps-Lock`() {
        val fake = FakeIc()
        val vm = vm(fake, clipboardText = { "hallo" })
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
    fun `clipboard-aenderung schaltet die Einfuege-Taste live`() {
        val fake = FakeIc()
        val vm = vm(fake)
        vm.onStartInput(pasteAvailable = false)
        assertFalse(vm.state.value.pasteAvailable)
        vm.onPasteAvailabilityChanged(true) // Nutzer kopiert bei offener Tastatur
        assertTrue(vm.state.value.pasteAvailable)
        vm.onPasteAvailabilityChanged(false)
        assertFalse(vm.state.value.pasteAvailable)
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
    fun `alternative unter Caps-Lock schreibt die ganze Alternative gross`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // CAPS
        vm.onAlternative("sch")
        // Eine Mehrzeichen-Alternative wird unter Caps komplett grossgeschrieben — und Caps
        // bleibt stehen (nur ein SHIFTED würde verbraucht).
        assertEquals("SCH", fake.buffer.toString())
        assertEquals(ShiftState.CAPS, vm.state.value.shift)
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
    fun `auto-cap aktiviert shift nach einem Satzzeichen`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput()       // leerer Puffer → erstes Zeichen gross (Satzanfang)
        vm.type("hallo")        // → "Hallo", danach mitten im Wort: Shift fällt auf OFF
        assertEquals(ShiftState.OFF, vm.state.value.shift)
        vm.onKey(FunctionKey(KeyAction.PERIOD))
        // Nach dem Punkt armiert Auto-Cap den nächsten Buchstaben gross.
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        vm.type("d")
        assertEquals("Hallo.D", fake.buffer.toString())
    }

    @Test
    fun `auto-cap aktiviert shift am Anfang einer neuen Zeile`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        // Cursor steht am Anfang einer neuen (leeren) Zeile — wie nach einem echten Enter in
        // einem mehrzeiligen Feld. Der Zeilenumbruch wird wie ein Satzanfang behandelt.
        fake.buffer.append("hallo\n")
        fake.select(6, 6)
        vm.onSelectionChanged(6, 6)
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        vm.type("w")
        assertEquals("hallo\nW", fake.buffer.toString())
    }

    @Test
    fun `auto-cap kapitalisiert nicht, wenn die InputConnection fehlt`() {
        // Ohne IC darf recomputeAutoCap das fehlende Ergebnis nicht als leeren Satzanfang
        // werten und faelschlich SHIFTED armieren (Punkt 4 der Code-Analyse).
        val vm = KeyboardViewModel(
            alphaLayout = alpha,
            symbolsLayout = symbols,
            numberLayout = number,
            phoneLayout = phone,
            inputConnectionProvider = { null },
        ).apply { applySettings(Settings(autoCapEnabled = true)) }
        assertEquals(ShiftState.OFF, vm.state.value.shift)
        vm.onSelectionChanged(5, 5) // mitten im Text, IC aber nicht verfuegbar
        assertEquals(ShiftState.OFF, vm.state.value.shift)
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
    fun `long-press shortcut NUMPAD schaltet aufs Ziffern-Pad`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings()) }
        // Aus dem Alpha-Layer (wo man nach einem ABC-Tap in einem Zahlenfeld landet) zurück
        // aufs Grosstasten-Pad — der Long-Press-Shortcut der ?123-Taste löst die Einbahnstrasse.
        vm.onKey(FunctionKey(KeyAction.NUMPAD))
        assertSame(number, vm.state.value.layout)
    }

    @Test
    fun `long-press shortcut DIALPAD schaltet aufs Waehlfeld`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings()) }
        vm.onKey(FunctionKey(KeyAction.DIALPAD))
        assertSame(phone, vm.state.value.layout)
    }

    @Test
    fun `vom per Long-Press erreichten Ziffern-Pad fuehrt ABC zurueck ins Alphabet`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings()) }
        vm.onKey(FunctionKey(KeyAction.NUMPAD))
        assertSame(number, vm.state.value.layout)
        // Kein neuer Dead-End: das Pad hat seinerseits ABC zurück ins Alphabet.
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
    fun `bei aktiver Auswahl gibt es keine Vorschläge`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("hallo") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        vm.type("ha")
        assertEquals(listOf("hallo"), vm.state.value.suggestions) // Präfix am Wortende → Vorschlag
        fake.select(0, 2) // „ha" markiert
        vm.onSelectionChanged(0, 2)
        // Ein Tap würde sonst das Präfix löschen UND die Selektion überschreiben (fertiger
        // Text). Also keine Vorschläge bei aktiver Auswahl — Leiste bleibt nur reserviert.
        assertTrue(vm.state.value.suggestions.isEmpty())
        assertTrue(vm.state.value.suggestionBarVisible)
    }

    @Test
    fun `vorschlag-tap bei noch nicht gemeldeter Live-Auswahl laesst fertigen Text unangetastet`() {
        // Doppel-Edge (Fugen-Review): der VM glaubt an einen kollabierten Cursor, im Editor liegt
        // aber bereits eine Auswahl ueber fertigem Text — das onUpdateSelection-Echo ist noch
        // unterwegs. Ohne Schutz wuerde der commitText in onSuggestionTap die Auswahl ersetzen und
        // fertigen Text zerstoeren (Bruch des obersten Gesetzes).
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("hallo") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        fake.buffer.append("WICHTIG ha") // „WICHTIG " ist fertig, „ha" das unfertige Praefix
        fake.select(10, 10)
        vm.onSelectionChanged(10, 10)
        assertEquals(listOf("hallo"), vm.state.value.suggestions) // Vorschlag steht am Wortende
        // Nutzer zieht eine Auswahl ueber den fertigen Text [0,10) — Echo aber noch nicht beim VM.
        fake.select(0, 10)
        // Vor dem Echo tippt der Nutzer den noch sichtbaren Vorschlag an.
        vm.onSuggestionTap("hallo")
        // Oberstes Gesetz: „WICHTIG " bleibt unangetastet; der Tap bricht ab (leeres Praefix erkannt).
        assertEquals("WICHTIG ha", fake.buffer.toString())
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
    fun `vorschlag-tap am Satzanfang uebernimmt die Grossschreibung des Praefix`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("der") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = true)) }
        vm.onStartInput()      // leeres Feld → Auto-Cap armiert SHIFTED
        vm.type("de")          // „De" (erstes Zeichen gross)
        vm.onSuggestionTap("der")
        // Klein vorgeschlagenes „der" wird auf das gross begonnene Präfix gecast → „Der".
        assertEquals("Der ", fake.buffer.toString())
    }

    /** Suggester, der seine Treffer als eigene Wörter meldet (wörtlich zu committen). */
    private fun userWordSuggester(vararg words: String) = object : Suggester {
        override fun suggest(prefix: String, includeBuiltIn: Boolean, includeUser: Boolean) = words.toList()
        override fun isUserWord(word: String) = words.contains(word)
    }

    @Test
    fun `eigenes Wort wird unter Caps-Lock NICHT grossgeschrieben`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = userWordSuggester("max@firma.ch"))
            .apply { applySettings(Settings(userWordsEnabled = true, autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // CAPS
        vm.type("max")          // unter Caps → „MAX"
        vm.onSuggestionTap("max@firma.ch")
        // Eigenes Wort bleibt wörtlich — keine Anpassung an Caps-Lock.
        assertEquals("max@firma.ch ", fake.buffer.toString())
    }

    @Test
    fun `bei abgeschalteten eigenen Woertern wird ein Treffer wie ein Woerterbuch-Wort gecast`() {
        val fake = FakeIc()
        // Der Suggester meldet „zeit" als eigenes Wort — aber die Funktion ist AUS. (Der
        // reale Grund: der User-Trie wird immer vorgehalten, isUserWord triggert also weiter.)
        val vm = vm(fake, suggester = userWordSuggester("zeit"))
            .apply { applySettings(Settings(userWordsEnabled = false, autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // CAPS
        vm.type("ze")                          // unter Caps → „ZE"
        vm.onSuggestionTap("zeit")
        // userWordsEnabled = false → KEINE Wörtlich-Sonderbehandlung; der Treffer folgt dem
        // Caps-Lock-Präfix wie ein gewöhnlicher Wörterbuch-Vorschlag (sonst käme „zeit").
        assertEquals("ZEIT ", fake.buffer.toString())
    }

    @Test
    fun `eigenes Wort behaelt am Satzanfang seine Schreibweise`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = userWordSuggester("max@firma.ch"))
            .apply { applySettings(Settings(userWordsEnabled = true, autoCapEnabled = true)) }
        vm.onStartInput()       // leeres Feld → Auto-Cap armiert SHIFTED
        vm.type("ma")           // → „Ma"
        vm.onSuggestionTap("max@firma.ch")
        // Trotz gross begonnenem Präfix wird die E-Mail nicht kapitalisiert.
        assertEquals("max@firma.ch ", fake.buffer.toString())
    }

    @Test
    fun `vorschlag-tap unter Caps-Lock schreibt den ganzen Vorschlag gross`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("der") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // CAPS
        vm.type("der")         // „DER"
        vm.onSuggestionTap("der")
        assertEquals("DER ", fake.buffer.toString())
    }

    @Test
    fun `vorschlag-tap mit nur einem Caps-Buchstaben als Praefix schreibt nur den ersten gross`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("der") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // CAPS
        vm.type("d")           // ein einzelner Caps-Buchstabe → „D"
        vm.onSuggestionTap("der")
        // Dokumentierter Tradeoff: casedLikePrefix erkennt Caps-Lock erst ab zwei Buchstaben
        // (sonst nicht von einem Satzanfang-Gross unterscheidbar). Ein einzelnes „D" wird daher
        // wie ein Satzanfang behandelt → „Der", nicht „DER". Test friert diese Entscheidung ein.
        assertEquals("Der ", fake.buffer.toString())
    }

    @Test
    fun `vorschlag-tap behaelt die Eigen-Schreibweise eines klein getippten Nomens`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("Zeit") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        vm.type("ze")          // klein getippt
        vm.onSuggestionTap("Zeit")
        // Klein getipptes Präfix → das Nomen behält sein eigenes Gross-Z (kein Klein-Cast).
        assertEquals("Zeit ", fake.buffer.toString())
    }

    @Test
    fun `backspace nach einem auswahl-ersetzenden Tipp loescht das neue Zeichen, nicht davor`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        fake.buffer.append("hallo")
        fake.select(1, 4)          // „all" markiert
        vm.onSelectionChanged(1, 4)
        vm.type("x")               // ersetzt die Auswahl → „hxo" (Echo kommt noch nicht)
        // Ohne lokalen Auswahl-Kollaps liefe Backspace fälschlich in den Auswahl-Pfad und
        // liesse das „x" stehen. Mit Kollaps wird das gerade getippte „x" gelöscht.
        vm.onKey(FunctionKey(KeyAction.BACKSPACE))
        assertEquals("ho", fake.buffer.toString())
    }

    @Test
    fun `eine Dezimalzahl nach Punkt armiert keine Auto-Grossschreibung`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput()                       // leeres Textfeld
        vm.type("3")
        vm.onKey(FunctionKey(KeyAction.PERIOD)) // „3." — Punkt darf hier kein Satzende sein …
        vm.type("14")                           // … weil danach Ziffern folgen
        assertEquals(ShiftState.OFF, vm.state.value.shift)
        vm.type("x")
        assertEquals("3.14x", fake.buffer.toString()) // nicht „3.14X"
    }

    @Test
    fun `Auto-Cap ueberspringt eine oeffnende Klammer nach dem Satzende`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput()
        vm.type("hallo")
        vm.onKey(FunctionKey(KeyAction.PERIOD))
        vm.onKey(FunctionKey(KeyAction.SPACE))
        vm.type("(")
        // Die öffnende Klammer ist „transparent" → der vorherige Punkt zählt weiter als
        // Satzende, die Armierung bleibt für den nächsten Buchstaben bestehen.
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
    }

    @Test
    fun `ein manuelles SHIFTED ueberlebt einen Selektions-Callback`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        fake.buffer.append("hallo welt")
        fake.select(6, 6)
        vm.onSelectionChanged(6, 6)             // Cursor mitten im Text → Auto-Cap rechnet
        vm.onKey(FunctionKey(KeyAction.SHIFT))  // Nutzer armiert manuell ein Nomen
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        vm.onSelectionChanged(7, 7)             // ein weiterer (spuriöser) Cursor-Callback
        // Das manuell gesetzte SHIFTED darf nicht von Auto-Cap entwaffnet werden.
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
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

    @Test
    fun `ein Field-Restart erhaelt den Shift-Zustand (z B Caps-Lock)`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onStartInput()
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // CAPS
        assertEquals(ShiftState.CAPS, vm.state.value.shift)
        // Reiner Restart desselben Feldes (Config-Change/View-Neuaufbau): Shift bleibt.
        vm.onStartInput(restarting = true)
        assertEquals(ShiftState.CAPS, vm.state.value.shift)
    }

    @Test
    fun `ein echter Feldwechsel setzt das Caps-Lock zurueck`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // CAPS
        vm.onStartInput() // neues Feld (restarting = false) → definierter Start
        assertEquals(ShiftState.OFF, vm.state.value.shift)
    }

    @Test
    fun `ein numerisches Feld startet auf dem Ziffern-Pad`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings()) }
        vm.onStartInput(FieldContext(numeric = true))
        assertSame(number, vm.state.value.layout)
    }

    @Test
    fun `ein Textfeld startet weiterhin auf der Buchstaben-Ebene`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings()) }
        vm.onStartInput(FieldContext(numeric = false))
        assertSame(alpha, vm.state.value.layout)
    }

    @Test
    fun `ein Telefonfeld startet auf dem eigenen Waehlfeld`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings()) }
        // phone impliziert numeric — das Wählfeld gewinnt vor der Symbolebene.
        vm.onStartInput(FieldContext(numeric = true, phone = true))
        assertSame(phone, vm.state.value.layout)
    }

    @Test
    fun `ein reines Zahlenfeld bekommt das Ziffern-Pad, nicht das Waehlfeld`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings()) }
        vm.onStartInput(FieldContext(numeric = true, phone = false))
        assertSame(number, vm.state.value.layout)
    }

    @Test
    fun `ein numerisches Feld armiert keine Auto-Grossschreibung`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        // Leerer Puffer wäre sonst ein „Satzanfang" → SHIFTED; im Zahlenfeld nicht.
        vm.onStartInput(FieldContext(numeric = true))
        assertEquals(ShiftState.OFF, vm.state.value.shift)
    }

    @Test
    fun `ein Passwortfeld unterdrueckt Vorschlaege trotz aktivierter Funktion`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("hallo") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        vm.onStartInput(FieldContext(isPassword = true))
        vm.type("ha")
        assertTrue(vm.state.value.suggestions.isEmpty())
        assertFalse(vm.state.value.suggestionBarVisible)
    }

    @Test
    fun `ein Feld mit NO_SUGGESTIONS-Flag unterdrueckt die Vorschlaege`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("hallo") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        // Kein Passwortfeld, aber das Ziel-Feld bittet ausdrücklich um keine Vorschläge (OTP/Kreditkarte).
        vm.onStartInput(FieldContext(noSuggestions = true))
        vm.type("ha")
        assertTrue(vm.state.value.suggestions.isEmpty())
        assertFalse(vm.state.value.suggestionBarVisible)
    }

    @Test
    fun `null hinter dem Cursor (flakige IC) gilt nicht als Wortende, kein Vorschlag`() {
        // Eigene IC: getTextBeforeCursor liefert ein Präfix, getTextAfterCursor aber null
        // (unbekannt — die API darf das). null darf NICHT als Wortende gewertet werden, sonst
        // böte ein Tap einen Vorschlag an, der fertigen Text mitten im Wort zerstückeln könnte.
        val ic = mockk<InputConnection>(relaxed = true)
        every { ic.getTextBeforeCursor(any(), any()) } returns "ha"
        every { ic.getTextAfterCursor(any(), any()) } returns null
        val vm = KeyboardViewModel(
            alphaLayout = alpha,
            symbolsLayout = symbols,
            numberLayout = number,
            phoneLayout = phone,
            inputConnectionProvider = { ic },
            suggester = Suggester { _, _, _ -> listOf("hallo") },
        ).apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        vm.onSelectionChanged(2, 2) // löst refreshForCursor aus: before="ha", after=null
        assertTrue(vm.state.value.suggestions.isEmpty())
        assertTrue(vm.state.value.suggestionBarVisible) // Leiste bleibt reserviert, nur leer
    }

    @Test
    fun `ein Passwortfeld armiert keine Auto-Grossschreibung`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        // Leerer Puffer wäre sonst ein „Satzanfang" → SHIFTED; im Passwortfeld nicht.
        vm.onStartInput(FieldContext(isPassword = true))
        assertEquals(ShiftState.OFF, vm.state.value.shift)
    }

    @Test
    fun `eine Ziffer verbraucht eine armierte Grossschreibung nicht`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // SHIFTED armiert
        vm.onKey(CharKey('5')) // Ziffer (z. B. auf der ?123-Ebene)
        assertEquals("5", fake.buffer.toString())
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift) // bleibt armiert
        vm.onKey(CharKey('h')) // erst der Buchstabe verbraucht die Armierung
        assertEquals("5H", fake.buffer.toString())
        assertEquals(ShiftState.OFF, vm.state.value.shift)
    }

    @Test
    fun `ein manuell armiertes SHIFTED ueberlebt eine Ziffer auch bei aktivem Auto-Cap`() {
        val fake = FakeIc()
        // Wie oben, aber mit eingeschaltetem Auto-Cap: der Guard (nicht das fehlende Auto-Cap)
        // erhält das MANUELL gesetzte SHIFTED über eine Ziffer hinweg — computeAutoCapShift
        // liefert für ein manuelles SHIFTED (autoCapArmed == false) bewusst null.
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        fake.buffer.append("hallo welt ")
        fake.select(11, 11)
        vm.onSelectionChanged(11, 11)          // mitten im Text → kein Satzanfang
        vm.onKey(FunctionKey(KeyAction.SHIFT))  // Nutzer armiert manuell
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        vm.onKey(CharKey('5'))                  // Ziffer dazwischen
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift) // manuelles SHIFTED bleibt
        vm.type("k")
        assertEquals("hallo welt 5K", fake.buffer.toString())
    }

    @Test
    fun `eine Ziffer am Satzanfang entwaffnet ein AUTO-armiertes SHIFTED`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput()                       // leerer Puffer → Auto-Cap armiert SHIFTED
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        vm.onKey(CharKey('5'))                  // Ziffer belegt den Satzanfang-Slot
        // Der Guard verbraucht das auto-armierte SHIFTED nicht sofort (Ziffer wird nicht
        // gecast), aber der folgende Kontext-Refresh entwaffnet korrekt: nach „5" ist kein
        // Satzanfang mehr. Anders als ein MANUELLES SHIFTED (Test oben) überlebt die
        // Auto-Armierung die Ziffer NICHT — der nächste Buchstabe bleibt klein.
        assertEquals(ShiftState.OFF, vm.state.value.shift)
        vm.type("x")
        assertEquals("5x", fake.buffer.toString()) // nicht „5X"
    }

    @Test
    fun `ein unveraenderter Selektions-Callback rechnet nichts neu`() {
        val fake = FakeIc()
        var calls = 0
        val vm = vm(fake, suggester = Suggester { _, _, _ -> calls++; listOf("hallo") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = false)) }
        fake.buffer.append("ha")
        fake.select(2, 2)
        vm.onSelectionChanged(2, 2)
        assertEquals(1, calls)
        vm.onSelectionChanged(2, 2) // identische Position → Guard greift, kein erneutes Rechnen
        assertEquals(1, calls)
        vm.onSelectionChanged(1, 1) // echte Bewegung → wieder rechnen
        assertEquals(2, calls)
    }

    @Test
    fun `ein Tastendruck liest den Kontext vor dem Cursor nur einmal`() {
        val fake = FakeIc()
        val vm = vm(fake, suggester = Suggester { _, _, _ -> listOf("hallo") })
            .apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = true)) }
        clearMocks(fake.ic, answers = false) // Zähler zurücksetzen, gestubbte Antworten behalten
        vm.onKey(CharKey('h'))
        // Vorschläge UND Auto-Cap teilen sich denselben getTextBeforeCursor-Read (vorher zwei).
        verify(exactly = 1) { fake.ic.getTextBeforeCursor(any(), any()) }
    }

    @Test
    fun `backspace loescht ein eingefuegtes Emoji als Ganzes, nicht eine halbe Surrogat`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        // Ein astrales Zeichen (😀 = U+1F600 = zwei UTF-16-Units), wie es per Einfügen
        // ins Feld gelangt. Ein code-unit-basierter Backspace liesse eine kaputte Hälfte stehen.
        fake.buffer.append("a😀")
        fake.select(3, 3)
        vm.onSelectionChanged(3, 3)
        vm.onKey(FunctionKey(KeyAction.BACKSPACE))
        // Das ganze Emoji ist weg, nur das „a" bleibt — keine verwaiste Surrogat-Hälfte.
        assertEquals("a", fake.buffer.toString())
    }

    @Test
    fun `Auto-Cap ueberspringt eine schliessende Klammer nach dem Satzende`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput()
        vm.type("hallo")
        vm.onKey(FunctionKey(KeyAction.PERIOD))
        vm.type(")")            // „hallo.)" — die schliessende Klammer steht NACH dem Punkt
        vm.onKey(FunctionKey(KeyAction.SPACE))
        // Die schliessende Klammer ist „transparent" → der Punkt zählt weiter als Satzende.
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
    }

    @Test
    fun `Auto-Cap ueberspringt ein schliessendes Guillemet nach dem Satzende`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput()
        vm.type("hallo")
        vm.onKey(FunctionKey(KeyAction.PERIOD))
        vm.type("»")            // de-CH-Schlusszeichen nach dem Satzende
        vm.onKey(FunctionKey(KeyAction.SPACE))
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
    }

    @Test
    fun `die Anfangs-Auswahl aus dem Feld macht Backspace sofort selektionsbewusst`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        fake.buffer.append("hallo")
        fake.select(1, 4) // "all" markiert, BEVOR ein onSelectionChanged kam
        // Das Feld meldet die Auswahl direkt beim Start.
        vm.onStartInput(FieldContext(initialSelStart = 1, initialSelEnd = 4))
        vm.onKey(FunctionKey(KeyAction.BACKSPACE))
        // Ohne vorheriges onSelectionChanged wird die Auswahl entfernt, nicht ein Zeichen.
        assertEquals("ho", fake.buffer.toString())
    }
}
