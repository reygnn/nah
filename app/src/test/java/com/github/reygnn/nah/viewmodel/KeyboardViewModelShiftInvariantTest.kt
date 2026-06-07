package com.github.reygnn.nah.viewmodel

import android.view.inputmethod.InputConnection
import com.github.reygnn.nah.data.suggestions.Suggester
import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.FunctionKey
import com.github.reygnn.nah.layout.KeyAction
import com.github.reygnn.nah.layout.OptimizedLayout
import com.github.reygnn.nah.settings.Settings
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Pinnt die **strukturellen Invarianten der Shift-/Auto-Cap-Zustandsmaschine** — die Eigenschaften,
 * die [KeyboardViewModel] in Kommentaren als „Invariante" beschreibt, bisher aber nur kommentarisch
 * festhielt (Befund 1 des Code-Reviews). Der bestehende Fuzzer in `KeyboardViewModelTest` prüft die
 * **Text**-Invarianten (kein Autocorrect), tippt dabei aber nie die Shift-Taste und sieht die internen
 * Merker nicht — genau diese Lücke füllt diese Datei.
 *
 * Geprüft werden zwei Struktur-Invarianten, beide aus dem Zusammenspiel mehrerer privater Merker
 * (`autoCapArmed`, `userDisarmedAutoCap`, der öffentliche `shift`):
 *  - **INV-S1**: `autoCapArmed ⟹ shift == SHIFTED`. Eine Auto-Cap-Armierung steht IMMER auf SHIFTED;
 *    auf diese Invariante stützen sich `applySettings` (das beim Ausschalten exakt diesen Übergang
 *    sofort entwaffnet) und `cycleShift` (das ein auto-armiertes SHIFTED direkt nach OFF schaltet).
 *  - **INV-S2**: `userDisarmedAutoCap ⟹ !autoCapArmed`. Ein bewusst entwaffneter Satzanfang ist nie
 *    zugleich armiert — solange der Merker steht, liefert `computeAutoCapShift` kein SHIFTED, also
 *    wird `autoCapArmed` nicht wieder gesetzt.
 *
 * Die Merker sind privat; ihr Lesen per Reflection ist gewollt **White-Box-Invariantentest**: bricht
 * ein künftiges Refactoring eine Invariante (oder benennt einen Merker um), schlägt der Test hörbar
 * fehl statt sie still verrotten zu lassen. Beobachtbar wären die Merker nur destruktiv (ein Shift-Tap
 * verrät die Armierung, mutiert sie aber) — für eine Prüfung nach JEDEM Fuzzer-Schritt taugt nur das
 * nicht-mutierende Lesen.
 */
class KeyboardViewModelShiftInvariantTest {

    /** Fake-InputConnection mit echtem Puffer + Cursor/Auswahl (wie in `KeyboardViewModelTest`),
     *  damit der Fuzzer den realen Post-Edit-Kontext lesbar hat und das Framework-Echo nachstellen kann. */
    private class FakeIc {
        val buffer = StringBuilder()
        var selectionStart = 0
            private set
        var selectionEnd = 0
            private set

        fun select(start: Int, end: Int) {
            selectionStart = start
            selectionEnd = end
        }

        val ic: InputConnection = mockk(relaxed = true) {
            every { commitText(any(), any()) } answers {
                val text = firstArg<CharSequence>().toString()
                buffer.replace(selectionStart, selectionEnd, text)
                selectionStart += text.length
                selectionEnd = selectionStart
                true
            }
            every { deleteSurroundingText(any(), any()) } answers {
                val from = (selectionStart - firstArg<Int>()).coerceAtLeast(0)
                buffer.delete(from, selectionStart)
                selectionStart = from
                selectionEnd = from
                true
            }
            every { deleteSurroundingTextInCodePoints(any(), any()) } answers {
                var from = selectionStart
                repeat(firstArg<Int>()) {
                    if (from <= 0) return@repeat
                    from -= if (from >= 2 && Character.isSurrogatePair(buffer[from - 2], buffer[from - 1])) 2 else 1
                }
                buffer.delete(from, selectionStart)
                selectionStart = from
                selectionEnd = from
                true
            }
            every { getTextBeforeCursor(any(), any()) } answers {
                buffer.substring((selectionStart - firstArg<Int>()).coerceAtLeast(0), selectionStart)
            }
            every { getTextAfterCursor(any(), any()) } answers {
                buffer.substring(selectionEnd, (selectionEnd + firstArg<Int>()).coerceAtMost(buffer.length))
            }
        }
    }

    private val alpha = OptimizedLayout.deCh()
    private val symbols = OptimizedLayout.symbols()
    private val number = OptimizedLayout.number()
    private val phone = OptimizedLayout.phone()

    private fun vm(fake: FakeIc, suggester: Suggester? = null): KeyboardViewModel = KeyboardViewModel(
        alphaLayout = alpha,
        symbolsLayout = symbols,
        numberLayout = number,
        phoneLayout = phone,
        inputConnectionProvider = { fake.ic },
        suggester = suggester,
    )

    // --- Reflection-Zugriff auf die privaten Invarianten-Merker (siehe Klassen-KDoc) ---

    private fun KeyboardViewModel.privateBool(field: String): Boolean =
        KeyboardViewModel::class.java.getDeclaredField(field).apply { isAccessible = true }.getBoolean(this)

    private val KeyboardViewModel.autoCapArmed get() = privateBool("autoCapArmed")
    private val KeyboardViewModel.userDisarmedAutoCap get() = privateBool("userDisarmedAutoCap")

    /** Prüft INV-S1 und INV-S2 am aktuellen (beobachtbaren) Zustand. [ctx] nennt Seed + Op zur Repro. */
    private fun KeyboardViewModel.assertShiftInvariants(ctx: String = "") {
        val armed = autoCapArmed
        val disarmed = userDisarmedAutoCap
        val shift = state.value.shift
        if (armed) {
            assertEquals("INV-S1 verletzt (autoCapArmed ⟹ SHIFTED) | $ctx", ShiftState.SHIFTED, shift)
        }
        assertFalse("INV-S2 verletzt (userDisarmedAutoCap ⟹ !autoCapArmed) | $ctx", disarmed && armed)
    }

    // --- Fokussierte, lesbare Tests an den Übergängen, die die Kommentare als knifflig markieren ---

    @Test
    fun `am Satzanfang ist Auto-Cap armiert und steht auf SHIFTED`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput(FieldContext()) // leerer Puffer → Satzanfang
        // INV-S1 explizit an der Quelle: armiert UND SHIFTED.
        assertTrue(vm.autoCapArmed)
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        vm.assertShiftInvariants()
    }

    @Test
    fun `ein Buchstabe am Satzanfang loest die Armierung wieder auf`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput(FieldContext())
        assertTrue(vm.autoCapArmed)
        vm.onKey(CharKey('h')) // committet „H", danach kein Satzanfang mehr
        // Nach dem Buchstaben ist nichts mehr armiert und Shift zurück auf OFF — INV-S1 wahrt sich
        // selbst (nicht-armiert darf jeden Shift haben), aber hier konkret: nicht armiert + OFF.
        assertFalse(vm.autoCapArmed)
        assertEquals(ShiftState.OFF, vm.state.value.shift)
        vm.assertShiftInvariants()
    }

    @Test
    fun `bewusstes Entwaffnen setzt userDisarmed und loescht die Armierung`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput(FieldContext()) // auto-armiertes SHIFTED
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // Nutzer entwaffnet bewusst → OFF
        // INV-S2 explizit: der Disarm-Merker steht, die Armierung ist weg.
        assertTrue(vm.userDisarmedAutoCap)
        assertFalse(vm.autoCapArmed)
        assertEquals(ShiftState.OFF, vm.state.value.shift)
        vm.assertShiftInvariants()
    }

    @Test
    fun `ein manuelles SHIFTED ist gesetzt aber nicht armiert`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = false)) }
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // rein manuell
        assertEquals(ShiftState.SHIFTED, vm.state.value.shift)
        assertFalse(vm.autoCapArmed) // SHIFTED ohne Armierung — die andere Richtung von INV-S1
        vm.assertShiftInvariants()
    }

    @Test
    fun `Auto-Cap ausschalten entwaffnet die Armierung und haelt die Invarianten`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput(FieldContext())
        assertTrue(vm.autoCapArmed)
        vm.applySettings(Settings(autoCapEnabled = false))
        // Der gezielte Entwaffnungs-Übergang in applySettings darf INV-S1 nicht in einen Zustand
        // armiert-aber-nicht-SHIFTED kippen.
        assertFalse(vm.autoCapArmed)
        assertEquals(ShiftState.OFF, vm.state.value.shift)
        vm.assertShiftInvariants()
    }

    @Test
    fun `ein echter Feldwechsel startet nicht-armiert und nicht-entwaffnet`() {
        val fake = FakeIc()
        val vm = vm(fake).apply { applySettings(Settings(autoCapEnabled = true)) }
        vm.onStartInput(FieldContext()) // armiert
        vm.onKey(FunctionKey(KeyAction.SHIFT)) // userDisarmed = true
        assertTrue(vm.userDisarmedAutoCap)
        // Echter Feldwechsel (numerisch → kein Satzanfang) → beide Merker sauber zurückgesetzt.
        vm.onStartInput(FieldContext(numeric = true))
        assertFalse(vm.userDisarmedAutoCap)
        assertFalse(vm.autoCapArmed)
        vm.assertShiftInvariants()
    }

    // --- Struktur-Fuzzer: Shift-Taste + Settings-Toggle inklusive ---

    /**
     * Würfelt seed-reproduzierbare Operationssequenzen, die — anders als der Text-Fuzzer in
     * `KeyboardViewModelTest` — **gezielt die Shift-Maschine treiben**: Shift-Taps, Auto-Cap an/aus
     * (`applySettings`), Buchstaben, Ziffern, satzendende Literale (`. ? !`), Cursor/Auswahl, Vorschlag-
     * Tap, Feld-Restart/-Wechsel — jeweils gefolgt vom Framework-Echo (`onSelectionChanged`). Nach JEDEM
     * Schritt UND nach dem Echo müssen INV-S1 und INV-S2 halten und es darf keine Exception fliegen.
     * Findet Edge×Edge-Kombinationen, die kein handgeschriebener Test enumeriert; eine Meldung nennt
     * Seed + Op-Index zur exakten Reproduktion.
     */
    @Test
    fun `fuzzer - keine Operationssequenz bricht eine Shift-Invariante`() {
        val letters = "aeioubcdhnrst".toList()
        val seeds = 300
        val opsPerSeed = 60
        var total = 0
        for (seed in 0 until seeds) {
            val rng = Random(seed)
            val fake = FakeIc()
            val vm = vm(fake, suggester = Suggester { prefix, _, _ ->
                if (prefix.length >= 2) listOf(prefix.lowercase()) else emptyList()
            }).apply { applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = seed % 2 == 0)) }
            // Das aktuell aktive Feld mitführen: ein RESTART (restarting == true) startet dasselbe Feld
            // neu (Config-Change/Editor-restartInput) und behält daher seinen Typ — das Framework wechselt
            // den Feldtyp nie über einen blossen Restart. Nur ein echter Feldwechsel (restarting == false)
            // zieht ein neues Feld. (Eine künstliche Typ-Änderung auf restarting == true erzeugte einen
            // armiert-aber-OFF-Zustand, den die reale Tastatur nie erreicht — der Fuzzer modelliert hier
            // bewusst die Framework-Realität statt jeder denkbaren Aufruf-Permutation.)
            var currentField = FieldContext()
            vm.onStartInput(currentField)

            repeat(opsPerSeed) { op ->
                val ctx = "seed=$seed op=$op vor='${fake.buffer}' " +
                    "ss=${fake.selectionStart} se=${fake.selectionEnd} shift=${vm.state.value.shift}"
                when (rng.nextInt(12)) {
                    0, 1, 2 -> vm.onKey(CharKey(letters[rng.nextInt(letters.size)]))
                    3 -> vm.onKey(CharKey('5')) // Ziffer (belegt den Satzanfang-Slot)
                    4, 5 -> vm.onKey(FunctionKey(KeyAction.SHIFT)) // Shift-Taps häufig würfeln
                    6 -> { // satzendendes / neutrales Literal
                        val action = listOf(
                            KeyAction.SPACE, KeyAction.PERIOD, KeyAction.COMMA,
                            KeyAction.QUESTION, KeyAction.EXCLAMATION,
                        )[rng.nextInt(5)]
                        vm.onKey(FunctionKey(action))
                    }
                    7 -> { // Auto-Cap an/aus toggeln — trifft den gezielten Entwaffnungs-Übergang
                        vm.applySettings(Settings(suggestionsEnabled = true, autoCapEnabled = rng.nextBoolean()))
                    }
                    8 -> { // Cursor bewegen
                        val p = rng.nextInt(fake.buffer.length + 1)
                        fake.select(p, p); vm.onSelectionChanged(p, p)
                    }
                    9 -> { // Auswahl setzen
                        val a = rng.nextInt(fake.buffer.length + 1)
                        val b = a + rng.nextInt(fake.buffer.length - a + 1)
                        fake.select(a, b); vm.onSelectionChanged(a, b)
                    }
                    10 -> { // Vorschlag antippen (nur wenn einer steht)
                        vm.state.value.suggestions.firstOrNull()?.let { vm.onSuggestionTap(it) }
                    }
                    11 -> { // Feld-Restart (gleiches Feld) ODER echter Feldwechsel (neues, evtl. numerisch/Passwort)
                        val restarting = rng.nextBoolean()
                        if (!restarting) {
                            currentField = when (rng.nextInt(3)) {
                                0 -> FieldContext(initialSelStart = fake.selectionStart, initialSelEnd = fake.selectionEnd)
                                1 -> FieldContext(numeric = true)
                                else -> FieldContext(isPassword = true)
                            }
                        }
                        vm.onStartInput(field = currentField, restarting = restarting)
                    }
                }
                vm.assertShiftInvariants("$ctx (nach Op)")
                // Framework-Echo nachstellen (die Naht, an der eine externe Bewegung Auto-Cap neu rechnet).
                vm.onSelectionChanged(fake.selectionStart, fake.selectionEnd)
                vm.assertShiftInvariants("$ctx (nach Echo)")
                total++
            }
        }
        println("Shift-Invarianten-Fuzzer: $total Operationen über $seeds Seeds ohne Invariantenbruch")
    }
}
