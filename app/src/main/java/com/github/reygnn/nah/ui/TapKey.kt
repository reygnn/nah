package com.github.reygnn.nah.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
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
    onKey: (KeyboardKey) -> Unit,
    onAlternative: (String) -> Unit = {},
) {
    val label = when (key) {
        is CharKey -> when (shift) {
            ShiftState.OFF -> key.output
            ShiftState.SHIFTED -> key.output.replaceFirstChar { it.uppercaseChar() }
            ShiftState.CAPS -> key.output.uppercase()
        }
        is FunctionKey -> key.label
    }

    // Backspace, Shift und Return zeigen ein Material-Vektor-Icon statt der
    // Unicode-Glyphe; alle anderen Tasten bleiben Text.
    val icon: ImageVector? = when {
        key !is FunctionKey -> null
        key.action == KeyAction.BACKSPACE -> NahIcons.Backspace
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
        null -> if (key is CharKey) colors.surfaceContainerHigh else colors.surfaceContainerLow
    }
    val fg = when (hint) {
        NahColors.Hint.Vowel -> NahColors.VowelOn
        NahColors.Hint.Consonant -> NahColors.ConsonantOn
        null -> if (key is CharKey) colors.onSurface else colors.onSurfaceVariant
    }

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

    val tapModifier = when {
        isBackspace -> Modifier.pointerInput(key) {
            // Backspace: erst sofort löschen, dann — falls gehalten — im Repeat-Takt.
            // Haptik nur beim ersten Druck, nicht bei jeder Wiederholung.
            detectTapGestures(
                onPress = {
                    tap()
                    if (withTimeoutOrNull(BACKSPACE_INITIAL_DELAY_MS) { tryAwaitRelease() } == null) {
                        while (true) {
                            onKey(key)
                            if (withTimeoutOrNull(BACKSPACE_REPEAT_MS) { tryAwaitRelease() } != null) break
                        }
                    }
                },
            )
        }
        alternatives.isNotEmpty() -> Modifier.pointerInput(key) {
            val chipPx = CHIP_WIDTH.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val longPress = awaitLongPressOrCancellation(down.id)
                if (longPress == null) {
                    tap() // schneller Tap → Basis-Output
                    return@awaitEachGesture
                }
                // Gehalten → Popup öffnen, Auswahl per Schieben verfolgen.
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                popupOpen = true
                highlight = highlightFor(longPress.position, size.width.toFloat(), chipPx, alternatives.size)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    highlight = highlightFor(change.position, size.width.toFloat(), chipPx, alternatives.size)
                    change.consume()
                    if (!change.pressed) break
                }
                // Losgelassen: gewählten Chip committen, sonst (auf der Taste) den Basis-Output.
                val selected = highlight
                popupOpen = false
                highlight = -1
                if (selected in alternatives.indices) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onAlternative(alternatives[selected])
                } else {
                    onKey(key)
                }
            }
        }
        else -> Modifier.clickable { tap() }
    }

    Box(
        // Gleichmässige Totzone ringsum (Padding VOR der Tap-Erkennung = nicht klickbar).
        modifier = modifier
            .padding(KEY_GAP)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(tapModifier),
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
            AlternativesPopup(alternatives = alternatives, highlight = highlight)
        }
    }
}

/**
 * Welcher Chip ist unter dem Finger? Erst wenn der Finger über die Tastenoberkante
 * (y < 0) ins Popup-Band zieht; sonst -1 (Loslassen committet den Basis-Output).
 */
private fun highlightFor(pos: Offset, keyWidthPx: Float, chipPx: Float, count: Int): Int {
    if (count == 0 || pos.y >= 0f) return -1
    val band = count * chipPx
    val startX = keyWidthPx / 2f - band / 2f
    return floor((pos.x - startX) / chipPx).toInt().coerceIn(0, count - 1)
}

/** Sichtbares Alternativen-Popup, mittig über der Taste. Der hervorgehobene Chip
 *  (unter dem Finger) wird beim Loslassen committet. */
@Composable
private fun AlternativesPopup(alternatives: List<String>, highlight: Int) {
    val colors = MaterialTheme.colorScheme
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = IntOffset(
                x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
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
                        text = alt,
                        color = if (i == highlight) colors.onPrimary else colors.inverseOnSurface,
                        fontSize = 20.sp,
                    )
                }
            }
        }
    }
}
