package com.github.reygnn.nah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.FunctionKey
import com.github.reygnn.nah.layout.KeyAction
import com.github.reygnn.nah.layout.KeyboardKey
import com.github.reygnn.nah.viewmodel.ShiftState
import kotlinx.coroutines.withTimeoutOrNull

/** Nicht klickbarer Rand ringsum jede Taste (Totzone gegen Fehltipper). */
private val KEY_GAP = 5.dp

/** Backspace-Auto-Repeat: Verzögerung bis das Halten zu wiederholen beginnt … */
private const val BACKSPACE_INITIAL_DELAY_MS = 400L
/** … und der Abstand zwischen den Wiederhol-Löschungen danach. */
private const val BACKSPACE_REPEAT_MS = 55L

/**
 * Eine grosse, deterministische Tipp-Taste. Ein Tap = genau diese [key]. Labels
 * sind immer sichtbar → ab Tag eins per hunt-and-peck benutzbar, keine Lernwand.
 */
@Composable
fun TapKey(
    key: KeyboardKey,
    shift: ShiftState,
    modifier: Modifier = Modifier,
    onKey: (KeyboardKey) -> Unit,
) {
    val isShiftKey = key is FunctionKey && key.action == KeyAction.SHIFT

    val label = when (key) {
        is CharKey -> if (shift != ShiftState.OFF) key.char.uppercaseChar().toString() else key.char.toString()
        is FunctionKey -> key.label
    }

    // Farben aus dem Material-3-Schema (Material You, dynamisch). Shift-Status über
    // die Rollen: einmaliges Shift = primary, Caps Lock = tertiary; sonst neutrale
    // Container-Tiers (Buchstaben heller/erhabener als Funktionstasten).
    val colors = MaterialTheme.colorScheme
    val bg = when {
        isShiftKey && shift == ShiftState.SHIFTED -> colors.primary
        isShiftKey && shift == ShiftState.CAPS -> colors.tertiary
        key is CharKey -> colors.surfaceContainerHigh
        else -> colors.surfaceContainerLow
    }
    val fg = when {
        isShiftKey && shift == ShiftState.SHIFTED -> colors.onPrimary
        isShiftKey && shift == ShiftState.CAPS -> colors.onTertiary
        key is CharKey -> colors.onSurface
        else -> colors.onSurfaceVariant
    }

    val isBackspace = key is FunctionKey && key.action == KeyAction.BACKSPACE
    // Backspace löscht beim Gedrückthalten fortlaufend; alle anderen Tasten sind
    // ein simpler Tap. Erst einmal sofort löschen, dann — falls nach der
    // Anfangsverzögerung noch gehalten — im Repeat-Takt, bis losgelassen wird.
    val tapModifier = if (isBackspace) {
        Modifier.pointerInput(key) {
            detectTapGestures(
                onPress = {
                    onKey(key)
                    if (withTimeoutOrNull(BACKSPACE_INITIAL_DELAY_MS) { tryAwaitRelease() } == null) {
                        while (true) {
                            onKey(key)
                            if (withTimeoutOrNull(BACKSPACE_REPEAT_MS) { tryAwaitRelease() } != null) break
                        }
                    }
                },
            )
        }
    } else {
        Modifier.clickable { onKey(key) }
    }

    Box(
        // Gleichmässige Totzone ringsum: das Padding liegt VOR der Tap-Erkennung, ist
        // also nicht klickbar. Ein knapper Fehlgriff landet im Zwischenraum → kein
        // Zeichen statt falsches Zeichen. Zwischen zwei Tasten ergibt das 2 × KEY_GAP
        // tote Fläche. Grösser = weniger Fehltipper, aber kleinere Tasten — tunebar.
        modifier = modifier
            .padding(KEY_GAP)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(tapModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = if (key is CharKey) 22.sp else 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}
