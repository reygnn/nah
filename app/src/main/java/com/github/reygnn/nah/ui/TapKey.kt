package com.github.reygnn.nah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.view.HapticFeedbackConstants
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
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
    colorHints: Boolean = false,
    onKey: (KeyboardKey) -> Unit,
) {
    val label = when (key) {
        is CharKey -> if (shift != ShiftState.OFF) key.char.uppercaseChar().toString() else key.char.toString()
        is FunctionKey -> key.label
    }

    // Backspace, Shift und Return zeigen ein Material-Vektor-Icon statt der
    // Unicode-Glyphe; alle anderen Tasten (Space, ?123, ABC, ., ,) bleiben Text.
    // Das Shift-Icon trägt den Zustand selbst (kein Sonderfarben mehr nötig):
    // Kontur-Pfeil = aus, gefüllter Pfeil = armiert, Capslock = Caps Lock.
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

    // Farben aus dem Material-3-Schema (Material You, dynamisch). Neutrale
    // Container-Tiers: Buchstaben heller/erhabener als Funktionstasten. Der
    // Shift-Zustand läuft jetzt allein übers Icon, nicht mehr über Sonderfarben.
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

    // Leichtes haptisches Feedback beim Tasten-Tap — hilft beim Ein-Finger-Tippen
    // ohne Hinsehen. KEYBOARD_TAP respektiert die System-Einstellung („Haptik beim
    // Tippen"), ist also de facto dort opt-in; eine eigene App-Option gibt es bewusst nicht.
    val view = LocalView.current
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onKey(key)
    }

    val isBackspace = key is FunctionKey && key.action == KeyAction.BACKSPACE
    // Backspace löscht beim Gedrückthalten fortlaufend; alle anderen Tasten sind
    // ein simpler Tap. Erst einmal sofort löschen, dann — falls nach der
    // Anfangsverzögerung noch gehalten — im Repeat-Takt, bis losgelassen wird. Die
    // Haptik nur beim ersten Druck, nicht bei jeder Wiederhol-Löschung (sonst Gebrumm).
    val tapModifier = if (isBackspace) {
        Modifier.pointerInput(key) {
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
    } else {
        Modifier.clickable { tap() }
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
    }
}
