package com.github.reygnn.nah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.reygnn.nah.layout.CharKey
import com.github.reygnn.nah.layout.FunctionKey
import com.github.reygnn.nah.layout.KeyAction
import com.github.reygnn.nah.layout.KeyboardKey
import com.github.reygnn.nah.viewmodel.ShiftState

/** Nicht klickbarer Rand ringsum jede Taste (Totzone gegen Fehltipper). */
private val KEY_GAP = 5.dp

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
    val shiftActive = isShiftKey && shift != ShiftState.OFF

    val label = when (key) {
        is CharKey -> if (shift != ShiftState.OFF) key.char.uppercaseChar().toString() else key.char.toString()
        is FunctionKey -> key.label
    }

    val bg = when {
        shiftActive -> NahColors.Accent
        key is CharKey -> NahColors.CharKey
        else -> NahColors.FunctionKey
    }
    val fg = if (key is CharKey) NahColors.OnKey else NahColors.OnKeyDim

    Box(
        // Gleichmässige Totzone ringsum: das Padding liegt VOR clickable, ist also
        // nicht klickbar. Ein knapper Fehlgriff landet im Zwischenraum → kein
        // Zeichen statt falsches Zeichen. Zwischen zwei Tasten ergibt das 2 × KEY_GAP
        // tote Fläche. Grösser = weniger Fehltipper, aber kleinere Tasten — tunebar.
        modifier = modifier
            .padding(KEY_GAP)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onKey(key) },
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
