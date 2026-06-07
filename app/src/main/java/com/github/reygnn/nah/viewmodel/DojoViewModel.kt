package com.github.reygnn.nah.viewmodel

import androidx.lifecycle.ViewModel
import com.github.reygnn.nah.data.suggestions.GermanWordList
import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.FunctionKey
import com.github.reygnn.nah.layout.KeyAction
import com.github.reygnn.nah.layout.KeyboardKey
import com.github.reygnn.nah.layout.OptimizedLayout
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Random springt frei im Pool umher; Guided geht ihn der Reihe nach durch. */
enum class DojoMode {
    RANDOM,
    GUIDED,
}

/**
 * Die vier Übungsstufen — Buchstaben-Positionen, dann ganze Wörter. Bewusst KEINE
 * displayName-Strings im Enum (anders als vuot): die UI mappt jeden Wert auf eine
 * `stringResource` (de/en), die Logik bleibt sprachfrei.
 *
 * Die Ziel-Pools werden aus demselben [OptimizedLayout.deCh] abgeleitet, das die echte
 * Tastatur rendert — das Dojo LIEST das (eingefrorene) Layout nur, es ändert nichts.
 */
enum class DojoLevel {
    /** Der zentrale Vokal-Cluster a/e/i/o/u. */
    VOWELS,
    /** Die häufigsten Konsonanten (dieselben wie [com.github.reygnn.nah.ui.NahColors]). */
    CONSONANTS,
    /** Alle Buchstaben-Tasten (die qu-Digraph-Taste → Ziel „qu"). */
    ALPHABET,
    /** Echte de-CH-Wörter aus [GermanWordList], Zeichen für Zeichen. */
    WORDS,
}

/**
 * Spielzustand des Dojos. [target] ist immer kleingeschrieben (der Drill geht um Positionen,
 * nicht um Gross-/Kleinschreibung — verglichen wird case-insensitiv). [typed] ist der bereits
 * korrekt getippte Anfang des Ziels (nur in der Wort-Stufe relevant; bei Buchstaben-Stufen
 * immer leer). [lastResult] treibt das kurze Erfolg/Fehler-Aufblitzen (true/false/`null`).
 */
data class DojoState(
    val score: Int = 0,
    val streak: Int = 0,
    val lives: Int = MAX_LIVES,
    val mode: DojoMode = DojoMode.RANDOM,
    val level: DojoLevel = DojoLevel.VOWELS,
    val target: String = "",
    val typed: String = "",
    val lastResult: Boolean? = null,
    val gameOver: Boolean = false,
    // Bester Score / beste Serie global über alle Stufen und Modi — der einzige Zustand, der einen
    // Reset (und, von der Activity persistiert, das Verlassen des Dojos) überlebt. Der laufende
    // Spielstand bleibt absichtlich flüchtig.
    val bestScore: Int = 0,
    val bestStreak: Int = 0,
) {
    companion object {
        const val MAX_LIVES = 5
    }
}

/**
 * Zustandsmaschine für das Tipp-Training („Dojo"). Ein [androidx.lifecycle.ViewModel] (anders als
 * [KeyboardViewModel], das im IME-Service ohne Lifecycle lebt), damit der Spielstand einen
 * Config-Change (Drehung) überlebt — sonst startete jede Drehung die laufende Runde neu. Reine
 * Logik + [StateFlow], kein viewModelScope/Coroutine, also auch ohne Robolectric JVM-testbar
 * (der `ViewModel()`-Default-Konstruktor berührt kein Android-Runtime).
 *
 * **Trainiert die Positionen, nicht das Tippen von Text:** das Dojo committet nichts in ein
 * Eingabefeld, es zeigt ein Ziel und prüft den Tap. Damit das Muskelgedächtnis exakt passt,
 * rendert die UI dieselbe `KeyboardContent` wie die echte Tastatur; dieser ViewModel bekommt
 * deren Tap-Ereignisse ([onKey]/[onAlternative]) und verrechnet sie zu Score/Streak/Lives.
 *
 * [random] ist injizierbar, damit der Test die Challenge-Auswahl deterministisch machen kann.
 * Alle Konstruktor-Parameter haben Defaults → Kotlin erzeugt den parameterlosen Konstruktor, den
 * die `viewModel()`-Default-Factory braucht.
 */
class DojoViewModel(
    private val random: Random = Random.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(DojoState())
    val state: StateFlow<DojoState> = _state.asStateFlow()

    // Das eingefrorene Buchstaben-Layout — einzige Quelle der Ziel-Pools, damit Dojo und echte
    // Tastatur nie auseinanderlaufen. Nur gelesen.
    private val charKeys = OptimizedLayout.deCh().rows.flatten().filterIsInstance<CharKey>()

    private val vowelTargets = charKeys.filter { it.char in VOWELS_SET }.map { it.output.lowercase() }
    private val consonantTargets = charKeys.filter { it.char in CONSONANTS_SET }.map { it.output.lowercase() }
    // Die qu-Taste committet „qu" → ihr Ziel ist „qu" (genau das, was ein Tap liefert), ehrlich
    // zum Layout. Alle anderen Tasten committen ihren eigenen Buchstaben.
    private val alphabetTargets = charKeys.map { it.output.lowercase() }
    // Häufigste zuerst (Guided beginnt mit den geläufigsten Wörtern), kleingeschrieben, dedupliziert.
    private val wordTargets = GermanWordList.words
        .sortedByDescending { it.second }
        .map { it.first.lowercase() }
        .distinct()

    // Laufzeiger für den Guided-Modus (Index in den aktuellen Pool); im Random-Modus ungenutzt.
    private var guidedIndex = 0

    init {
        nextChallenge()
    }

    // --- öffentliche Aktionen ---

    fun setMode(mode: DojoMode) {
        if (_state.value.mode == mode) return
        _state.update { it.copy(mode = mode) }
        resetGame()
    }

    fun setLevel(level: DojoLevel) {
        if (_state.value.level == level) return
        _state.update { it.copy(level = level) }
        resetGame()
    }

    /** Tap auf eine Taste der gerenderten Tastatur. Buchstaben sind ein Versuch; Funktionstasten
     *  sind im Drill neutral (nur Backspace nimmt in der Wort-Stufe den letzten Buchstaben zurück). */
    fun onKey(key: KeyboardKey) {
        when (key) {
            is CharKey -> onInput(key.output)
            is FunctionKey -> onFunction(key.action)
        }
    }

    /** Eine im Long-Press-Popup gewählte Alternative (Umlaut/Akzent/Digraph) — derselbe Eingabe-
     *  pfad wie ein Tap, damit Umlaut-Wörter in der Wort-Stufe drillbar sind (ä/ö/ü liegen per
     *  Long-Press auf ihrem Grundvokal). */
    fun onAlternative(text: String) = onInput(text)

    /** Lädt den persistierten Bestwert ins Spiel — von der Activity beim Beobachten von
     *  `DojoStatsRepository` aufgerufen (analog zu `KeyboardViewModel.applySettings`: Persistenz
     *  fliesst durch eine Methode, nicht durch den Konstruktor, damit der ViewModel rein bleibt).
     *  Hebt den Bestwert nur an, senkt ihn nie — so kann ein verspätetes Lade-Echo einen bereits
     *  in dieser Sitzung erspielten höheren Wert nicht überschreiben. */
    fun setBest(bestScore: Int, bestStreak: Int) {
        _state.update {
            it.copy(
                bestScore = maxOf(it.bestScore, bestScore),
                bestStreak = maxOf(it.bestStreak, bestStreak),
            )
        }
    }

    // --- interne Logik ---

    private fun onInput(text: String) {
        val s = _state.value
        // Nach „Game Over" startet jeder beliebige Tap eine neue Runde.
        if (s.gameOver) {
            resetGame()
            return
        }
        if (s.target.isEmpty()) return
        val input = text.lowercase()

        if (s.level == DojoLevel.WORDS) {
            val remaining = s.target.substring(s.typed.length)
            when {
                remaining == input -> { // letztes Stück → Wort komplett
                    registerCorrect()
                    nextChallenge()
                }
                remaining.startsWith(input) -> { // korrekter Teil-Fortschritt, (noch) keine Punkte
                    _state.update { it.copy(typed = it.typed + input, lastResult = null) }
                }
                else -> registerWrong()
            }
        } else {
            if (input == s.target) {
                registerCorrect()
                nextChallenge()
            } else {
                registerWrong()
            }
        }
    }

    private fun onFunction(action: KeyAction) {
        if (_state.value.gameOver) {
            resetGame()
            return
        }
        // In der Wort-Stufe nimmt Backspace den letzten getippten Buchstaben zurück (Korrektur-
        // hilfe, keine Strafe). Alle anderen Funktionstasten (Shift, Space, Ebenenwechsel, …)
        // sind im Drill neutral — sie sind kein Tipp-Versuch und kosten kein Leben.
        if (action == KeyAction.BACKSPACE &&
            _state.value.level == DojoLevel.WORDS &&
            _state.value.typed.isNotEmpty()
        ) {
            _state.update { it.copy(typed = it.typed.dropLast(1), lastResult = null) }
        }
    }

    private fun registerCorrect() {
        _state.update {
            val newScore = it.score + POINTS_PER_HIT + it.streak * STREAK_BONUS
            val newStreak = it.streak + 1
            it.copy(
                score = newScore,
                streak = newStreak,
                // Bestwert monoton mitführen; resetGame lässt diese Felder bewusst stehen.
                bestScore = maxOf(it.bestScore, newScore),
                bestStreak = maxOf(it.bestStreak, newStreak),
                lastResult = true,
            )
        }
    }

    private fun registerWrong() {
        _state.update {
            val newLives = (it.lives - 1).coerceAtLeast(0)
            it.copy(
                streak = 0,
                lives = newLives,
                lastResult = false,
                gameOver = newLives <= 0,
            )
        }
    }

    private fun resetGame() {
        guidedIndex = 0
        _state.update {
            it.copy(
                score = 0,
                streak = 0,
                lives = DojoState.MAX_LIVES,
                target = "",
                typed = "",
                lastResult = null,
                gameOver = false,
            )
        }
        nextChallenge()
    }

    private fun nextChallenge() {
        val pool = poolFor(_state.value.level)
        if (pool.isEmpty()) {
            _state.update { it.copy(target = "", typed = "") }
            return
        }
        val target = when (_state.value.mode) {
            DojoMode.RANDOM -> randomTarget(pool, avoid = _state.value.target)
            DojoMode.GUIDED -> pool[guidedIndex % pool.size].also { guidedIndex++ }
        }
        _state.update { it.copy(target = target, typed = "") }
    }

    /** Zieht ein Zufallsziel, aber nicht zweimal dasselbe direkt hintereinander — bei kleinen Pools
     *  (z. B. den fünf Vokalen) wirkte eine sofortige Wiederholung sonst wie ein Hänger statt wie
     *  Zufall. Die Pools sind per Konstruktion duplikatfrei, die Schleife terminiert also; bei
     *  Pool-Grösse 1 ist die Wiederholung unvermeidlich und wird oben abgekürzt. [avoid] ist beim
     *  ersten Zug nach einem Reset leer und passt dann auf nichts. */
    private fun randomTarget(pool: List<String>, avoid: String): String {
        if (pool.size <= 1) return pool[0]
        var pick = pool[random.nextInt(pool.size)]
        while (pick == avoid) pick = pool[random.nextInt(pool.size)]
        return pick
    }

    private fun poolFor(level: DojoLevel): List<String> = when (level) {
        DojoLevel.VOWELS -> vowelTargets
        DojoLevel.CONSONANTS -> consonantTargets
        DojoLevel.ALPHABET -> alphabetTargets
        DojoLevel.WORDS -> wordTargets
    }

    private companion object {
        const val POINTS_PER_HIT = 10
        const val STREAK_BONUS = 2

        val VOWELS_SET = "aeiou".toSet()
        /** Dieselben häufigen Konsonanten wie die Lern-Farben (NahColors.keyConsonants). */
        val CONSONANTS_SET = "srntdh".toSet()
    }
}
