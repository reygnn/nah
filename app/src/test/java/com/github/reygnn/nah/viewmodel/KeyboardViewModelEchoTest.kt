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
        val vm = vm(fake) { _, _, _, _ -> listOf("hallo") } // simpler Suggester
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

    /**
     * Pinnt die zentrale Korrektheits-Invariante der Echo-Optimierung: **die Cursor-Vorhersage
     * ([KeyboardViewModel.pendingSelfEcho]) ist reine Dedup-Optimierung, der synchrone Read in
     * `afterTextChanged` trägt die Korrektheit.** Zwei Commits hintereinander, BEVOR das erste
     * `onUpdateSelection`-Echo eintrifft, halten `selStart` stale → die zweite Vorhersage ist
     * falsch. Trotzdem muss der State nach „ha" korrekt sein (synchroner Read gegen die reale IC),
     * und die verspäteten Echos dürfen ihn nicht verfälschen. Schützt gegen eine künftige
     * „Optimierung", die den synchronen Recompute per Vorhersage überspringen wollte.
     */
    @Test
    fun `zwei schnelle Commits ohne zwischengeschaltetes Echo halten den State korrekt`() {
        val fake = CountingIc()
        // Präfix + Marker (nicht exakt-gleich, sonst entfernte ihn der No-op-Filter in
        // computeSuggestions); beweist trotzdem, welches Präfix der synchrone Read gesehen hat.
        val vm = vm(fake) { prefix, _, _, _ -> listOf(prefix + "x") }
        vm.onStartInput(FieldContext())
        vm.applySettings(Settings(suggestionsEnabled = true))

        // Beide Commits, bevor irgendein Echo zurückkommt → pendingSelfEcho wird mit einer aus
        // dem stale selStart abgeleiteten, FALSCHEN Position überschrieben.
        vm.onKey(CharKey('h'))
        vm.onKey(CharKey('a'))
        // Korrekt trotz Fehlvorhersage: der synchrone Read sah die reale IC ("ha").
        assertEquals(listOf("hax"), vm.state.value.suggestions)

        // Jetzt treffen die verspäteten Echos ein. Das erste passt zufällig auf die (falsche)
        // Quittung und wird übersprungen, das zweite recomputet — der State bleibt korrekt.
        vm.onSelectionChanged(1, 1) // verspätetes Echo von 'h'
        vm.onSelectionChanged(2, 2) // Echo von 'a'
        assertEquals(listOf("hax"), vm.state.value.suggestions)
    }

    /**
     * Pinnt den Fix gegen die über einen No-Op-Callback hinweg scharf gebliebene Quittung: ein
     * redundanter `onUpdateSelection` ohne echte Bewegung muss eine offene Selbst-Echo-Quittung
     * verfallen lassen. Sonst würde ein danach kommendes echtes Cursor-Ereignis, das zufällig auf
     * der alten Vorhersage landet, fälschlich entdoppelt und sein nötiges Recompute übersprungen.
     */
    @Test
    fun `ein No-Op-Callback entschaerft die offene Selbst-Echo-Quittung`() {
        val fake = CountingIc()
        val vm = vm(fake)
        vm.onStartInput(FieldContext())
        vm.onKey(CharKey('a')) // Quittung = 1 scharf, real Cursor bei 1, VM-selStart noch 0

        // Redundanter Callback ohne Bewegung (== aktueller VM-Stand 0,0): muss die Quittung löschen.
        vm.onSelectionChanged(0, 0)

        fake.beforeReads = 0
        // Echtes Cursor-Ereignis auf Position 1 (= die alte, nun entschärfte Vorhersage). Ohne den
        // Fix griffe die stale Quittung und überspränge das Recompute (beforeReads bliebe 0).
        vm.onSelectionChanged(1, 1)

        assertEquals(1, fake.beforeReads)
    }
}
