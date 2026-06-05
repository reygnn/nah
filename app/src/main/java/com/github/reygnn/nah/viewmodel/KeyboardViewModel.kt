package com.github.reygnn.nah.viewmodel

import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.github.reygnn.nah.data.suggestions.Suggester
import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.FunctionKey
import com.github.reygnn.nah.layout.KeyAction
import com.github.reygnn.nah.layout.KeyboardKey
import com.github.reygnn.nah.layout.KeyboardLayout
import com.github.reygnn.nah.settings.Settings
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ShiftState {
    OFF,
    SHIFTED,
    CAPS;

    /**
     * Wendet die Schreibweise dieses Shift-Zustands auf [text] an: [SHIFTED] schreibt nur
     * den ersten Buchstaben gross („qu"→„Qu", „sch"→„Sch"), [CAPS] alles, [OFF] nichts.
     * **Einzige Quelle** für das Commit ([KeyboardViewModel]) UND die Tastenbeschriftung
     * (`ui/TapKey`), damit Angezeigtes und Getipptes nie auseinanderdriften — genau das
     * wäre ein stiller Bruch der „kein Autocorrect"-Garantie. Locale-unabhängig (ROOT),
     * damit derselbe Tap auf jedem Gerät dasselbe Zeichen liefert (kein türkisches i/İ).
     */
    fun applyTo(text: String): String = when (this) {
        OFF -> text
        SHIFTED -> text.replaceFirstChar { it.uppercaseChar() }
        CAPS -> text.uppercase(Locale.ROOT)
    }
}

data class KeyboardUiState(
    val layout: KeyboardLayout,
    val shift: ShiftState = ShiftState.OFF,
    val suggestions: List<String> = emptyList(),
    /** Ob die Vorschlagsleiste Platz belegt (feste Höhe), auch wenn [suggestions]
     *  gerade leer ist. So springt die Tastatur beim Erscheinen/Verschwinden der
     *  Vorschläge nicht in der Höhe. Aus, wenn die Funktion ganz deaktiviert ist. */
    val suggestionBarVisible: Boolean = false,
    /** „Stützräder": Vokale/häufige Konsonanten farbig einfärben (Lernhilfe). */
    val colorHints: Boolean = false,
    /** Hat die Zwischenablage Text? Steuert, ob die Einfügen-Taste aktiv ist. */
    val pasteAvailable: Boolean = false,
)

/**
 * Orchestriert das Tippen. Reine Entscheidungslogik + [StateFlow]; der IME-Service
 * ist dünner Glue. **Kein Autocorrect, keine Wortersetzung von fertigem Text** —
 * jeder Tap committet genau das getippte Zeichen. Vorschläge (falls aktiviert)
 * ersetzen nur das aktuelle, noch unfertige Präfix und nur auf Antippen.
 */
class KeyboardViewModel(
    private val alphaLayout: KeyboardLayout,
    private val symbolsLayout: KeyboardLayout,
    private val phoneLayout: KeyboardLayout,
    private val inputConnectionProvider: () -> InputConnection?,
    private val suggester: Suggester? = null,
    /** Liefert den aktuellen Zwischenablage-Text (oder `null`/leer). Wird NUR beim
     *  tatsächlichen Einfügen gelesen (Inhalt-Zugriff → System-Toast), nicht laufend. */
    private val clipboardTextProvider: () -> String? = { null },
) {

    private val _state = MutableStateFlow(KeyboardUiState(layout = alphaLayout))
    val state: StateFlow<KeyboardUiState> = _state.asStateFlow()

    private var settings = Settings()

    // Vom Service über onSelectionChanged gepflegt. Eine aktive Auswahl (selStart !=
    // selEnd) muss Backspace löschen können; deleteSurroundingText würde sie nicht
    // anfassen.
    private var selStart = 0
    private var selEnd = 0
    private val hasSelection get() = selStart != selEnd

    // Eigenschaften des aktuellen Eingabefelds (z. B. die gewünschte Return-Action),
    // vom Service in onStartInput gesetzt.
    private var field = FieldContext()

    // Wurde der aktuelle SHIFTED-Zustand von der Auto-Großschreibung gesetzt (nicht
    // vom Nutzer)? Dann hebt ein Shift-Tap ihn direkt auf (→ OFF), statt erst über
    // CAPS zu zyklen — sonst bräuchte man zwei Taps mit Caps-Lock dazwischen, um die
    // automatische Armierung wieder loszuwerden.
    private var autoCapArmed = false

    fun applySettings(newSettings: Settings) {
        settings = newSettings
        if (_state.value.colorHints != newSettings.letterColorHintsEnabled) {
            _state.value = _state.value.copy(colorHints = newSettings.letterColorHintsEnabled)
        }
        // Nur Vorschläge neu bewerten — eine Settings-Änderung bewegt den Cursor nicht, also
        // KEIN Auto-Cap (sonst armierte ein blosser Toggle den Satzanfang neu). Gated intern.
        refreshForCursor(reconsiderAutoCap = false)
    }

    /**
     * Neues Eingabefeld beginnt: Feld-Eigenschaften übernehmen, passende Ebene wählen
     * (Ziffernfeld → Symbolebene), Shift auf einen definierten Stand bringen und Auto-Cap
     * neu bestimmen. Die Anfangs-Auswahl kommt aus dem Feld, damit der selektionsbewusste
     * Backspace nicht erst aufs erste `onUpdateSelection` warten muss.
     */
    fun onStartInput(
        field: FieldContext = FieldContext(),
        pasteAvailable: Boolean = false,
        restarting: Boolean = false,
    ) {
        this.field = field
        selStart = field.initialSelStart
        selEnd = field.initialSelEnd
        val layout = when {
            field.phone -> phoneLayout // eigenes Wählfeld
            field.numeric -> symbolsLayout // Zahl/Datum → allgemeine Ziffern-/Symbolebene
            else -> alphaLayout
        }
        if (restarting) {
            // Reiner RESTART desselben Feldes (z. B. Config-Change, View neu aufgebaut): nur
            // Ebene/Einfügen-Zustand übernehmen, den vom Nutzer gesetzten Shift NICHT wegwerfen
            // und KEIN Auto-Cap neu rechnen (sonst entwaffnete ein Restart ein manuelles SHIFTED).
            _state.value = _state.value.copy(layout = layout, pasteAvailable = pasteAvailable)
            refreshForCursor(reconsiderAutoCap = false)
        } else {
            // ECHTER Feldwechsel: mit definiertem Shift (OFF) beginnen — ein vom letzten Feld
            // übrig gebliebenes CAPS-Lock soll nicht in ein neues, fremdes Feld lecken — und
            // Ebene/Einfügen/Shift in derselben Emission setzen. refreshForCursor armiert danach
            // ggf. wieder SHIFTED; computeAutoCapShift lässt Passwort-/Zahlenfelder selbst aus.
            autoCapArmed = false
            _state.value = _state.value.copy(
                layout = layout,
                pasteAvailable = pasteAvailable,
                shift = ShiftState.OFF,
            )
            refreshForCursor(reconsiderAutoCap = true)
        }
    }

    /**
     * Cursor oder Auswahl haben sich geändert (Service-Callback `onUpdateSelection`).
     * Hält Auto-Cap und Vorschläge zum neuen Cursor konsistent — sonst blieben sie
     * stehen, wenn der Nutzer mitten in den Text tippt — und merkt sich eine aktive
     * Auswahl für den selektionsbewussten Backspace.
     */
    fun onSelectionChanged(newSelStart: Int, newSelEnd: Int) {
        // Unveränderter Callback (Editoren melden `onUpdateSelection` auch mal ohne echte
        // Bewegung, z. B. beim Aktualisieren der Kandidatenregion) → nichts neu zu rechnen.
        if (newSelStart == selStart && newSelEnd == selEnd) return
        selStart = newSelStart
        selEnd = newSelEnd
        // Ein Cursor-/Auswahl-Sprung: Vorschläge und Auto-Cap aus einem Read, eine Emission.
        refreshForCursor(reconsiderAutoCap = true)
    }

    fun onKey(key: KeyboardKey) {
        when (key) {
            is CharKey -> onChar(key)
            is FunctionKey -> onFunction(key.action)
        }
    }

    private fun onChar(key: CharKey) = commitWithShift(key.output)

    /**
     * Eine im Long-Press-Popup gewählte Alternative committen (z. B. „sch", „é", das
     * einzelne „q"). Läuft durch dieselbe Shift-Casing-Logik wie ein normaler Tap —
     * kein Autocorrect, committet genau das Gewählte.
     */
    fun onAlternative(text: String) = commitWithShift(text)

    /** Committet [text] mit Shift-Casing: SHIFTED grossschreibt nur den ersten
     *  Buchstaben („qu"→„Qu", „sch"→„Sch"), CAPS alles. */
    private fun commitWithShift(text: String) {
        val shift = _state.value.shift
        val out = shift.applyTo(text)
        safeIc { it.commitText(out, 1) }
        // SHIFTED gilt „nur für den nächsten Buchstaben". Eine Ziffer/ein Symbol (etwa auf
        // der ?123-Ebene) wird von applyTo ohnehin nicht verändert und soll eine armierte
        // Grossschreibung NICHT verbrauchen — sonst ginge die Auto-Cap-Armierung verloren,
        // sobald am Satzanfang erst eine Ziffer kommt. CAPS bleibt ohnehin stehen.
        if (shift == ShiftState.SHIFTED && text.firstOrNull()?.isLetter() == true) {
            setShift(ShiftState.OFF)
        }
        afterTextChanged()
    }

    private fun onFunction(action: KeyAction) {
        when (action) {
            KeyAction.SHIFT -> cycleShift()
            KeyAction.BACKSPACE -> {
                // Bei aktiver Auswahl diese löschen — commitText("") ersetzt die
                // Selektion durch nichts. Ein Lösch-vor-Cursor würde eine Auswahl
                // NICHT anfassen, sondern stattdessen das Zeichen davor löschen.
                if (hasSelection) {
                    safeIc { it.commitText("", 1) }
                } else {
                    // Code-Point-basiert (nicht deleteSurroundingText, das UTF-16-Code-Units
                    // löscht): so wird ein eingefügtes/vorhandenes astrales Zeichen (Emoji,
                    // seltene Schrift) als Ganzes entfernt statt in eine kaputte Surrogat-
                    // Hälfte zerlegt. Tippen kann nah selbst keine Emoji, Einfügen aber schon.
                    safeIc { it.deleteSurroundingTextInCodePoints(1, 0) }
                }
                afterTextChanged()
            }
            KeyAction.SPACE -> { commit(" "); afterTextChanged() }
            KeyAction.PERIOD -> { commit("."); afterTextChanged() }
            KeyAction.COMMA -> { commit(","); afterTextChanged() }
            KeyAction.PASTE -> {
                // Zwischenablage-Text wörtlich einfügen — kein Shift-Casing, kein Autocorrect.
                val text = clipboardTextProvider()
                if (!text.isNullOrEmpty()) {
                    safeIc { it.commitText(text, 1) }
                    afterTextChanged()
                }
            }
            KeyAction.RETURN -> {
                // Verlangt das Feld eine Editor-Action (Suchen/Senden/Los/Weiter),
                // diese auslösen statt blind ein Enter zu schicken — sonst landet in
                // einem Such-/Sendefeld ein Zeilenumbruch statt des Absendens. Ohne
                // angeforderte Action bleibt es ein echtes Enter (mehrzeilige Felder).
                val action = field.imeAction
                if (action != null) {
                    safeIc { it.performEditorAction(action) }
                } else {
                    safeIc {
                        it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    }
                }
                afterTextChanged()
            }
            KeyAction.SYMBOLS -> {
                // Vorschläge laufen auch hier weiter (Ziffern liegen auf dieser Ebene —
                // so wird z. B. eine eigene PLZ vorgeschlagen). Die reservierte Leiste
                // bleibt auf beiden Ebenen, damit die Höhe beim Wechsel nicht springt.
                _state.value = _state.value.copy(layout = symbolsLayout)
                refreshForCursor(reconsiderAutoCap = false) // Ebenenwechsel bewegt den Cursor nicht
            }
            KeyAction.ALPHA -> {
                _state.value = _state.value.copy(layout = alphaLayout)
                refreshForCursor(reconsiderAutoCap = false)
            }
        }
    }

    /**
     * Die Zwischenablage hat sich geändert (Service-Listener `OnPrimaryClipChangedListener`).
     * Schaltet die Einfügen-Taste live aktiv/inaktiv — auch während die Tastatur offen ist,
     * nicht erst beim nächsten Feldwechsel. Liest NUR die Metadaten (kein Inhalt → kein
     * „Zwischenablage gelesen"-Toast); der Service reicht das Ergebnis herein.
     */
    fun onPasteAvailabilityChanged(available: Boolean) {
        if (_state.value.pasteAvailable != available) {
            _state.value = _state.value.copy(pasteAvailable = available)
        }
    }

    /** Vorschlag angetippt: ersetzt NUR das aktuelle unfertige Präfix, nie fertigen Text. */
    fun onSuggestionTap(word: String) {
        val prefix = currentWord()
        // Eigene Wörter (Namen/Adressen/E-Mails) wörtlich committen — ihre Schreibweise ist
        // massgeblich. Nur Wörterbuch-Vorschläge dem Präfix-Casing anpassen, damit ein am
        // Satzanfang gross begonnenes „De" nicht durch klein vorgeschlagenes „der" ersetzt
        // wird. Kein Autocorrect: in beiden Fällen wird nur das unfertige Präfix ersetzt.
        // userWordsEnabled mitprüfen: der User-Trie wird IMMER vorgehalten (siehe NahIme), also
        // meldet isUserWord auch bei abgeschalteter Funktion noch Treffer. Ohne diese Gate würde
        // ein Wort, das zufällig in beiden Listen steht, bei AUS­geschalteten eigenen Wörtern
        // fälschlich wörtlich statt gecast committet (z. B. „Zeit" statt „ZEIT" unter Caps-Lock).
        val isUserWord = settings.userWordsEnabled && suggester?.isUserWord(word) == true
        val out = if (isUserWord) word else casedLikePrefix(word, prefix)
        safeIc { ic ->
            if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
            ic.commitText("$out ", 1)
        }
        selEnd = selStart // nach dem Eigen-Edit gibt es keine aktive Auswahl mehr
        setShift(ShiftState.OFF)
        clearSuggestions()
        recomputeAutoCap()
    }

    /**
     * Überträgt die Gross-/Kleinschreibung des ersetzten [prefix] auf [word], ohne dessen
     * Eigen-Schreibweise zu zerstören: ein gross begonnenes Präfix (Satzanfang/Auto-Cap)
     * macht den ersten Buchstaben gross, ein komplett gross getipptes (Caps-Lock) den ganzen
     * Vorschlag. Ein klein getipptes Präfix lässt das Wort, wie es in der Liste steht (z. B.
     * das Nomen „Zeit"). Locale-unabhängig (ROOT), konsistent zu [ShiftState.applyTo].
     */
    private fun casedLikePrefix(word: String, prefix: String): String {
        val letters = prefix.filter { it.isLetter() }
        return when {
            letters.length >= 2 && letters.all { it.isUpperCase() } -> word.uppercase(Locale.ROOT)
            letters.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }

    // --- intern ---

    private fun commit(text: String) {
        safeIc { it.commitText(text, 1) }
    }

    private fun cycleShift() {
        val next = when (_state.value.shift) {
            ShiftState.OFF -> ShiftState.SHIFTED
            // Auto-armiert → der Tap will entwaffnen (OFF); manuell armiert → weiter
            // zu Caps-Lock. So bleibt der manuelle Zyklus OFF→SHIFTED→CAPS→OFF erhalten.
            ShiftState.SHIFTED -> if (autoCapArmed) ShiftState.OFF else ShiftState.CAPS
            ShiftState.CAPS -> ShiftState.OFF
        }
        autoCapArmed = false // ein Shift-Tap ist immer manuell
        setShift(next)
    }

    private fun setShift(s: ShiftState) {
        if (_state.value.shift != s) _state.value = _state.value.copy(shift = s)
    }

    private fun afterTextChanged() {
        // Ein Eigen-Edit kollabiert eine evtl. aktive Auswahl (Commit ersetzt sie,
        // Backspace löscht sie). Bis das `onUpdateSelection`-Echo die exakte Position
        // nachreicht, lokal als „keine Auswahl" markieren — sonst liefe ein sofort
        // folgendes Backspace fälschlich in den Auswahl-Lösch-Pfad und liesse das gerade
        // getippte Zeichen stehen. Die genaue Cursorzahl korrigiert gleich das Echo.
        selEnd = selStart
        // Synchron direkt nach dem eigenen Edit, damit Shift/Vorschläge ohne sichtbare
        // Verzögerung stehen. Das vom Commit ausgelöste `onUpdateSelection`-Echo ruft
        // dieselbe Logik gleich nochmal auf — das ist gewollt (es deckt auch externe
        // Cursor-Sprünge ab) und idempotent: `copy` ist ein No-op bei Gleichstand.
        // Bewusst NICHT per Cursor-Vorhersage entdoppelt — eine falsche Vorhersage würde ein
        // nötiges Recompute überspringen und falsch grossschreiben (schlechter Tausch).
        refreshForCursor(reconsiderAutoCap = true)
    }

    private fun clearSuggestions() {
        if (_state.value.suggestions.isNotEmpty()) {
            _state.value = _state.value.copy(suggestions = emptyList())
        }
    }

    /**
     * Liest den Editor-Kontext **vor** dem Cursor **einmal** und frischt daraus Vorschläge UND
     * (bei [reconsiderAutoCap]) die Auto-Cap-Shift-Lage in **einer** [_state]-Emission auf. So
     * kostet ein Cursor-/Tipp-Schritt nur einen `getTextBeforeCursor`-Roundtrip (statt je einen
     * für Vorschläge und Auto-Cap) und löst nur eine Recomposition aus.
     *
     * Der eine Read deckt nur den Kontext **vor** dem Cursor ab. Liegt ein Vorschlags-Präfix an,
     * liest [computeSuggestions] über [atWordEnd] zusätzlich **einmal hinter** den Cursor — ein
     * eigener, nötiger Read (anderer Bereich), kein doppelter Vor-Cursor-Read.
     *
     * [reconsiderAutoCap] = `false` lässt den Shift unangetastet — für Schritte, die den Cursor
     * NICHT bewegen (Settings-/Ebenenwechsel, Field-Restart): dort soll ein manuell gesetztes
     * SHIFTED/CAPS nicht durch einen Satzanfang-Check umgeworfen werden.
     */
    private fun refreshForCursor(reconsiderAutoCap: Boolean) {
        val before = readContextBeforeCursor()
        val (suggestions, barVisible) = computeSuggestions(before)
        // null = unverändert lassen (Feature aus, Spezialfeld, manuelles Shift, fehlende IC).
        val autoCapShift = if (reconsiderAutoCap) computeAutoCapShift(before) else null
        // Ein nicht-null Ergebnis armiert genau dann, wenn es SHIFTED ist (siehe
        // computeAutoCapShift); null lässt die Armierung unangetastet.
        if (autoCapShift != null) autoCapArmed = autoCapShift == ShiftState.SHIFTED
        _state.value = _state.value.copy(
            suggestions = suggestions,
            suggestionBarVisible = barVisible,
            shift = autoCapShift ?: _state.value.shift,
        )
    }

    /** Vorschläge + ob die Leiste Platz reserviert, aus dem bereits gelesenen [before]. */
    private fun computeSuggestions(before: String?): Pair<List<String>, Boolean> {
        val s = suggester
        val anySource = settings.suggestionsEnabled || settings.userWordsEnabled
        // Funktion ganz aus oder Passwortfeld → gar keine Leiste (kein verschwendeter Platz,
        // kein Schulter-Surfen über der Passworteingabe).
        if (s == null || !anySource || field.isPassword) return emptyList<String>() to false
        // Ab hier ist die Leiste reserviert (feste Höhe), auch ohne aktuelle Vorschläge.
        // Bei einer aktiven Auswahl keine Vorschläge: onSuggestionTap löscht nur das Präfix
        // vor dem Cursor und committet darüber — bei bestehender Selektion würde derselbe Tap
        // zusätzlich die markierte Stelle ersetzen (commitText überschreibt die Auswahl) und so
        // fertigen Text ungewollt anfassen. Die Leiste bleibt reserviert, nur leer.
        val prefix = wordBeforeCursor(before)
        val list = if (prefix.length >= 2 && !hasSelection && atWordEnd()) {
            s.suggest(prefix.lowercase(), settings.suggestionsEnabled, settings.userWordsEnabled)
        } else {
            emptyList()
        }
        return list to true
    }

    /**
     * Steht direkt hinter dem Cursor noch ein Buchstabe, sind wir mitten im Wort —
     * dort werden keine Vorschläge angeboten: ein Antippen würde nur das Präfix vor
     * dem Cursor ersetzen (siehe [onSuggestionTap]) und so das Wortende zerstückeln.
     */
    private fun atWordEnd(): Boolean {
        val after = safeIc { it.getTextAfterCursor(1, 0)?.toString() } ?: return true
        return after.isEmpty() || !after[0].isLetterOrDigit()
    }

    /**
     * Neuer Shift-Zustand aus dem Satzkontext [before], oder `null` = unverändert lassen.
     * **Reine Funktion** (liest nur Settings/Feld/aktuellen Shift, mutiert nichts): das
     * Armieren von [autoCapArmed] machen die Aufrufer aus dem Rückgabewert — ein nicht-`null`
     * Ergebnis ist armiert genau dann, wenn es [ShiftState.SHIFTED] ist (siehe [refreshForCursor]
     * /[recomputeAutoCap]). Die Guards subsumieren bewusst die alte `shift == OFF || autoCapArmed`-
     * Vorprüfung aus [afterTextChanged]: ein manuelles SHIFTED und ein CAPS-Lock liefern hier
     * ohnehin `null` (Aufrufer lassen [autoCapArmed] dann unangetastet), ein unverbrauchtes
     * Auto-SHIFTED rechnet weiter.
     */
    private fun computeAutoCapShift(before: String?): ShiftState? {
        if (!settings.autoCapEnabled) return null
        if (field.isPassword) return null // case-sensitive: nie automatisch grossschreiben
        if (field.numeric) return null // Zahl-/Telefonfeld kennt keinen „Satzanfang"
        val shift = _state.value.shift
        if (shift == ShiftState.CAPS) return null
        // Ein manuell gesetztes SHIFTED (nicht von Auto-Cap) nicht durch einen Cursor-/
        // Selektions-Callback überschreiben — der Nutzer will den nächsten Buchstaben gross.
        if (shift == ShiftState.SHIFTED && !autoCapArmed) return null
        // Fehlt die InputConnection (before == null), den Shift-Zustand UNVERÄNDERT lassen —
        // nicht ein fehlendes Ergebnis als leeren Satzanfang werten und fälschlich
        // kapitalisieren. Ein wirklich leeres Feld liefert "" (nicht null) und armiert korrekt.
        if (before == null) return null
        // Schliessende „transparente" Satzzeichen (öffnende Klammern/Anführung) und Whitespace
        // vom Ende her überspringen, dann auf ein Satzende prüfen: so bleibt nach „Satz. («"
        // grossgeschrieben, aber eine Dezimalzahl wie „3.14" armiert NICHT (Ziffern sind nicht
        // transparent → letztes bedeutungstragendes Zeichen ist „4", kein Satzende).
        val meaningful = before.trimEnd { it.isWhitespace() || it in TRANSPARENT_PUNCT }
        val shouldCap = meaningful.isEmpty() || meaningful.last() in SENTENCE_ENDERS
        return if (shouldCap) ShiftState.SHIFTED else ShiftState.OFF
    }

    /** Auto-Cap einzeln neu rechnen (nach einem Vorschlag-Commit, der den Cursor bewegt hat).
     *  Liest den Kontext selbst — kein gemeinsamer Read mit Vorschlägen nötig, da [onSuggestionTap]
     *  die Vorschläge ohnehin separat leert. */
    private fun recomputeAutoCap() {
        val shift = computeAutoCapShift(readContextBeforeCursor()) ?: return
        autoCapArmed = shift == ShiftState.SHIFTED
        setShift(shift)
    }

    private fun readContextBeforeCursor(): String? =
        safeIc { it.getTextBeforeCursor(CONTEXT_LOOKBEHIND, 0)?.toString() }

    private fun currentWord(): String = wordBeforeCursor(readContextBeforeCursor())

    /** Das Token direkt vor dem Cursor aus [before]: Buchstaben UND Ziffern, damit auch ein
     *  numerisches Token (z. B. eine eigene PLZ) als Präfix erkannt und vorgeschlagen wird. */
    private fun wordBeforeCursor(before: String?): String =
        (before ?: "").takeLastWhile { it.isLetterOrDigit() }

    private inline fun <T> safeIc(block: (InputConnection) -> T): T? {
        val ic = inputConnectionProvider() ?: return null
        return try {
            block(ic)
        } catch (e: Exception) {
            Log.w(TAG, "InputConnection-Aufruf fehlgeschlagen: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "NahIme"
        /** Wie viele Zeichen vor dem Cursor pro Schritt gelesen werden — deckt sowohl den
         *  Satzanfang-Scan (Auto-Cap) als auch das Vorschlags-Präfix mit einem Read ab. */
        const val CONTEXT_LOOKBEHIND = 64
        val SENTENCE_ENDERS = setOf('.', '!', '?')
        /**
         * Beim Suchen nach dem Satzende vom Cursor rückwärts überspringbar — sie
         * unterbrechen einen Satzanfang nicht. Zwei Sorten:
         *  - **öffnende** Klammern/Anführung (inkl. de-CH-Guillemets): beginnen einen
         *    (zitierten) Satz, „. «Geh" soll gross weitergehen;
         *  - **schliessende** Klammern/Anführung: stehen NACH dem Satzende-Zeichen
         *    („Satz.» “, „(Hallo.)"), dürfen den vorausgehenden Punkt also nicht verdecken.
         * Die geraden `"`/`'` sind ambig (öffnend wie schliessend) und ohnehin transparent.
         * Bewusst KEINE Ziffern/Buchstaben (sonst armierte „3.14").
         */
        val TRANSPARENT_PUNCT = setOf(
            '(', '[', '{', '«', '„', '‚', '‘', '“', // öffnend
            ')', ']', '}', '»', '’', '”', // schliessend
            '"', '\'', // gerade (ambig)
        )
    }
}
