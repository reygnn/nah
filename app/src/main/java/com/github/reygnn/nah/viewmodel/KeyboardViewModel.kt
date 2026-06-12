package com.github.reygnn.nah.viewmodel

import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Immutable
import com.github.reygnn.nah.data.suggestions.Suggester
import com.github.reygnn.nah.data.suggestions.UserWordValidation
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

/**
 * `@Immutable`: nur `val`s, neue Instanz je Emission — ehrlich und harmlos. Bringt kein Tempo
 * (der State wechselt per Definition bei jeder Emission, `KeyboardContent` rekomponiert ohnehin),
 * steht nur der Konsistenz halber neben den `@Immutable`-Layout-Typen. Das einzige formal instabile
 * Feld ([suggestions]: `List`) lässt sich nicht annotieren; relevant nur für die `SuggestionBar`.
 */
@Immutable
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
    /** Das aktuell getippte, noch nicht gespeicherte Wort, das als „speichern"-Chip angeboten wird
     *  (`null` = gerade keins). Folgt **live** dem getippten Wort, solange der Cursor am Wortende steht.
     *  Antippen legt es in die eigene, backuppbare Wortliste — **kein Eingriff in fertigen Text**
     *  (siehe [KeyboardViewModel.onSaveWordTap]). */
    val saveWord: String? = null,
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
    private val numberLayout: KeyboardLayout,
    private val phoneLayout: KeyboardLayout,
    private val inputConnectionProvider: () -> InputConnection?,
    private val suggester: Suggester? = null,
    /** Fordert das Einfügen aus der Zwischenablage an. Der Service löst den (potenziell
     *  langsamen, weil URI-auflösenden) Inhalt-Zugriff asynchron AUSSERHALB des UI-Threads
     *  auf und reicht das Ergebnis über [commitClipboardText] zurück — so blockiert ein
     *  Einfüge-Tap nie den Main-Thread (kein ANR). Inhalt-Zugriff weiterhin NUR bei dieser
     *  expliziten Nutzeraktion, nicht laufend. */
    private val onPasteRequested: () -> Unit = {},
    /** Öffnet die App-Einstellungen (per Long-Press auf der Ebenen-Umschalttaste, siehe
     *  [KeyAction.SETTINGS]). Der Service startet die Activity; der ViewModel kennt kein Android. */
    private val onSettingsRequested: () -> Unit = {},
    /** Speichert das angetippte Wort als **gelerntes** Wort. Der Service persistiert es off-state über
     *  `LearnedWordRepository` (NICHT den kuratierten `UserWordRepository` — gelernte Wörter werden wie
     *  Wörterbuch-Wörter an Shift/Caps angepasst, nicht wörtlich committet) und bestätigt per Toast; der
     *  ViewModel kennt weder DataStore noch Android (siehe [onSaveWordTap]). */
    private val onSaveWordRequested: (String) -> Unit = {},
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

    // Einmal-Quittung gegen das redundante Selbst-Echo: nach einem eigenen Edit mit vorhersagbarer
    // Länge die kollabierte Cursorposition, die der Editor per `onUpdateSelection` zurückmelden wird.
    // [afterTextChanged] hat synchron (gegen den realen Post-Edit-Kontext, derselbe verlässliche Read,
    // auf den sich der ViewModel ohnehin stützt) schon Shift/Vorschläge gerechnet — das passende Echo
    // trägt nichts Neues und sein Recompute wird übersprungen. Ein NICHT passendes Echo ist eine
    // externe Cursor-Bewegung und rechnet normal weiter. Die Vorhersage dient AUSSCHLIESSLICH diesem
    // Dedup, nie dem Überspringen des synchronen Reads — eine falsche Vorhersage kann darum kein
    // nötiges Recompute verlieren (sie fällt in den Recompute-Zweig). `null` = keine Quittung scharf
    // (Pfade ohne sichere Längen-Vorhersage wie Backspace mit astralen Codepoints, oder Return).
    private var pendingSelfEcho: Int? = null

    // Eigenschaften des aktuellen Eingabefelds (z. B. die gewünschte Return-Action),
    // vom Service in onStartInput gesetzt.
    private var field = FieldContext()

    // Wurde der aktuelle SHIFTED-Zustand von der Auto-Großschreibung gesetzt (nicht
    // vom Nutzer)? Dann hebt ein Shift-Tap ihn direkt auf (→ OFF), statt erst über
    // CAPS zu zyklen — sonst bräuchte man zwei Taps mit Caps-Lock dazwischen, um die
    // automatische Armierung wieder loszuwerden.
    private var autoCapArmed = false

    // Hat der Nutzer ein AUTO-armiertes SHIFTED gerade per Shift-Tap bewusst entwaffnet? Dann
    // soll ein blosser Cursor-/Selektions-Callback (der den Satzanfang erneut „sähe") Auto-Cap
    // an dieser Stelle NICHT wieder anwerfen — der Nutzerwille zählt. Gilt nur bis zur nächsten
    // echten Aktion (Tippen/Edit/Feldwechsel), die den Merker zurücksetzt; ein dann erreichter
    // neuer Satzanfang armiert wieder normal.
    private var userDisarmedAutoCap = false

    fun applySettings(newSettings: Settings) {
        settings = newSettings
        // Auto-Cap gerade ausgeschaltet, während noch ein AUTO-armiertes SHIFTED steht: dieses eine
        // armierte Zeichen sofort entwaffnen, statt es noch grossschreiben zu lassen. Bewusst nur
        // dieser eine Übergang — ein MANUELL gesetztes SHIFTED oder CAPS hat autoCapArmed == false und
        // bleibt unberührt, und es wird NICHTS neu armiert (anders als ein voller reconsiderAutoCap,
        // der einen vom Nutzer bewusst entwaffneten Satzanfang wieder anwerfen würde — genau darum
        // bleibt der Refresh unten reconsiderAutoCap = false). Der shift-Check ist redundant zur
        // Invariante „autoCapArmed ⟹ SHIFTED", aber defensiv, falls die je bricht.
        if (!newSettings.autoCapEnabled && autoCapArmed && _state.value.shift == ShiftState.SHIFTED) {
            autoCapArmed = false
            setShift(ShiftState.OFF)
        }
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
        // Eine offene Selbst-Echo-Quittung gehört nicht über eine Feld-/Restart-Grenze getragen —
        // das initialSel*-getriebene Echo des neuen Feldes soll normal rechnen, nicht übersprungen werden.
        pendingSelfEcho = null
        // Reihenfolge ist load-bearing: ein Telefonfeld ist BEIDES (field.phone UND field.numeric,
        // siehe FieldContext) — phone MUSS zuerst geprüft werden, sonst bekäme es das allgemeine
        // Zahlen-Pad statt des Wählfelds. Nicht umsortieren.
        val layout = when {
            field.phone -> phoneLayout // eigenes Wählfeld (Telefon: * # +)
            field.numeric -> numberLayout // Zahl/PIN/Datum → Grosstasten-Ziffern-Pad (, . -)
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
            userDisarmedAutoCap = false // neues Feld → ein Entwaffnen aus dem alten Feld gilt nicht mehr
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
        // Eine etwaige Selbst-Echo-Quittung dabei trotzdem verfallen lassen: sie gehörte zu
        // unserem letzten Edit; bliebe sie über diesen Callback hinweg scharf, könnte ein
        // späteres externes Ereignis, das zufällig auf der alten Vorhersage landet, fälschlich
        // als eigenes Echo entdoppelt und sein nötiges Recompute übersprungen werden.
        if (newSelStart == selStart && newSelEnd == selEnd) {
            pendingSelfEcho = null
            return
        }
        selStart = newSelStart
        selEnd = newSelEnd
        // Unser eigener, gerade committeter Edit echot exakt die vorhergesagte (kollabierte)
        // Position zurück; afterTextChanged hat dafür schon synchron gerechnet → die autoritative
        // Position ist oben übernommen, der zweite Read wird gespart. Die Quittung gilt nur einmal
        // (Einmal-Verbrauch), eine danach eintreffende externe Bewegung rechnet wieder normal.
        val expected = pendingSelfEcho
        pendingSelfEcho = null
        if (expected != null && newSelStart == expected && newSelEnd == expected) return
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

    /**
     * Das als „speichern"-Chip angebotene Wort wurde angetippt: in die gelernten Wörter (eigener,
     * backuppbarer Store) legen. **Fasst KEINEN Text an** — anders als [onSuggestionTap] wird hier nichts ersetzt oder
     * committet, das oberste Gesetz bleibt also unberührt. Der Service persistiert über den Callback
     * (off-state) und bestätigt per Toast.
     *
     * Das Chip sofort entfernen: ein Speichern bewegt weder Cursor noch Text, es käme also kein Refresh,
     * der es wegräumt. Das Wegnehmen hier verhindert zugleich, dass ein Doppel-Tap dasselbe Wort zweimal
     * zu speichern versucht.
     */
    fun onSaveWordTap(word: String) {
        if (word.isEmpty()) return
        onSaveWordRequested(word)
        if (_state.value.saveWord != null) _state.value = _state.value.copy(saveWord = null)
    }

    /** Committet [text] mit Shift-Casing: SHIFTED grossschreibt nur den ersten
     *  Buchstaben („qu"→„Qu", „sch"→„Sch"), CAPS alles. */
    private fun commitWithShift(text: String) {
        val shift = _state.value.shift
        val out = shift.applyTo(text)
        safeIc { it.commitText(out, 1) }
        // SHIFTED gilt „nur für den nächsten Buchstaben". Eine Ziffer/ein Symbol (etwa auf
        // der SYM-Ebene) wird von applyTo ohnehin nicht verändert und darf ein armiertes
        // SHIFTED NICHT verbrauchen — so überlebt ein MANUELL gesetztes Shift eine
        // dazwischengetippte Ziffer und schreibt den darauffolgenden Buchstaben noch gross
        // („5" dann „h" → „5H"). Genau das ist der Sinn dieses Guards. Ein AUTO-armiertes
        // SHIFTED wird dagegen vom folgenden refreshForCursor neu aus dem Kontext bewertet
        // und nach einer Ziffer korrekt entwaffnet (die Ziffer hat den Satzanfang belegt; ein
        // Punkt/!/? würde neu armieren) — gewollt, nicht durch diesen Guard verhindert. CAPS
        // bleibt ohnehin stehen (der Guard betrifft nur SHIFTED).
        if (shift == ShiftState.SHIFTED && text.firstOrNull()?.isLetter() == true) {
            setShift(ShiftState.OFF)
        }
        // Vorhergesagte Cursorposition: commitText ersetzt [selStart, selEnd) durch [out], der Cursor
        // landet kollabiert bei selStart + out.length — unabhängig davon, ob eine Auswahl bestand.
        // `out.length` (nicht text.length), weil CAPS „qu"→„QU" usw.; applyTo ist für das de-CH-Alphabet
        // längenerhaltend. Bei einer Reverse-Selektion träfe die Vorhersage nicht → sicherer Fallback.
        afterTextChanged(predictedCursor = selStart + out.length)
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
            KeyAction.SPACE -> commitLiteral(" ")
            KeyAction.PERIOD -> commitLiteral(".")
            KeyAction.COMMA -> commitLiteral(",")
            // Per Long-Press auf der Punkt-Taste (Buchstabenebene) erreichbar. Wie der Punkt:
            // committen das Zeichen wörtlich und armieren danach Auto-Cap (? und ! sind Satzenden).
            KeyAction.QUESTION -> commitLiteral("?")
            KeyAction.EXCLAMATION -> commitLiteral("!")
            // Einfügen anfordern — der Service löst den Inhalt off-main auf und committet
            // ihn über commitClipboardText (kein UI-Thread-Block, siehe onPasteRequested).
            KeyAction.PASTE -> onPasteRequested()
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
            // Per Long-Press auf der Umschalttaste erreichbar (siehe OptimizedLayout.functionRow):
            // zurück aufs Grosstasten-Pad. Teil des Voll-Mesh — aus einem Zahl-/Telefonfeld führt
            // ABC ins Alphabet, von dort kommt man so wieder aufs grosse Pad statt nur auf die
            // schmale Symbol-Ziffernreihe. Wie die übrigen Ebenenwechsel bewegt das den Cursor
            // nicht → kein Auto-Cap-Recompute.
            KeyAction.NUMPAD -> {
                _state.value = _state.value.copy(layout = numberLayout)
                refreshForCursor(reconsiderAutoCap = false)
            }
            KeyAction.DIALPAD -> {
                _state.value = _state.value.copy(layout = phoneLayout)
                refreshForCursor(reconsiderAutoCap = false)
            }
            // Per Long-Press auf der Umschalttaste jeder Ebene erreichbar — öffnet die Einstellungen
            // über den Service-Callback (kein Android-Wissen im ViewModel). Berührt weder Text noch
            // Cursor: kein Commit, kein Auto-Cap-Recompute.
            KeyAction.SETTINGS -> onSettingsRequested()
        }
    }

    /**
     * Die Zwischenablage hat sich geändert (Service-Listener `OnPrimaryClipChangedListener`).
     * Schaltet die Einfügen-Taste aktiv/inaktiv, ohne auf den nächsten Feldwechsel zu warten.
     * **Best-Effort:** der Service-Listener feuert nur, solange die Tastatur fokussiert ist
     * (Android schränkt Clipboard-Callbacks für nicht-fokussierte Apps ein) — der verlässliche
     * Stand kommt aus `onStartInput`. Liest NUR die Metadaten (kein Inhalt → kein
     * „Zwischenablage gelesen"-Toast); der Service reicht das Ergebnis herein.
     */
    fun onPasteAvailabilityChanged(available: Boolean) {
        if (_state.value.pasteAvailable != available) {
            _state.value = _state.value.copy(pasteAvailable = available)
        }
    }

    /**
     * Committet den (vom Service asynchron aufgelösten) Zwischenablage-Text **wörtlich** —
     * kein Shift-Casing, kein Autocorrect. Gegenstück zu [onPasteRequested]: der Service löst
     * den Inhalt off-main auf und ruft dies auf dem Main-Thread auf. Leerer/`null` Text →
     * No-op. [afterTextChanged] frischt danach Auto-Cap/Vorschläge zum neuen Cursor auf.
     */
    fun commitClipboardText(text: String?) {
        if (text.isNullOrEmpty()) return
        safeIc { it.commitText(text, 1) }
        afterTextChanged(predictedCursor = selStart + text.length)
    }

    /** Vorschlag angetippt: ersetzt NUR das aktuelle unfertige Präfix, nie fertigen Text. */
    fun onSuggestionTap(word: String) {
        val prefix = currentWord()
        // Schutz des obersten Gesetzes (fertiger Text wird NIE verändert) gegen eine Live-Auswahl,
        // die der ViewModel noch nicht kennt (das onUpdateSelection-Echo ist unterwegs, der Vorschlag-
        // Chip steht aber noch): der commitText unten würde die markierte Stelle ERSETZEN und damit
        // fertigen Text zerstören. Zwei Abbruchgründe:
        //  - leeres Präfix: ein gültiger Vorschlag hatte immer ein >= 2 Zeichen langes Präfix; ist es
        //    jetzt leer, hat sich der Kontext geändert (eine Auswahl verdeckt den Wortanfang → wordBefore
        //    liefert ""), also nichts anfassen.
        //  - der Editor meldet real noch markierten Text (getSelectedText ist autoritativ und deckt auch
        //    Auswahlen ab, die nicht am Wortanfang beginnen — dort ist das Präfix nicht leer). Bewusst
        //    getSelectedText und NICHT getExtractedText: getExtractedText marshallt den GANZEN Feldinhalt
        //    über den Binder (die hintMaxChars sind nur ein Hinweis, den die Standard-TextView ignoriert),
        //    nur um eine Auswahl zu erkennen — getSelectedText liefert genau den (kurzen) markierten Text,
        //    derselbe Schutz ohne den unbegrenzten Read. Null/leer = keine Auswahl ODER vom Editor nicht
        //    unterstützt; dann trägt der Präfix-Check oben.
        if (prefix.isEmpty()) {
            clearSuggestions()
            return
        }
        if (!safeIc { it.getSelectedText(0) }.isNullOrEmpty()) {
            clearSuggestions()
            return
        }
        // Dritter Abbruchgrund, gegen denselben Eingriff in fertigen Text aus einem anderen Eckfall:
        // ein Vorschlag wird nur am Wortende angeboten (siehe [computeSuggestions]/[atWordEnd]), aber
        // diese Prüfung läuft beim BERECHNEN. Steht der Cursor zum TAP-Zeitpunkt mitten im Wort, muss
        // die Vorschlagsliste stale sein — ein übersprungener Recompute ist die einzige Quelle: nach
        // einem eigenen Edit ist [selStart] bis zum `onUpdateSelection`-Echo stale, und trifft ein
        // externer Cursor-Callback (oder ein vom Editor coalesctes Netto-Echo) genau diese stale
        // Position, greift der no-change-Guard in [onSelectionChanged] und überspringt das
        // Neurechnen — die am Wortende berechneten Vorschläge bleiben stehen. Ohne diesen Re-Check
        // trennte der deleteSurroundingText+commitText unten den Wortrest HINTER dem Cursor ab
        // („hal" + Cursor bei „ha|l" → „hallo l"): fertiger Text, Bruch des obersten Gesetzes.
        // [atWordEnd] liest die reale InputConnection zum Tap-Zeitpunkt (wie getSelectedText oben),
        // ist also unabhängig davon, ob ein Echo zwischendurch übersprungen wurde; gleiche
        // null-als-unbekannt-Haltung (im Zweifel kein Eingriff).
        if (!atWordEnd()) {
            clearSuggestions()
            return
        }
        // Den tatsächlich zu committenden Text bestimmen — dieselbe Quelle wie der No-op-Filter
        // in [computeSuggestions], damit beide nie divergieren (siehe [committedForm]).
        val out = committedForm(word, prefix)
        safeIc { ic ->
            // Delete + Commit als EIN atomarer Edit (beginBatchEdit/endBatchEdit) — sonst sieht der
            // Editor den Zwischenzustand „Präfix weg, Ersatz noch nicht da": ein Redraw kann flackern
            // und der Service bekäme zwei onUpdateSelection-Echos (eines auf halb-editiertem Text)
            // statt einem. Das finally garantiert endBatchEdit auch bei einer Ausnahme dazwischen.
            ic.beginBatchEdit()
            try {
                // deleteSurroundingText zählt UTF-16-Code-Units — hier sicher, weil [prefix] aus
                // wordBeforeCursor nur isLetterOrDigit-Zeichen sammelt; einzelne Surrogat-Hälften
                // sind das nicht, also ist der Präfix garantiert BMP-only und prefix.length (Units)
                // == Anzahl Zeichen. (Backspace nutzt bewusst die Code-Point-Variante, weil dort
                // eingefügte astrale Zeichen wie Emoji im Spiel sein können — hier nie.)
                if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
                // Bewusst KEIN angehängtes Leerzeichen: nah committet exakt das Wort, das Leerzeichen
                // bzw. Satzzeichen setzt der Nutzer selbst. So klebt ein direkt folgendes „."/","/"?"/"!"
                // ohne Umweg über Backspace am Wort, und es bleibt deterministisch — kein Smart-Space-
                // Sonderfall, kein Whitespace, den die Tastatur hinter dem Rücken wieder wegschluckt
                // (das wäre genau das „Raten"/Ändern-fertigen-Texts, das nah ablehnt).
                ic.commitText(out, 1)
            } finally {
                ic.endBatchEdit()
            }
        }
        selEnd = selStart // nach dem Eigen-Edit gibt es keine aktive Auswahl mehr
        // Dieser Pfad sagt seine Position bewusst NICHT voraus (sein Echo soll wie bisher normal
        // rechnen) — aber eine etwaige Alt-Quittung aus einem vorherigen Edit löschen, damit sie
        // nicht zufällig das Echo dieses Vorschlag-Commits verschluckt.
        pendingSelfEcho = null
        setShift(ShiftState.OFF)
        clearSuggestions()
        userDisarmedAutoCap = false // ein Vorschlag-Commit ist eine echte Aktion → Auto-Cap rechnet wieder
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

    /**
     * Der Text, den ein Tap auf [word] beim aktuellen [prefix] **tatsächlich committen** würde:
     * eigene Wörter (Namen/Adressen/E-Mails) wörtlich — ihre Schreibweise ist massgeblich —, nur
     * Wörterbuch-Vorschläge dem Präfix-Casing angepasst (damit ein am Satzanfang gross begonnenes
     * „De" nicht durch klein vorgeschlagenes „der" ersetzt wird). Kein Autocorrect: in beiden Fällen
     * wird nur das unfertige Präfix ersetzt.
     *
     * [settings.userWordsEnabled] mitprüfen: der User-Index wird IMMER vorgehalten (siehe NahIme),
     * also meldet [Suggester.isUserWord] auch bei abgeschalteter Funktion noch Treffer. Ohne diese
     * Gate würde ein Wort, das zufällig in beiden Listen steht, bei AUS­geschalteten eigenen Wörtern
     * fälschlich wörtlich statt gecast committet (z. B. „Zeit" statt „ZEIT" unter Caps-Lock).
     *
     * **Einzige Quelle** für [onSuggestionTap] (was committet wird) UND den No-op-Filter in
     * [computeSuggestions] (Vorschlag ausblenden, wenn das Ergebnis == dem schon Getippten ist):
     * laufen beide über diese Funktion, kann der Filter nie etwas anderes annehmen als der Commit tut.
     */
    private fun committedForm(word: String, prefix: String): String {
        val isUserWord = settings.userWordsEnabled && suggester?.isUserWord(word) == true
        return if (isUserWord) word else casedLikePrefix(word, prefix)
    }

    // --- intern ---

    /** Committet [text] wörtlich (kein Shift-Casing, kein Autocorrect) und frischt danach
     *  Auto-Cap/Vorschläge auf. Sagt die kollabierte Cursorposition (selStart + Länge) voraus,
     *  damit das eigene onUpdateSelection-Echo als redundant erkannt wird (siehe [afterTextChanged]
     *  /[onSelectionChanged]). Genutzt für Space/Punkt/Komma/?/! — alle mit fester, bekannter Länge. */
    private fun commitLiteral(text: String) {
        safeIc { it.commitText(text, 1) }
        afterTextChanged(predictedCursor = selStart + text.length)
    }

    private fun cycleShift() {
        val current = _state.value.shift
        // Tippt der Nutzer Shift auf einem AUTO-armierten SHIFTED, will er die automatische
        // Grossschreibung bewusst weghaben — bis zur nächsten echten Aktion darf sie ein
        // Cursor-Callback an dieser Stelle nicht wieder anwerfen (siehe userDisarmedAutoCap).
        // Jeder andere Shift-Tap ist eine normale manuelle Wahl und löscht den Merker wieder.
        userDisarmedAutoCap = current == ShiftState.SHIFTED && autoCapArmed
        val next = when (current) {
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

    private fun afterTextChanged(predictedCursor: Int? = null) {
        // Quittung für das eigene onUpdateSelection-Echo scharf stellen (oder löschen, wenn der Pfad
        // die neue Position nicht sicher vorhersagen kann → predictedCursor == null). Siehe
        // [pendingSelfEcho]/[onSelectionChanged]: das passende Echo überspringt dann seinen Recompute.
        pendingSelfEcho = predictedCursor
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
        // Eine echte Tipp-/Edit-Aktion hebt ein vorheriges manuelles Entwaffnen wieder auf:
        // ab hier soll Auto-Cap wieder normal aus dem (neuen) Kontext rechnen.
        userDisarmedAutoCap = false
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
        // Präfix + Wortende-Lage EINMAL bestimmen und an Vorschläge UND Save-Chip weiterreichen:
        // beide hängen an derselben Frage „steht ein unfertiges Wort am Cursor?", also genügt ein
        // einziger `getTextAfterCursor`-Read (atWordEnd) statt je einem pro Konsument. Der Read fällt
        // nur an, wenn überhaupt ein ≥2-Zeichen-Präfix ohne aktive Auswahl vorliegt (sonst kein Wort).
        val prefix = wordBeforeCursor(before)
        val atEnd = if (prefix.length >= 2 && !hasSelection) atWordEnd() else false
        val (suggestions, barVisible) = computeSuggestions(prefix, atEnd)
        val saveWord = computeSaveWord(prefix, atEnd)
        // null = unverändert lassen (Feature aus, Spezialfeld, manuelles Shift, fehlende IC).
        val autoCapShift = if (reconsiderAutoCap) computeAutoCapShift(before) else null
        // Ein nicht-null Ergebnis armiert genau dann, wenn es SHIFTED ist (siehe
        // computeAutoCapShift); null lässt die Armierung unangetastet.
        if (autoCapShift != null) autoCapArmed = autoCapShift == ShiftState.SHIFTED
        _state.value = _state.value.copy(
            suggestions = suggestions,
            suggestionBarVisible = barVisible,
            saveWord = saveWord,
            shift = autoCapShift ?: _state.value.shift,
        )
    }

    /** Vorschläge + ob die Leiste Platz reserviert, aus dem bereits ermittelten [prefix] und seiner
     *  Wortende-Lage [atEnd] (beide in [refreshForCursor] aus EINEM Read bestimmt). */
    private fun computeSuggestions(prefix: String, atEnd: Boolean): Pair<List<String>, Boolean> {
        // Passwortfeld oder ein Feld mit „keine Vorschläge"-Flag (OTP/Kreditkarte/Benutzername) → gar
        // keine Leiste (kein Schulter-Surfen über sensibler Eingabe, ausdrückliches Feld-Signal). Das
        // sticht auch settings.barAlwaysVisible: über sensibler Eingabe bleibt die Leiste immer aus.
        if (field.isPassword || field.noSuggestions) return emptyList<String>() to false
        val s = suggester
        // „Immer-an"-Quellen, die die Leiste DAUERHAFT reservieren (feste Höhe): Wörterbuch + kuratierte
        // eigene Wörter. Gelernte Wörter sind dagegen ON-DEMAND — sie tragen zur Vorschlagsliste bei,
        // reservieren die Leiste aber NICHT: da sie per Default an sind, stünde sie sonst bei jedem
        // Nutzer leer-reserviert da. Für einen gelernten Treffer blendet sich die Leiste wie das Save-Chip
        // nur bei Bedarf ein (KeyboardContent zeigt sie auch, sobald Vorschläge vorliegen).
        val reservingSource = settings.suggestionsEnabled || settings.userWordsEnabled
        val anySource = reservingSource || settings.learnedWordsEnabled
        // Leiste reserviert (feste Höhe), wenn eine „immer-an"-Quelle aktiv ist ODER der Nutzer sie
        // ausdrücklich immer sichtbar haben will (barAlwaysVisible) — so springt sie beim Tippen nicht.
        val barVisible = reservingSource || settings.barAlwaysVisible
        // Ohne aktive Vorschlagsquelle gibt es keine Vorschläge (die Leiste kann aber reserviert sein).
        // Bei einer aktiven Auswahl ebenfalls keine: onSuggestionTap löscht nur das Präfix vor dem Cursor
        // und committet darüber — bei bestehender Selektion würde derselbe Tap zusätzlich die markierte
        // Stelle ersetzen (commitText überschreibt die Auswahl) und so fertigen Text ungewollt anfassen.
        val list = if (s != null && anySource && prefix.length >= 2 && !hasSelection && atEnd) {
            s.suggest(
                prefix.lowercase(),
                settings.suggestionsEnabled,
                settings.userWordsEnabled,
                settings.learnedWordsEnabled,
            )
                // Einen Vorschlag ausblenden, der exakt das schon Getippte committen würde — seit
                // Vorschläge keinen Trailing-Space mehr anhängen (siehe onSuggestionTap), wäre sein
                // Antippen ein reiner No-op (Präfix löschen, identisch wieder committen). Das passiert
                // u. a. direkt nach einer Annahme: das Wort steht noch am Cursor und schlägt sich selbst
                // vor. Case-sensitiv über committedForm, NICHT equalsIgnoreCase: ein klein getipptes
                // „zeit" → „Zeit" (Nomen-Grossschreibung) ist KEIN No-op und bleibt sichtbar.
                .filterNot { committedForm(it, prefix) == prefix }
        } else {
            emptyList()
        }
        return list to barVisible
    }

    /**
     * Das Wort, das gerade als „speichern"-Chip angeboten wird — oder `null` = keins. **Live**: folgt
     * dem getippten Wort, sobald der Cursor am Wortende steht. **Immer aktiv** (kein Settings-Schalter,
     * bewusste Nutzerwahl), aber gegated: nie über sensibler Eingabe, nur am Wortende ohne Auswahl, nur
     * ein echtes Wort, und nicht, was schon in der Liste steht.
     *
     * **Gespeichert** wird die getippte Schreibweise als Basis (im `LearnedWordRepository`). Beim
     * **Vorschlagen** wird ein gelerntes Wort dann aber NICHT wörtlich committet, sondern wie ein
     * Wörterbuch-Wort an Shift/Caps angepasst (vgl. [committedForm]/[Suggester.isLearnedWord]) — anders
     * als ein kuratiertes eigenes Wort ([Suggester.isUserWord]). Speicher-Form ≠ Commit-Form.
     */
    private fun computeSaveWord(prefix: String, atEnd: Boolean): String? {
        // Privacy: nie anbieten, eine Eingabe aus einem Passwort- oder „keine Vorschläge"-Feld
        // (OTP/2FA/Benutzername/Kreditkarte) in eine dauerhafte, backuppbare Liste zu schreiben.
        if (field.isPassword || field.noSuggestions) return null
        // Wie bei den Vorschlägen: nur am Wortende und ohne aktive Auswahl (atEnd ist in
        // refreshForCursor aus demselben Read bestimmt).
        if (hasSelection || !atEnd) return null
        // Ein echtes WORT: innerhalb der Wortlisten-Grenzen (dieselben wie UserWordValidation, damit
        // ein angebotenes Wort beim Speichern nicht doch an der Länge scheitert) UND mit mindestens
        // einem Buchstaben — so taucht in einem Zahlenfeld nicht für jede getippte PIN/Zahl ein
        // Speichern-Chip auf (es geht um Wörter, nicht um Ziffernfolgen).
        if (prefix.length < UserWordValidation.MIN_LENGTH || prefix.length > UserWordValidation.MAX_LENGTH) return null
        if (prefix.none { it.isLetter() }) return null
        // Schon gespeichert → nichts anzubieten — egal ob als kuratiertes (wörtliches) ODER bereits
        // gelerntes Wort. Beide Indizes werden IMMER vorgehalten (siehe NahIme), das greift also auch
        // bei abgeschalteter Vorschlagsquelle. Fehlt der Suggester (Tests ohne Quelle), kann nicht
        // dedupliziert werden → im Zweifel anbieten.
        if (suggester?.isUserWord(prefix) == true || suggester?.isLearnedWord(prefix) == true) return null
        return prefix
    }

    /**
     * Steht direkt hinter dem Cursor noch ein Buchstabe, sind wir mitten im Wort —
     * dort werden keine Vorschläge angeboten: ein Antippen würde nur das Präfix vor
     * dem Cursor ersetzen (siehe [onSuggestionTap]) und so das Wortende zerstückeln.
     *
     * `null` heisst **unbekannt** (fehlende/flakige InputConnection), NICHT „leer/Wortende":
     * dann konservativ `false` zurückgeben (kein Vorschlag), statt im Zweifel einen Vorschlag
     * anzubieten, der ein mitten im Wort stehendes Wortende zerstückeln könnte — das wäre eine
     * Verletzung des obersten Gesetzes. Gleiche null-als-unbekannt-Haltung wie [computeAutoCapShift].
     * Ein echtes Wortende liefert "" (nicht null) und wird korrekt als Wortende gewertet.
     */
    private fun atWordEnd(): Boolean {
        val after = safeIc { it.getTextAfterCursor(1, 0)?.toString() } ?: return false
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
        // Der Nutzer hat Auto-Cap an dieser Stelle gerade bewusst entwaffnet → nicht wieder
        // anwerfen (ein blosser Cursor-Callback soll den Willen nicht überschreiben). Der Merker
        // hält nur bis zur nächsten echten Aktion (s. userDisarmedAutoCap / afterTextChanged).
        if (userDisarmedAutoCap) return null
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
        // „Transparente" Satzzeichen (öffnende/schliessende Klammern/Anführung) und Whitespace
        // vom Ende her überspringen, dann auf ein Satzende prüfen: so bleibt nach „Satz. («"
        // grossgeschrieben, aber eine Dezimalzahl wie „3.14" armiert NICHT (Ziffern sind nicht
        // transparent → letztes bedeutungstragendes Zeichen ist „4", kein Satzende).
        // Zeilenumbrüche werden dabei bewusst NICHT übersprungen: ein „\n" beginnt eine neue
        // Zeile, die wie ein Satzanfang grossgeschrieben werden soll (mehrzeilige Notiz/Mail).
        // Es taucht ohnehin nur in mehrzeiligen Feldern auf — wo Return ein echtes Enter ist,
        // nicht eine Editor-Action.
        val meaningful = before.trimEnd { (it.isWhitespace() && it != '\n') || it in TRANSPARENT_PUNCT }
        val last = meaningful.lastOrNull()
        // Bekannte Grenze dieser bewusst einfachen Heuristik: eine Abkürzung wie „z. B.", „usw."
        // oder „Nr." endet ebenfalls auf einem SENTENCE_ENDER und armiert darum fälschlich die
        // Grossschreibung des nächsten Zeichens. In Kauf genommen — die Alternative (ein
        // Abkürzungs-Wörterbuch) wäre genau das „Raten", das nah ablehnt, und nie vollständig.
        // Folgenlos genug: es armiert nur EIN Zeichen (kein fertiger Text wird angefasst), und
        // ein einzelner Shift-Tap entwaffnet das auto-armierte SHIFTED sofort wieder (siehe
        // cycleShift/autoCapArmed). Genau wie „3.14" NICHT armiert (Ziffer ist kein Satzende),
        // ist dies die andere Seite derselben simplen, deterministischen Regel.
        val shouldCap = last == null || last == '\n' || last in SENTENCE_ENDERS
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
         *  Satzanfang-Scan (Auto-Cap) als auch das Vorschlags-Präfix mit einem Read ab.
         *
         *  Bekannte Grenze (wie die Abkürzungs-Heuristik in [computeAutoCapShift]): stünden
         *  vor dem Cursor MEHR als so viele reine Whitespace-/transparente Zeichen, läge das
         *  letzte bedeutungstragende Zeichen ausserhalb des Fensters → der Satzanfang-Scan
         *  sähe nur Leerraum (`meaningful == ""`) und armierte fälschlich gross. Praktisch
         *  irrelevant (niemand tippt 64 Leerzeichen) und folgenarm — es armiert nur EIN
         *  Zeichen, das ein Shift-Tap sofort wieder entwaffnet. Ein grösseres Fenster kostete
         *  pro Schritt mehr, ohne realen Gewinn. */
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
