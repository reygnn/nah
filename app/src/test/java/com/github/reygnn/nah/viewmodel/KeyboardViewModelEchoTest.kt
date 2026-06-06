package com.github.reygnn.nah.viewmodel

import android.view.inputmethod.InputConnection
import com.github.reygnn.nah.data.suggestions.Suggester
import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.OptimizedLayout
import com.github.reygnn.nah.settings.Settings
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pinnt die Selbst-Echo-Entdopplung (siehe [KeyboardViewModel.pendingSelfEcho]): ein eigener Edit
 * mit vorhersagbarer Länge wird synchron in `afterTextChanged` einmal verarbeitet; das danach vom
 * Editor kommende `onUpdateSelection`-Echo trägt nichts Neues und darf KEINEN zweiten Kontext-Read
 * auslösen. Eine externe Cursor-Bewegung (Position ≠ Vorhersage) muss dagegen weiter recomputen.
 *
 * Zählt die `getTextBeforeCursor`/`getTextAfterCursor`-Aufrufe an der InputConnection — das ist die
 * IPC-Last pro Tastendruck, die diese Optimierung halbiert.
 */
class KeyboardViewModelEchoTest {

    /** Fake-IC mit echtem Puffer + Cursor und Read-Zähler. commitText verschiebt den Cursor wie
     *  die echte IC, sodass das Test-Echo (onSelectionChanged) die reale neue Position meldet. */
    private class CountingIc {
        val buffer = StringBuilder()
        var selectionStart = 0
            private set
        var selectionEnd = 0
            private set
        var beforeReads = 0
        var afterReads = 0

        val ic: InputConnection = mockk(relaxed = true) {
            every { commitText(any(), any()) } answers {
                val text = firstArg<CharSequence>().toString()
                buffer.replace(selectionStart, selectionEnd, text)
                selectionStart += text.length
                selectionEnd = selectionStart
                true
            }
            every { getTextBeforeCursor(any(), any()) } answers {
                beforeReads++
                buffer.substring((selectionStart - firstArg<Int>()).coerceAtLeast(0), selectionStart)
            }
            every { getTextAfterCursor(any(), any()) } answers {
                afterReads++
                buffer.substring(selectionEnd, (selectionEnd + firstArg<Int>()).coerceAtMost(buffer.length))
            }
        }
    }

    private fun vm(fake: CountingIc, suggester: Suggester? = null) = KeyboardViewModel(
        alphaLayout = OptimizedLayout.deCh(),
        symbolsLayout = OptimizedLayout.symbols(),
        numberLayout = OptimizedLayout.number(),
        phoneLayout = OptimizedLayout.phone(),
        inputConnectionProvider = { fake.ic },
        suggester = suggester,
    )

    /** Simuliert das vom Commit ausgelöste onUpdateSelection-Echo mit der realen Cursorposition. */
    private fun KeyboardViewModel.echo(fake: CountingIc) =
        onSelectionChanged(fake.selectionStart, fake.selectionEnd)

    @Test
    fun `das eigene Echo eines Tippens loest keinen zweiten Kontext-Read aus`() {
        val fake = CountingIc()
        val vm = vm(fake)
        vm.onStartInput(FieldContext()) // Default: Auto-Cap an, Vorschläge aus

        fake.beforeReads = 0
        vm.onKey(CharKey('h')) // 1× synchroner Read in afterTextChanged
        vm.echo(fake) // Echo auf der vorhergesagten Position → KEIN weiterer Read

        assertEquals(1, fake.beforeReads) // vor der Optimierung: 2
    }

    @Test
    fun `eine externe Cursor-Bewegung rechnet trotz scharfer Quittung neu`() {
        val fake = CountingIc()
        val vm = vm(fake)
        vm.onStartInput(FieldContext())
        vm.onKey(CharKey('h')); vm.echo(fake) // Cursor jetzt bei 1, Quittung verbraucht
        vm.onKey(CharKey('a')) // Quittung = 2 scharf, noch KEIN Echo

        fake.beforeReads = 0
        // Externer Sprung an eine dritte Stelle (Finger-Tap im Text): ≠ aktueller Stand (1) und
        // ≠ Vorhersage (2) → die scharfe Quittung greift nicht, es MUSS recomputet werden.
        vm.onSelectionChanged(0, 0)

        assertEquals(1, fake.beforeReads)
    }

    @Test
    fun `mit Vorschlaegen halbiert die Entdopplung die Reads pro Wort`() {
        val fake = CountingIc()
        val vm = vm(fake) { _, _, _ -> listOf("hallo") } // simpler Suggester
        vm.onStartInput(FieldContext())
        vm.applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = true))

        fake.beforeReads = 0
        fake.afterReads = 0
        // 'h': prefix len 1 → kein atWordEnd → 1 before-Read; Echo(1,1) passt → übersprungen.
        vm.onKey(CharKey('h')); vm.echo(fake)
        // 'a': prefix "ha" len 2 → atWordEnd liest 1× hinter den Cursor → 1 before + 1 after;
        //      Echo(2,2) passt → beide gespart.
        vm.onKey(CharKey('a')); vm.echo(fake)

        // Summe: before 1+1 = 2, after 0+1 = 1 → 3 Reads. Ohne Entdopplung wären es 6 (jeder
        // Wert verdoppelt durch das jeweilige Echo).
        assertEquals(2, fake.beforeReads)
        assertEquals(1, fake.afterReads)
    }
}
