package com.github.reygnn.nah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.reygnn.nah.viewmodel.KeyboardUiState
import com.github.reygnn.nah.viewmodel.KeyboardViewModel

/** Einstiegspunkt, der den IME-Service mit dem ViewModel verbindet. */
@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    KeyboardContent(
        state = state,
        onKey = viewModel::onKey,
        onSuggestion = viewModel::onSuggestionTap,
    )
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
            .background(NahColors.Background)
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
                        modifier = Modifier.weight(key.weight),
                        onKey = onKey,
                    )
                }
            }
        }
    }
}
