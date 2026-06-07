package com.github.reygnn.nah.viewmodel

import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.FunctionKey
import com.github.reygnn.nah.layout.KeyAction
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Zustandsmaschine des Tipp-Trainings — synchron, kein `Dispatchers.Main`, also kein
 * `MainDispatcherRule` nötig. Die meisten Tests fahren im GUIDED-Modus, damit die Ziele
 * deterministisch der Pool-Reihenfolge folgen.
 */
class DojoViewModelTest {

    private fun vm() = DojoViewModel(random = Random(0))

    /** Eingabe eines Strings über den Long-Press-Pfad (geht durch dieselbe onInput-Logik wie ein Tap). */
    private fun DojoViewModel.type(text: String) = onAlternative(text)

    @Test
    fun `guided startet mit dem ersten Ziel des Vokal-Pools`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED) // setzt zurück und nimmt das erste Pool-Ziel
        // Vokal-Cluster in Layout-Reihenfolge: o, u, a, i, e.
        assertEquals("o", vm.state.value.target)
        assertEquals(DojoState.MAX_LIVES, vm.state.value.lives)
    }

    @Test
    fun `richtiger Tap gibt Punkte, erhoeht die Serie und geht zum naechsten Ziel`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        vm.onKey(CharKey('o'))
        val s = vm.state.value
        assertEquals(10, s.score)
        assertEquals(1, s.streak)
        assertEquals(true, s.lastResult)
        assertEquals("u", s.target) // nächstes Vokal-Ziel
    }

    @Test
    fun `Serien-Bonus waechst mit jeder Folge`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        vm.onKey(CharKey('o')) // +10 (Serie 0)
        vm.onKey(CharKey('u')) // +10 + 1*2 = 12
        assertEquals(22, vm.state.value.score)
        assertEquals(2, vm.state.value.streak)
    }

    @Test
    fun `falscher Tap kostet ein Leben und bricht die Serie, Ziel bleibt`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        vm.onKey(CharKey('o')) // korrekt → Serie 1, Ziel "u"
        vm.onKey(CharKey('x')) // falsch
        val s = vm.state.value
        assertEquals(DojoState.MAX_LIVES - 1, s.lives)
        assertEquals(0, s.streak)
        assertEquals(false, s.lastResult)
        assertEquals("u", s.target) // unverändert
    }

    @Test
    fun `Funktionstasten sind neutral und kosten kein Leben`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        vm.onKey(FunctionKey(KeyAction.SHIFT))
        vm.onKey(FunctionKey(KeyAction.SPACE))
        vm.onKey(FunctionKey(KeyAction.SYMBOLS))
        val s = vm.state.value
        assertEquals(DojoState.MAX_LIVES, s.lives)
        assertEquals(0, s.streak)
        assertEquals("o", s.target)
    }

    @Test
    fun `guided geht den Vokal-Pool der Reihe nach durch und faengt von vorne an`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        val seen = mutableListOf<String>()
        repeat(6) {
            seen += vm.state.value.target
            vm.onKey(CharKey(vm.state.value.target.first()))
        }
        assertEquals(listOf("o", "u", "a", "i", "e", "o"), seen)
    }

    @Test
    fun `Alphabet-Stufe drillt die qu-Digraph-Taste als eigenes Ziel`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        vm.setLevel(DojoLevel.ALPHABET)
        assertEquals("x", vm.state.value.target) // erstes Layout-Zeichen
        vm.onKey(CharKey('x'))
        assertEquals("qu", vm.state.value.target) // q-Taste committet „qu"
        // Tap auf die qu-Taste (output „qu") trifft das Ziel.
        vm.onKey(CharKey('q', output = "qu"))
        assertEquals(true, vm.state.value.lastResult)
        assertEquals(DojoState.MAX_LIVES, vm.state.value.lives) // kein Fehltreffer
        // x-Treffer (10, Serie 0) + qu-Treffer (10 + 1*2) = 22.
        assertEquals(22, vm.state.value.score)
    }

    @Test
    fun `Wort-Stufe schreitet Zeichen fuer Zeichen voran und punktet erst beim kompletten Wort`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        vm.setLevel(DojoLevel.WORDS)
        val word = vm.state.value.target
        assertTrue("erwartete ein nicht-leeres Wort", word.isNotEmpty())
        // Bis zum vorletzten Zeichen: Fortschritt, aber keine Punkte und kein Lebensverlust.
        for (i in 0 until word.length - 1) {
            vm.type(word[i].toString())
            assertEquals(word.substring(0, i + 1), vm.state.value.typed)
            assertEquals(0, vm.state.value.score)
            assertEquals(DojoState.MAX_LIVES, vm.state.value.lives)
        }
        // Letztes Zeichen vervollständigt das Wort → Punkte, neues Ziel, typed zurückgesetzt.
        vm.type(word.last().toString())
        assertEquals(10, vm.state.value.score)
        assertEquals("", vm.state.value.typed)
        assertTrue(vm.state.value.target.isNotEmpty())
    }

    @Test
    fun `Wort-Stufe akzeptiert mehrzeichige Eingaben als Praefix (Digraph qu im Wort)`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        vm.setLevel(DojoLevel.WORDS)
        val word = vm.state.value.target
        // Die ersten beiden Zeichen in EINER Eingabe (so liefert die qu-Taste „qu" mitten im Wort).
        vm.type(word.substring(0, 2))
        assertEquals(word.substring(0, 2), vm.state.value.typed)
    }

    @Test
    fun `falsches Zeichen in der Wort-Stufe kostet ein Leben, Fortschritt bleibt`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        vm.setLevel(DojoLevel.WORDS)
        val word = vm.state.value.target
        vm.type(word.first().toString()) // ein korrektes Zeichen
        // Ein garantiert falsches nächstes Zeichen wählen.
        val wrong = if (word.getOrNull(1) == 'x') 'y' else 'x'
        vm.type(wrong.toString())
        assertEquals(DojoState.MAX_LIVES - 1, vm.state.value.lives)
        assertEquals(word.substring(0, 1), vm.state.value.typed) // Fortschritt unverändert
    }

    @Test
    fun `Backspace nimmt in der Wort-Stufe den letzten Buchstaben zurueck, ohne zu bestrafen`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        vm.setLevel(DojoLevel.WORDS)
        val word = vm.state.value.target
        vm.type(word.first().toString())
        vm.onKey(FunctionKey(KeyAction.BACKSPACE))
        assertEquals("", vm.state.value.typed)
        assertEquals(DojoState.MAX_LIVES, vm.state.value.lives)
    }

    @Test
    fun `fuenf Fehler beenden das Spiel, danach startet jeder Tap neu`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        repeat(DojoState.MAX_LIVES) { vm.onKey(CharKey('x')) } // 5x falsch (Ziel ist ein Vokal)
        assertTrue(vm.state.value.gameOver)
        assertEquals(0, vm.state.value.lives)
        // Ein beliebiger Tap startet neu.
        vm.onKey(CharKey('o'))
        val s = vm.state.value
        assertFalse(s.gameOver)
        assertEquals(DojoState.MAX_LIVES, s.lives)
        assertEquals(0, s.score)
        assertEquals("o", s.target) // guided beginnt wieder bei vorne
    }

    @Test
    fun `random wiederholt dasselbe Ziel nicht direkt hintereinander`() {
        // RANDOM ist der Default-Modus — kein setMode nötig. Der Vokal-Pool hat nur fünf Ziele;
        // ohne Anti-Repeat käme häufig zweimal dasselbe in Folge.
        val vm = DojoViewModel(random = Random(0))
        val seen = mutableListOf<String>()
        repeat(50) {
            val t = vm.state.value.target
            seen += t
            vm.onKey(CharKey(t.first())) // korrekt → nächstes Ziel (kein Lebensverlust)
        }
        for (i in 1 until seen.size) {
            assertNotEquals("zwei gleiche Ziele in Folge bei $i", seen[i - 1], seen[i])
        }
        // Und der Pool wird auch wirklich variiert (nicht etwa auf einem Wert festgehakt).
        assertTrue(seen.toSet().size >= 2)
    }

    @Test
    fun `Bestwert haelt Score und Serie fest und ueberlebt einen Reset`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        vm.onKey(CharKey('o')) // +10, Serie 1
        vm.onKey(CharKey('u')) // +12 → 22, Serie 2
        assertEquals(22, vm.state.value.bestScore)
        assertEquals(2, vm.state.value.bestStreak)
        // Fünf Fehltipper → Game Over, danach ein Tap → Neustart (frischer Spielstand).
        repeat(DojoState.MAX_LIVES) { vm.onKey(CharKey('z')) } // 'z' ist kein Vokal → falsch
        assertTrue(vm.state.value.gameOver)
        vm.onKey(CharKey('o')) // startet neu
        assertEquals(0, vm.state.value.score)
        // Bestwert bleibt über den Reset hinweg erhalten.
        assertEquals(22, vm.state.value.bestScore)
        assertEquals(2, vm.state.value.bestStreak)
    }

    @Test
    fun `setBest hebt den Bestwert an, senkt ihn aber nie`() {
        val vm = vm()
        vm.setBest(100, 7)
        assertEquals(100, vm.state.value.bestScore)
        assertEquals(7, vm.state.value.bestStreak)
        vm.setBest(50, 3) // niedriger → ignoriert
        assertEquals(100, vm.state.value.bestScore)
        assertEquals(7, vm.state.value.bestStreak)
    }

    @Test
    fun `Stufenwechsel laesst den Spielstand stehen und tauscht nur den Pool`() {
        val vm = vm()
        vm.setMode(DojoMode.GUIDED)
        vm.onKey(CharKey('o')) // korrekt → 10 Punkte
        vm.onKey(CharKey('x')) // Fehltipp → ein Leben weg
        assertEquals(10, vm.state.value.score)
        assertEquals(DojoState.MAX_LIVES - 1, vm.state.value.lives)
        vm.setLevel(DojoLevel.CONSONANTS)
        val s = vm.state.value
        // Spielstand bleibt erhalten — ein Stufenwechsel ist keine Strafe.
        assertEquals(10, s.score)
        assertEquals(DojoState.MAX_LIVES - 1, s.lives)
        // Nur Pool/Ziel wechseln: erster Konsonant in Layout-Reihenfolge (h, s, r, t, n, d).
        assertEquals("h", s.target)
        assertEquals("", s.typed)
        assertEquals(null, s.lastResult) // stehengebliebenes Aufblitzen verworfen
    }
}
