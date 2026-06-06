package com.github.reygnn.nah.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.github.reygnn.nah.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.FunctionKey
import com.github.reygnn.nah.layout.KeyAction
import com.github.reygnn.nah.layout.KeyboardKey
import com.github.reygnn.nah.viewmodel.ShiftState
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Nicht klickbarer Rand ringsum jede Taste (Totzone gegen Fehltipper). */
private val KEY_GAP = 5.dp

/** Backspace-Auto-Repeat: Verzögerung bis das Halten zu wiederholen beginnt … */
private const val BACKSPACE_INITIAL_DELAY_MS = 400L
/** … und der Abstand zwischen den Wiederhol-Löschungen danach. */
private const val BACKSPACE_REPEAT_MS = 55L

/**
 * Marker (kleiner gefüllter Kreis, rechts oben) auf Tasten mit Long-Press-Menü: macht die
 * sonst unsichtbaren Alternativen sichtbar (Discoverability — Anforderung 3), ohne das
 * zentrierte Label anzutasten. Die Farbe kommt aus dem Theme-Akzent (`colorScheme.primary`)
 * statt eines festen Rots: theme-konsistenter Kontrast auf dem dynamischen Schema und kein
 * „Fehler/Benachrichtigung"-Beiklang eines roten Punktes. [MARKER_INSET] hält den Punkt
 * innerhalb des abgerundeten Tasten-Clips (8.dp-Ecke).
 */
private val MARKER_SIZE = 7.dp
private val MARKER_INSET = 4.dp

/** Long-Press-Popup: Grösse eines Alternativ-Chips und Abstand über der Taste. */
private val CHIP_WIDTH = 46.dp
private val CHIP_HEIGHT = 50.dp
private val POPUP_GAP = 6.dp

/** Ein Long-Press-Chip: sichtbares [label] + Wirkung beim Loslassen darauf. Vereinheitlicht
 *  die beiden Quellen (CharKey-Alternativen committen einen String, FunctionKey-Shortcuts
 *  lösen eine Aktion aus) auf dieselbe Popup-Geste. */
private class LongPressItem(val label: String, val onSelect: () -> Unit)

/**
 * Schriftgrösse, die NICHT mit der System-Schriftskalierung mitwächst. Tastenbeschriftungen
 * (und Popup-Chips) sind feste Symbole in Containern mit fixer dp-Höhe; skalierte `sp` würde
 * bei grossem System-Font-Scale über die Tastenhöhe wachsen und das Label vertikal abschneiden —
 * ein Bruch der „sichtbare Labels"-Anforderung genau für die Nutzergruppe, die grosse Schrift
 * braucht. `dp.toSp()` im aktuellen Density-Kontext liefert eine fontScale-unabhängige Grösse, das
 * Label passt also deterministisch in die Taste. (Fliesstext anderswo skaliert weiterhin normal.)
 */
@Composable
private fun nonScaledSp(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }

/**
 * Eine grosse, deterministische Tipp-Taste. Ein Tap = genau diese [key]. Labels
 * sind immer sichtbar → ab Tag eins per hunt-and-peck benutzbar, keine Lernwand.
 *
 * Tasten mit Long-Press-Einträgen zeigen beim **Gedrückthalten** ein sichtbares Popup, das
 * **vertikal nach oben** aufklappt. Zwei Quellen, gleiche Geste: [CharKey.alternatives]
 * committen ein Zeichen (z. B. `a` → ä/à/â, qu-Taste → einzelnes `q`),
 * [FunctionKey.longPressActions] lösen eine Aktion aus (die ?123-Taste → Ziffern-Pad/Wählfeld).
 * Auswahl: **Halten und loslassen** committet das **erste** Item (z. B. ä) — der Finger muss
 * sich nicht bewegen; für weitere Items den Finger nach **oben** schieben, zum Abbrechen nach
 * **unten** unter die Taste ziehen. Ein normaler **Tap** committet immer das Basiszeichen.
 * Sichtbar = keine Lernkurve (anders als vuots unsichtbare Swipes).
 */
@Composable
fun TapKey(
    key: KeyboardKey,
    shift: ShiftState,
    modifier: Modifier = Modifier,
    colorHints: Boolean = false,
    enabled: Boolean = true,
    onKey: (KeyboardKey) -> Unit,
    onAlternative: (String) -> Unit = {},
) {
    val label = when (key) {
        // Dieselbe Casing-Quelle wie das Commit (siehe ShiftState.applyTo) — so zeigt die
        // Taste garantiert exakt das an, was sie tippt.
        is CharKey -> shift.applyTo(key.output)
        is FunctionKey -> key.label
    }

    // Lokalisierte TalkBack-Beschreibung pro Funktionstaste statt der rohen Glyphe/des
    // Symbol-Strings: Icon-Tasten (Backspace/Shift/Return/Paste) trügen sonst nur ihr
    // Unicode-Symbol, die Layer-Toggles (?123/ABC) nur ihren Symbol-String — beides
    // buchstabiert ein Screenreader sinnlos. CharKeys und ,/. sind selbsterklärend (sichtbarer
    // Text genügt) → null. Eine Quelle für Icon-contentDescription UND die Box-Semantik unten.
    val functionCd: String? = (key as? FunctionKey)?.let {
        when (it.action) {
            KeyAction.SPACE -> stringResource(R.string.key_space_cd)
            KeyAction.PASTE -> stringResource(R.string.key_paste_cd)
            KeyAction.BACKSPACE -> stringResource(R.string.key_backspace_cd)
            KeyAction.RETURN -> stringResource(R.string.key_return_cd)
            KeyAction.SHIFT -> stringResource(R.string.key_shift_cd)
            KeyAction.SYMBOLS -> stringResource(R.string.key_symbols_cd)
            KeyAction.ALPHA -> stringResource(R.string.key_alpha_cd)
            else -> null
        }
    }

    // Der Shift-Zustand (aus/einmal/Feststelltaste) ist visuell nur am wechselnden Icon
    // erkennbar; für TalkBack zusätzlich als stateDescription, damit ein blinder Nutzer hört,
    // ob gerade Caps-Lock aktiv ist (sonst committen Buchstaben überraschend gross/klein).
    val shiftStateCd: String? = if (key is FunctionKey && key.action == KeyAction.SHIFT) {
        when (shift) {
            ShiftState.OFF -> stringResource(R.string.shift_state_off)
            ShiftState.SHIFTED -> stringResource(R.string.shift_state_on)
            ShiftState.CAPS -> stringResource(R.string.shift_state_caps)
        }
    } else {
        null
    }

    // Backspace, Shift und Return zeigen ein Material-Vektor-Icon statt der
    // Unicode-Glyphe; alle anderen Tasten bleiben Text.
    val icon: ImageVector? = when {
        key !is FunctionKey -> null
        key.action == KeyAction.BACKSPACE -> NahIcons.Backspace
        key.action == KeyAction.PASTE -> NahIcons.Paste
        key.action == KeyAction.RETURN -> NahIcons.Return
        key.action == KeyAction.SPACE -> NahIcons.Space
        key.action == KeyAction.SHIFT -> when (shift) {
            ShiftState.CAPS -> NahIcons.ShiftCaps
            ShiftState.SHIFTED -> NahIcons.Shift
            ShiftState.OFF -> NahIcons.ShiftOutline
        }
        else -> null
    }

    val colors = MaterialTheme.colorScheme
    // „Stützräder": Vokale/häufige Konsonanten bekommen fixe Lern-Farben, sobald der
    // Schalter an ist. Sonst die neutralen Container-Tiers wie gehabt.
    val hint = if (colorHints && key is CharKey) NahColors.hintFor(key.char) else null
    val bg = when (hint) {
        NahColors.Hint.Vowel -> NahColors.VowelKey
        NahColors.Hint.Consonant -> NahColors.ConsonantKey
        NahColors.Hint.Rare -> NahColors.RareKey
        null -> if (key is CharKey) colors.surfaceContainerHigh else colors.surfaceContainerLow
    }
    val fgBase = when (hint) {
        NahColors.Hint.Vowel -> NahColors.VowelOn
        NahColors.Hint.Consonant -> NahColors.ConsonantOn
        NahColors.Hint.Rare -> NahColors.RareOn
        null -> if (key is CharKey) colors.onSurface else colors.onSurfaceVariant
    }
    // Inaktiv (z. B. Einfügen-Taste bei leerer Zwischenablage): stark gedimmt, keine Geste.
    val fg = if (enabled) fgBase else fgBase.copy(alpha = 0.3f)

    val view = LocalView.current
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onKey(key)
    }

    val isBackspace = key is FunctionKey && key.action == KeyAction.BACKSPACE
    // Vereinheitlichte Long-Press-Einträge: CharKey-Alternativen committen einen String, die
    // Shortcuts der ?123-Taste lösen eine KeyAction aus — beide über dieselbe sichtbare Geste.
    val longPressItems: List<LongPressItem> = when (key) {
        is CharKey -> key.alternatives.map { alt -> LongPressItem(alt) { onAlternative(alt) } }
        is FunctionKey -> key.longPressActions.map { act -> LongPressItem(act.label) { onKey(FunctionKey(act)) } }
    }
    // BEWUSSTE a11y-Grenze: Die Long-Press-Alternativen sind nur über die Schiebe/Loslass-Geste
    // erreichbar, die TalkBack nicht synthetisieren kann — es gibt KEINE semantics-CustomActions
    // dafür. Akzeptiert, weil Long-Press eine sehende Komfortgeste ist und JEDE Funktion auch per
    // Tap erreichbar bleibt (einzelnes q über die qu-Alternative ist Komfort; Ziffern voll über
    // die ?123-Ebene, Akzente notfalls per Symbolebene). Vollständige TalkBack-Bedienung ist für
    // diese Einfinger-Sicht-Tastatur kein Ziel (siehe Basis-Labels oben).

    // Long-Press-Popup-Zustand (nur für Tasten mit Long-Press-Einträgen).
    var popupOpen by remember { mutableStateOf(false) }
    var highlight by remember { mutableIntStateOf(CHIP_CANCEL) }
    // Linke Tastenkante in Fensterkoordinaten (via onGloballyPositioned gepflegt) und die
    // daraus berechnete, ggf. an den Fensterrand geclampte linke Kante des Popup-Bands.
    // BEIDE — Popup-Position UND Chip-Auswahl — rechnen gegen denselben Wert, damit ein
    // randnah geclamptes Popup nicht einen anderen Chip anzeigt, als es committet.
    var keyLeftInWindow by remember { mutableFloatStateOf(0f) }
    var bandLeftPx by remember { mutableFloatStateOf(0f) }
    // Geteilte Interaction-Source für den Ripple der Gesten-Tasten (Backspace + Long-Press-
    // Tasten). Die nutzen rohes pointerInput statt clickable und bekämen sonst weder Ripple
    // noch (s. die semantics-Modifier unten) eine TalkBack-aktivierbare Klick-Semantik —
    // anders als die normalen Tasten, die clickable beides liefert.
    val interactionSource = remember { MutableInteractionSource() }
    // awaitEachGesture läuft in einem restricted Scope (kein suspend-emit möglich); der
    // Ripple der Long-Press-Tasten wird daher über diesen Scope getrieben.
    val scope = rememberCoroutineScope()

    val tapModifier = when {
        isBackspace -> Modifier
            .semantics(mergeDescendants = true) { role = Role.Button; onClick { onKey(key); true } }
            .indication(interactionSource, ripple())
            .pointerInput(key) {
                // Backspace: erst sofort löschen, dann — falls gehalten — im Repeat-Takt.
                // Haptik nur beim ersten Druck, nicht bei jeder Wiederholung.
                detectTapGestures(
                    onPress = {
                        val press = PressInteraction.Press(it)
                        interactionSource.emit(press) // Ripple an
                        tap()
                        if (withTimeoutOrNull(BACKSPACE_INITIAL_DELAY_MS) { tryAwaitRelease() } == null) {
                            while (true) {
                                onKey(key)
                                if (withTimeoutOrNull(BACKSPACE_REPEAT_MS) { tryAwaitRelease() } != null) break
                            }
                        }
                        interactionSource.emit(PressInteraction.Release(press)) // Ripple aus
                    },
                )
            }
        longPressItems.isNotEmpty() -> Modifier
            .semantics(mergeDescendants = true) { role = Role.Button; onClick { onKey(key); true } }
            .indication(interactionSource, ripple())
            .pointerInput(key) {
            val chipWidthPx = CHIP_WIDTH.toPx()
            val chipHeightPx = CHIP_HEIGHT.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = PressInteraction.Press(down.position)
                scope.launch { interactionSource.emit(press) } // Ripple an
                // try/finally: der Release MUSS laufen, auch wenn die Geste mittendrin abbricht
                // (Pointer-Reset / Rekomposition mit geändertem key während des Haltens) — sonst
                // bliebe der Ripple-Highlight dauerhaft hängen. clickable/detectTapGestures
                // garantieren das intern; diese handgeschriebene Geste muss es selbst tun.
                try {
                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress == null) {
                        tap() // schneller Tap → Basis-Output
                        return@awaitEachGesture // finally unten gibt den Ripple frei
                    }
                    // Gehalten → Popup öffnen, Auswahl per Schieben nach oben verfolgen. Die
                    // X-Kante der Spalte (mittig über der Taste, an den Fensterrand geclampt)
                    // bestimmt nur die Anzeige; die Auswahl ist rein vertikal (chipIndexFor).
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    val keyLeft = keyLeftInWindow
                    val keyHeight = size.height.toFloat()
                    bandLeftPx = bandLeftInWindow(keyLeft, size.width.toFloat(), chipWidthPx, view.width.toFloat())
                    popupOpen = true
                    highlight = chipIndexFor(longPress.position, chipHeightPx, longPressItems.size, keyHeight)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        highlight = chipIndexFor(change.position, chipHeightPx, longPressItems.size, keyHeight)
                        change.consume()
                        if (!change.pressed) break
                    }
                    // Losgelassen: gewählten Chip auslösen. Default ist Chip 0 (Halten ohne
                    // Schieben), nach oben gezogen die weiteren; unter die Taste ([CHIP_CANCEL])
                    // → nichts. Das Basiszeichen gibt es hier NICHT — dafür ist der Tap da.
                    val selected = highlight
                    popupOpen = false
                    highlight = CHIP_CANCEL
                    if (selected in longPressItems.indices) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        longPressItems[selected].onSelect()
                    }
                } finally {
                    // Räumt das Popup auch bei Abbruch auf (sonst bliebe es bei einer mitten im
                    // Halten abgebrochenen Geste sichtbar offen) und gibt den Ripple frei.
                    popupOpen = false
                    highlight = CHIP_CANCEL
                    scope.launch { interactionSource.emit(PressInteraction.Release(press)) } // Ripple aus
                }
            }
        }
        // role = Button macht die normalen Tasten für TalkBack konsistent zu den
        // Gesten-Tasten (Backspace/Alternativen setzen es oben schon explizit).
        else -> Modifier.clickable(role = Role.Button) { tap() }
    }

    Box(
        // Gleichmässige Totzone ringsum (Padding VOR der Tap-Erkennung = nicht klickbar).
        modifier = modifier
            .padding(KEY_GAP)
            .onGloballyPositioned { keyLeftInWindow = it.positionInWindow().x }
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(
                if (enabled) {
                    tapModifier
                } else {
                    // Deaktiviert (z. B. Einfügen bei leerer Zwischenablage): keine Geste, aber
                    // TalkBack soll den Deaktiviert-Zustand ansagen statt die Taste still
                    // wirkungslos zu lassen. Die Beschreibung liefert weiterhin das Icon (functionCd).
                    Modifier.semantics(mergeDescendants = true) { disabled(); role = Role.Button }
                },
            )
            .then(
                // Text-gerenderte Funktionstasten (Space/?123/ABC) trügen sonst nur ihren
                // sichtbaren String; die lokalisierte contentDescription macht daraus eine
                // gesprochene Funktion. Icon-Tasten beschreibt das Icon selbst (unten).
                if (icon == null && functionCd != null) {
                    Modifier.semantics(mergeDescendants = true) { contentDescription = functionCd }
                } else {
                    Modifier
                },
            )
            .then(
                if (shiftStateCd != null) {
                    Modifier.semantics(mergeDescendants = true) { stateDescription = shiftStateCd }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = functionCd ?: label,
                tint = fg,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Text(
                text = label,
                color = fg,
                // Bewusst nicht-skalierend (siehe nonScaledSp): das Label muss in die fixe
                // Tastenhöhe passen. maxLines/softWrap als Absicherung gegen Umbruch.
                fontSize = if (key is CharKey) nonScaledSp(22.dp) else nonScaledSp(18.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
        // Sichtbarer Hinweis „diese Taste hat ein Long-Press-Menü". Dieselbe Bedingung wie die
        // Geste oben (longPressItems) → der Marker erscheint exakt dann, wenn das Halten etwas tut.
        if (longPressItems.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(MARKER_INSET)
                    .size(MARKER_SIZE)
                    .background(colors.primary, CircleShape),
            )
        }
        if (popupOpen) {
            // shift.applyTo (im Popup) wirkt nur auf die Buchstaben-Alternativen; die
            // Aktions-Labels der ?123-Taste („123"/„*#+") tragen keine Buchstaben → No-op.
            AlternativesPopup(
                alternatives = longPressItems.map { it.label },
                shift = shift,
                highlight = highlight,
                bandLeftPx = bandLeftPx,
            )
        }
    }
}

/**
 * Linke Kante des Alternativen-Bands in **Fensterkoordinaten**: mittig über der Taste,
 * aber an den Fensterrand geclampt — exakt so, wie das Popup selbst platziert wird.
 * Indem Popup-Position und [chipIndexFor] denselben Wert nutzen, kann ein an den Rand
 * geschobenes Popup nie einen anderen Chip anzeigen, als beim Loslassen committet wird.
 */
internal fun bandLeftInWindow(
    keyLeftInWindow: Float,
    keyWidthPx: Float,
    bandWidthPx: Float,
    windowWidthPx: Float,
): Float {
    val keyCenter = keyLeftInWindow + keyWidthPx / 2f
    val maxLeft = (windowWidthPx - bandWidthPx).coerceAtLeast(0f)
    return (keyCenter - bandWidthPx / 2f).coerceIn(0f, maxLeft)
}

/** Unter die Tastenunterkante gezogen → Geste abgebrochen, Loslassen committet nichts. */
internal const val CHIP_CANCEL = -2

/**
 * Welcher Chip ist unter dem Finger im **vertikalen**, nach oben aufklappenden Menü?
 *  - Finger noch auf der Taste (0 ≤ y ≤ [keyHeightPx]) → Chip 0, der Default: Halten und
 *    Loslassen ohne jede Bewegung committet das erste Item (z. B. ä),
 *  - je [chipHeightPx] weiter über die Tastenoberkante (y < 0) gezogen → der nächsthöhere Chip,
 *  - unter die Tastenunterkante (y > [keyHeightPx]) → [CHIP_CANCEL] (Abbruch, kein Commit).
 *
 * Rein y-basiert: die Alternativen stehen in einer einzigen Spalte mittig über der Taste,
 * die Finger-X ist für die Auswahl belanglos (anders als beim früheren horizontalen Band).
 */
internal fun chipIndexFor(
    pos: Offset,
    chipHeightPx: Float,
    count: Int,
    keyHeightPx: Float,
): Int {
    if (count == 0) return CHIP_CANCEL
    if (pos.y > keyHeightPx) return CHIP_CANCEL
    return floor(-pos.y / chipHeightPx).toInt().coerceIn(0, count - 1)
}

/** Sichtbares Alternativen-Popup als **vertikale Spalte**, mittig über der Taste. Item 0 sitzt
 *  **unten** (direkt über dem Finger) → Loslassen ohne Schieben committet es; höhere Items
 *  liegen darüber. Der hervorgehobene Chip (unter dem Finger) wird beim Loslassen committet. Die
 *  Chips zeigen die Alternative durch dieselbe Casing-Quelle ([ShiftState.applyTo]) wie der Commit
 *  (onAlternative → commitWithShift) — sonst zeigte das Popup unter Shift/Caps „sch", committete
 *  aber „Sch"/„SCH" und bräche die „Angezeigtes == Getipptes"-Garantie der Basistasten. */
@Composable
private fun AlternativesPopup(
    alternatives: List<String>,
    shift: ShiftState,
    highlight: Int,
    bandLeftPx: Float,
) {
    val colors = MaterialTheme.colorScheme
    // x zentriert die (eine Chip breite) Spalte über der Taste, geclampt an den Fensterrand
    // (siehe bandLeftInWindow). Die Chip-Auswahl ist rein vertikal, hängt also nicht daran.
    val positionProvider = remember(bandLeftPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = IntOffset(
                x = bandLeftPx.roundToInt(),
                y = anchorBounds.top - popupContentSize.height,
            )
        }
    }
    Popup(popupPositionProvider = positionProvider) {
        // Invers zu den (dunklen) Tasten: helle, schwebende Fläche → klar als eigenes
        // Element erkennbar. Der hervorgehobene Chip in der Akzentfarbe. Von oben nach unten
        // absteigend gerendert, damit Item 0 ganz unten (am Finger) steht.
        Column(
            modifier = Modifier
                .padding(bottom = POPUP_GAP)
                .shadow(6.dp, RoundedCornerShape(10.dp))
                .background(colors.inverseSurface),
        ) {
            for (i in alternatives.indices.reversed()) {
                Box(
                    modifier = Modifier
                        .size(CHIP_WIDTH, CHIP_HEIGHT)
                        .background(if (i == highlight) colors.primary else colors.inverseSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = shift.applyTo(alternatives[i]),
                        color = if (i == highlight) colors.onPrimary else colors.inverseOnSurface,
                        fontSize = nonScaledSp(20.dp), // wie die Tasten: fixe Chip-Höhe, kein Clipping
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
