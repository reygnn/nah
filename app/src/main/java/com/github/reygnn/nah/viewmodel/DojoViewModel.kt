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

/**
 * Wie das nächste Ziel GEWÄHLT wird: [RANDOM] springt frei im Pool umher, [GUIDED] geht ihn der
 * Reihe nach durch. Das betrifft nur die Auswahl beim *Weiterrücken* — und weitergerückt wird in
 * beiden Modi ausschliesslich nach einem Treffer. Ein Fehltipp lässt das aktuelle Ziel stehen
 * (man muss die Position finden, bevor es weitergeht), springt also auch im RANDOM-Modus nicht.
 */
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
 * Ein Stufen-Rekord: höchster Score und längste Serie — zwei UNABHÄNGIGE Maxima (nicht ein Paar), die
 * aus verschiedenen Läufen stammen dürfen. Pro [DojoLevel] einer; siehe [DojoState.bests].
 */
data class LevelBest(val score: Int = 0, val streak: Int = 0)

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
    // Rekorde PRO STUFE (Modi zusammengefasst) — je Stufe zwei unabhängige Maxima (siehe [LevelBest]).
    // Bewusst per-Stufe statt global: weil das WORT-Scoring nach Wortlänge skaliert und kurze Pools wie
    // die Vokale am leichtesten lange Serien tragen, wäre EIN globaler Rekord faktisch nur „wie lange
    // hast du Vokale gedrückt". Pro Stufe bleibt jeder Rekord für sich aussagekräftig. Das ist der
    // ANZEIGE-Wert fürs Scoreboard (die jeweils gewählte Stufe, siehe [bestFor]): klettert live im Lauf
    // mit und überlebt einen Reset. Bewusst NICHT die Persistenzquelle pro Frame — den dauerhaften
    // Record schreibt die Activity event-gesteuert an Run-Grenzen (Lauf zu Ende / Verlassen), nicht bei
    // jedem Treffer (siehe DojoActivity).
    val bests: Map<DojoLevel, LevelBest> = emptyMap(),
) {
    /** Der Rekord der gegebenen Stufe (Default-Nullrekord, solange die Stufe noch keinen hat). */
    fun bestFor(level: DojoLevel): LevelBest = bests[level] ?: LevelBest()

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

    // Jedes Zeichen, das die echte Tastatur produzieren kann — die Vereinigung aller CharKey-Outputs
    // UND ihrer Long-Press-Alternativen (qu, ch, Umlaute, Akzente …), kleingeschrieben. Da das volle
    // Alphabet je eine eigene Taste hat und ä/ö/ü als Ein-Zeichen-Alternative auf ihrem Grundvokal
    // liegen, gilt: „jedes Zeichen einzeln tippbar" ist genau „jedes Zeichen ist hier enthalten".
    private val producibleChars: Set<Char> =
        charKeys.flatMap { listOf(it.output) + it.alternatives }
            .joinToString("")
            .lowercase()
            .toSet()

    // `distinct()` erzwingt die Duplikatfreiheit, auf die sich randomTarget verlässt, statt sie nur
    // dem (eingefrorenen) Layout zu unterstellen — zwei Tasten mit demselben Output würden den
    // Anti-Repeat-Loop sonst hängen lassen. Heute ändert es nichts an der Pool-Reihenfolge.
    private val vowelTargets = charKeys.filter { it.char in VOWELS_SET }.map { it.output.lowercase() }.distinct()
    private val consonantTargets = charKeys.filter { it.char in CONSONANTS_SET }.map { it.output.lowercase() }.distinct()
    // Die qu-Taste committet „qu" → ihr Ziel ist „qu" (genau das, was ein Tap liefert), ehrlich
    // zum Layout. Alle anderen Tasten committen ihren eigenen Buchstaben.
    private val alphabetTargets = charKeys.map { it.output.lowercase() }.distinct()
    // Häufigste zuerst (Guided beginnt mit den geläufigsten Wörtern), kleingeschrieben, dedupliziert.
    // GEFILTERT auf tippbare Wörter: das Korpus (GermanWordList) darf laut CLAUDE.md wachsen, OHNE das
    // eingefrorene Layout anzufassen. Käme je ein Wort mit einem hier nicht produzierbaren Zeichen in
    // den Drill (Leerzeichen/Phrase, ß, Ziffer …), wäre sein Ziel unausweichlich untippbar und triebe
    // garantiert in den Game Over — ein Fehltipp-Tod ohne richtigen Tap. Der Filter koppelt den Pool
    // ehrlich an das, was die Tastatur committen kann; der Round-Trip-Test pinnt die Invariante.
    private val wordTargets = GermanWordList.words
        .sortedByDescending { it.second }
        .map { it.first.lowercase() }
        .distinct()
        .filter { word -> word.all { it in producibleChars } }

    // Laufzeiger für den Guided-Modus (Index in den aktuellen Pool); im Random-Modus ungenutzt.
    private var guidedIndex = 0

    init {
        nextChallenge()
    }

    // --- öffentliche Aktionen ---

    /**
     * Modus-Toggle (RANDOM↔GUIDED): **behält den laufenden Spielstand** (Score/Serie/Leben) und zieht
     * nur ein frisches Ziel aus DEMSELBEN Pool (siehe [switchPool]). Ein Moduswechsel ändert bloss die
     * Ziehreihenfolge, nicht die Stufe — er kann also nichts in einen fremden Stufen-Rekord lecken und
     * muss den Lauf nicht zurücksetzen (anders als [setLevel]). Ausnahme Game Over: ein toter Lauf lässt
     * sich nicht „behalten", und ein [switchPool] hinterliesse einen widersprüchlichen
     * target-gesetzt-aber-gameOver-Zustand → dort voller [resetGame].
     */
    fun setMode(mode: DojoMode) {
        if (_state.value.mode == mode) return
        _state.update { it.copy(mode = mode) }
        if (_state.value.gameOver) resetGame() else switchPool()
    }

    /**
     * Stufenwechsel startet **immer einen frischen Lauf** (Score/Serie/Leben zurück auf Anfang) — die
     * persistierten Stufen-Rekorde ([DojoState.bests]) bleiben. Konsequenz aus den per-Stufe-Rekorden:
     * ein Lauf gehört zu genau einer Stufe, also darf eine auf der leichten Vokal-Stufe aufgebaute
     * Serie/Score beim Wechsel nicht in den Rekord einer anderen Stufe lecken (sonst wäre die
     * Bestenliste wieder von der leichtesten Stufe dominierbar). [resetGame] deckt zugleich den
     * Game-Over-Fall sauber ab: kein widersprüchlicher target-gesetzt-aber-gameOver-Zustand.
     */
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

    /** Lädt die persistierten Rekorde ins Spiel — von der Activity beim Beobachten von
     *  `DojoStatsRepository` aufgerufen (analog zu `KeyboardViewModel.applySettings`: Persistenz
     *  fliesst durch eine Methode, nicht durch den Konstruktor, damit der ViewModel rein bleibt).
     *  Hebt jedes Feld jeder Stufe nur an, nie ab (feldweises Maximum pro Stufe) — so kann ein
     *  verspätetes Lade-Echo einen in dieser Sitzung bereits erspielten höheren Score oder eine
     *  längere Serie nicht überschreiben. */
    fun setBests(loaded: Map<DojoLevel, LevelBest>) {
        _state.update { state ->
            val merged = state.bests.toMutableMap()
            for ((level, best) in loaded) {
                val cur = merged[level] ?: LevelBest()
                merged[level] = LevelBest(maxOf(cur.score, best.score), maxOf(cur.streak, best.streak))
            }
            state.copy(bests = merged)
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
        val s = _state.value
        // Funktionstasten starten bei Game Over BEWUSST nicht neu — nur ein Buchstaben-Tap
        // (onInput) tut das. Sonst riss ein versehentlich gestreiftes Shift/Space/Backspace die
        // gerade erspielte Runde sofort weg, bevor man den Endstand überhaupt anschauen konnte.
        if (s.gameOver) return
        // In der Wort-Stufe nimmt Backspace den letzten getippten Buchstaben zurück (Korrektur-
        // hilfe, keine Strafe). Alle anderen Funktionstasten (Shift, Space, Ebenenwechsel, …)
        // sind im Drill neutral — sie sind kein Tipp-Versuch und kosten kein Leben.
        if (action == KeyAction.BACKSPACE && s.level == DojoLevel.WORDS && s.typed.isNotEmpty()) {
            _state.update { it.copy(typed = it.typed.dropLast(1), lastResult = null) }
        }
    }

    private fun registerCorrect() {
        // Aus _state.value heraus rechnen (einmal lesen, wie onFunction): kein separater Snapshot.
        _state.update {
            // Buchstaben-Stufen: ein Tap = fixe Grundpunkte. WORT-Stufe: nach Wortlänge skaliert, damit
            // ein vollständiges Wort proportional zum Aufwand (Anzahl Taps) zählt — sonst wäre ein
            // 12-Zeichen-Wort so viel wert wie ein einzelner Vokal-Tap und das Punkte-Grinden liefe
            // immer über den leichtesten Pool. (Die qu-Taste der Alphabet-Stufe bleibt ein Tap = fix.)
            val base = if (it.level == DojoLevel.WORDS) POINTS_PER_HIT * it.target.length else POINTS_PER_HIT
            val newScore = it.score + base + it.streak * STREAK_BONUS
            val newStreak = it.streak + 1
            // Stufen-Rekord als zwei unabhängige Maxima mitführen: jedes Feld klettert für sich, ohne
            // dass Score und Serie aus demselben Lauf stammen müssen. resetGame lässt die Rekorde stehen.
            val cur = it.bestFor(it.level)
            val updatedBest = LevelBest(maxOf(cur.score, newScore), maxOf(cur.streak, newStreak))
            it.copy(
                score = newScore,
                streak = newStreak,
                bests = it.bests + (it.level to updatedBest),
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

    /**
     * Modus-Wechsel bei laufendem Spiel: zieht ein frisches Ziel aus DEMSELBEN Pool, **ohne** den
     * Spielstand anzutasten — Score, Serie und Leben bleiben stehen (anders als [resetGame], das beim
     * Neustart nach Game Over und bei jedem Stufenwechsel greift). Der Guided-Cursor springt auf den
     * Anfang des Pools; ein etwaiger Wort-Fortschritt und ein stehengebliebenes Treffer/Fehler-Aufblitzen
     * werden verworfen.
     */
    private fun switchPool() {
        guidedIndex = 0
        _state.update { it.copy(typed = "", lastResult = null) }
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
