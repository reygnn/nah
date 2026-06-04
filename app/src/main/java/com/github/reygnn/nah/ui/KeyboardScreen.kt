package com.github.reygnn.nah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.reygnn.nah.viewmodel.KeyboardUiState
import com.github.reygnn.nah.viewmodel.KeyboardViewModel

/** Abstand der Tasten zur Bildschirmunterkante (über die Gestenzone). Tunebar —
 *  am Pixel 9a einstellen: zu wenig = Kollision mit der Wischleiste, zu viel =
 *  unnötig hoher dunkler Streifen unten. */
private val BOTTOM_INSET: Dp = 48.dp

/** Einstiegspunkt, der den IME-Service mit dem ViewModel verbindet. */
@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NahTheme {
        KeyboardContent(
            state = state,
            onKey = viewModel::onKey,
            onSuggestion = viewModel::onSuggestionTap,
        )
    }
}

/** Reine, zustandslose Darstellung — direkt in Previews/Tests verwendbar. */
@Composable
fun KeyboardContent(
    state: KeyboardUiState,
    onKey: (com.github.reygnn.nah.layout.KeyboardKey) -> Unit,
    onSuggestion: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            // Fester Unterrand: schiebt die Tasten über die System-Gestenzone,
            // damit die unterste Reihe nicht mit den Wischgesten kollidiert. Der
            // Hintergrund (oben gesetzt) füllt weiter bis zur Unterkante, nur die
            // Tasten rücken hoch. Bewusst KEIN navigationBarsPadding() — das liess
            // in der IME-ComposeView die Höhe auf den Inset kollabieren (leere
            // Tastatur). Fester Wert ist deterministisch und tunebar.
            .padding(bottom = BOTTOM_INSET)
            .padding(2.dp),
    ) {
        if (state.suggestions.isNotEmpty()) {
            SuggestionBar(suggestions = state.suggestions, onSuggestion = onSuggestion)
        }
        state.layout.rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
            ) {
                row.forEach { key ->
                    TapKey(
                        key = key,
                        shift = state.shift,
                        modifier = Modifier
                            .weight(key.weight)
                            .fillMaxHeight(),
                        onKey = onKey,
                    )
                }
            }
        }
    }
}
