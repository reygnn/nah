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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.github.reygnn.nah.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Long-Press-Popup: Grösse eines Alternativ-Chips und Abstand über der Taste. */
private val CHIP_WIDTH = 46.dp
private val CHIP_HEIGHT = 50.dp
private val POPUP_GAP = 6.dp

/**
 * Eine grosse, deterministische Tipp-Taste. Ein Tap = genau diese [key]. Labels
 * sind immer sichtbar → ab Tag eins per hunt-and-peck benutzbar, keine Lernwand.
 *
 * Tasten mit [CharKey.alternatives] zeigen beim **Gedrückthalten** ein sichtbares
 * Popup (z. B. `c` → ch/ck/sch, qu-Taste → einzelnes `q`). Auswahl: Finger zum Chip
 * **schieben und loslassen** — losgelassen auf der Taste committet den Basis-Output.
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

    // Die Space-Taste rendert nur " " — ohne contentDescription läse TalkBack ein leeres
    // Label vor („Schaltfläche"). Alle anderen Tasten haben sichtbaren Text bzw. ein Icon
    // mit contentDescription und brauchen das nicht.
    val spaceCd = if (key is FunctionKey && key.action == KeyAction.SPACE) {
        stringResource(R.string.key_space_cd)
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
    val alternatives = (key as? CharKey)?.alternatives.orEmpty()

    // Long-Press-Popup-Zustand (nur für Tasten mit Alternativen).
    var popupOpen by remember { mutableStateOf(false) }
    var highlight by remember { mutableIntStateOf(-1) }
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
        alternatives.isNotEmpty() -> Modifier
            .semantics(mergeDescendants = true) { role = Role.Button; onClick { onKey(key); true } }
            .indication(interactionSource, ripple())
            .pointerInput(key) {
            val chipPx = CHIP_WIDTH.toPx()
            val bandPx = alternatives.size * chipPx
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = PressInteraction.Press(down.position)
                scope.launch { interactionSource.emit(press) } // Ripple an
                val longPress = awaitLongPressOrCancellation(down.id)
                if (longPress == null) {
                    tap() // schneller Tap → Basis-Output
                    scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                    return@awaitEachGesture
                }
                // Gehalten → Popup öffnen, Auswahl per Schieben verfolgen. Band-Kante
                // einmal pro Geste festlegen (mittig über der Taste, an den Fensterrand
                // geclampt) — Popup und Chip-Auswahl teilen sich genau diesen Wert.
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val keyLeft = keyLeftInWindow
                val keyHeight = size.height.toFloat()
                val bandLeft = bandLeftInWindow(keyLeft, size.width.toFloat(), bandPx, view.width.toFloat())
                bandLeftPx = bandLeft
                popupOpen = true
                highlight = chipIndexFor(longPress.position, keyLeft, bandLeft, chipPx, alternatives.size, keyHeight)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    highlight = chipIndexFor(change.position, keyLeft, bandLeft, chipPx, alternatives.size, keyHeight)
                    change.consume()
                    if (!change.pressed) break
                }
                // Losgelassen: gewählten Chip committen; auf der Taste → Basis-Output; unter
                // die Taste gezogen ([CHIP_CANCEL]) → nichts committen (Geste abgebrochen).
                val selected = highlight
                popupOpen = false
                highlight = CHIP_BASE
                when {
                    selected in alternatives.indices -> {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onAlternative(alternatives[selected])
                    }
                    selected == CHIP_CANCEL -> Unit // bewusst abgebrochen → kein Commit
                    else -> onKey(key) // CHIP_BASE
                }
                scope.launch { interactionSource.emit(PressInteraction.Release(press)) } // Ripple aus
            }
        }
        else -> Modifier.clickable { tap() }
    }

    Box(
        // Gleichmässige Totzone ringsum (Padding VOR der Tap-Erkennung = nicht klickbar).
        modifier = modifier
            .padding(KEY_GAP)
            .onGloballyPositioned { keyLeftInWindow = it.positionInWindow().x }
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (enabled) tapModifier else Modifier)
            .then(
                if (spaceCd != null) {
                    Modifier.semantics(mergeDescendants = true) { contentDescription = spaceCd }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Text(
                text = label,
                color = fg,
                fontSize = if (key is CharKey) 22.sp else 18.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (popupOpen) {
            AlternativesPopup(
                alternatives = alternatives,
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

/** Loslassen auf der Taste selbst → Basis-Output (kein Chip gewählt). */
internal const val CHIP_BASE = -1

/** Unter die Tastenunterkante gezogen → Geste abgebrochen, Loslassen committet nichts. */
internal const val CHIP_CANCEL = -2

/**
 * Welcher Chip ist unter dem Finger?
 *  - über die Tastenoberkante ([pos].y < 0) ins Popup-Band gezogen → der getroffene Chip,
 *  - noch auf der Taste (0 ≤ y ≤ [keyHeightPx]) → [CHIP_BASE] (Loslassen committet den Basis-Output),
 *  - unter die Tastenunterkante (y > [keyHeightPx]) → [CHIP_CANCEL] (Abbruch, kein Commit).
 *
 * Gerechnet in Fensterkoordinaten gegen [bandLeftInWindow] ([keyLeftInWindow] verschiebt
 * die tastenlokale Finger-X dorthin), damit ein randnah geclamptes Popup den richtigen
 * Chip liefert.
 */
internal fun chipIndexFor(
    pos: Offset,
    keyLeftInWindow: Float,
    bandLeftInWindow: Float,
    chipPx: Float,
    count: Int,
    keyHeightPx: Float,
): Int {
    if (count == 0) return CHIP_BASE
    if (pos.y > keyHeightPx) return CHIP_CANCEL
    if (pos.y >= 0f) return CHIP_BASE
    val pointerXInWindow = keyLeftInWindow + pos.x
    return floor((pointerXInWindow - bandLeftInWindow) / chipPx).toInt().coerceIn(0, count - 1)
}

/** Sichtbares Alternativen-Popup, mittig über der Taste. Der hervorgehobene Chip
 *  (unter dem Finger) wird beim Loslassen committet. Die Chips zeigen die Alternative
 *  durch dieselbe Casing-Quelle ([ShiftState.applyTo]) wie der Commit (onAlternative →
 *  commitWithShift) — sonst zeigte das Popup unter Shift/Caps „sch", committete aber
 *  „Sch"/„SCH" und bräche die „Angezeigtes == Getipptes"-Garantie der Basistasten. */
@Composable
private fun AlternativesPopup(
    alternatives: List<String>,
    shift: ShiftState,
    highlight: Int,
    bandLeftPx: Float,
) {
    val colors = MaterialTheme.colorScheme
    // x kommt aus der bereits geclampten Band-Kante (siehe bandLeftInWindow) — dieselbe
    // Quelle wie die Chip-Auswahl, damit Anzeige und Commit deckungsgleich sind.
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
        // Element erkennbar. Der hervorgehobene Chip in der Akzentfarbe.
        Row(
            modifier = Modifier
                .padding(bottom = POPUP_GAP)
                .shadow(6.dp, RoundedCornerShape(10.dp))
                .background(colors.inverseSurface),
        ) {
            alternatives.forEachIndexed { i, alt ->
                Box(
                    modifier = Modifier
                        .size(CHIP_WIDTH, CHIP_HEIGHT)
                        .background(if (i == highlight) colors.primary else colors.inverseSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = shift.applyTo(alt),
                        color = if (i == highlight) colors.onPrimary else colors.inverseOnSurface,
                        fontSize = 20.sp,
                    )
                }
            }
        }
    }
}
