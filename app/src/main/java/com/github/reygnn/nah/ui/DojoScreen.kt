package com.github.reygnn.nah.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.reygnn.nah.R
import com.github.reygnn.nah.layout.OptimizedLayout
import com.github.reygnn.nah.viewmodel.DojoLevel
import com.github.reygnn.nah.viewmodel.DojoMode
import com.github.reygnn.nah.viewmodel.DojoState
import com.github.reygnn.nah.viewmodel.DojoViewModel
import com.github.reygnn.nah.viewmodel.KeyboardUiState

/**
 * Das Tipp-Training („Dojo"). Trainiert die **Positionen** des optimierten Layouts: oben ein
 * Ziel + Punktestand, unten **dieselbe** [KeyboardContent] wie die echte Tastatur (identische
 * Geometrie → echtes Muskelgedächtnis). Keine Hilfen — Labels immer sichtbar, kein Highlight;
 * der Drill prüft nur den Tap. Das (eingefrorene) Layout wird ausschliesslich gelesen.
 */
@Composable
fun DojoScreen(viewModel: DojoViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Das Tastatur-Layout ist fix — einmal bauen. Keine Vorschlagsleiste, keine Lern-Farben,
    // Shift OFF: das Dojo zeigt die nackte echte Tastatur.
    val keyboardState = remember { KeyboardUiState(layout = OptimizedLayout.deCh()) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Oberer Bereich: Titel, Punktestand, Stufen/Modus-Wahl, Ziel.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.dojo_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Scoreboard(state)
                LevelChips(state.level, viewModel::setLevel)
                ModeChips(state.mode, viewModel::setMode)
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    TargetPrompt(state)
                }
            }

            // Unterer Bereich: die echte Tastatur. Taps/Long-Press laufen in den ViewModel,
            // Vorschläge sind ausgeschaltet.
            KeyboardContent(
                state = keyboardState,
                onKey = viewModel::onKey,
                onAlternative = viewModel::onAlternative,
                onSuggestion = {},
            )
        }
    }
}

@Composable
private fun Scoreboard(state: DojoState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.dojo_score, state.score),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.dojo_streak, state.streak),
            style = MaterialTheme.typography.titleMedium,
        )
        // Leben als Herzchen: gefüllt für verbleibende, leer für verbrauchte.
        Text(
            buildString {
                repeat(state.lives) { append("♥") }
                repeat(DojoState.MAX_LIVES - state.lives) { append("♡") }
            },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun LevelChips(selected: DojoLevel, onSelect: (DojoLevel) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (level in DojoLevel.entries) {
            FilterChip(
                selected = level == selected,
                onClick = { onSelect(level) },
                label = { Text(stringResource(level.labelRes())) },
            )
        }
    }
}

@Composable
private fun ModeChips(selected: DojoMode, onSelect: (DojoMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (mode in DojoMode.entries) {
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(mode.labelRes())) },
            )
        }
    }
}

/**
 * Das aktuelle Ziel, gross und zentriert. Game Over zeigt den Neustart-Hinweis. In der Wort-Stufe
 * wird der bereits korrekt getippte Anfang abgesetzt (Akzentfarbe), der Rest normal — so sieht man
 * den Fortschritt. Der Hintergrund blitzt bei Treffer/Fehler kurz grün/rot ([DojoState.lastResult]).
 */
@Composable
private fun TargetPrompt(state: DojoState) {
    val colors = MaterialTheme.colorScheme
    if (state.gameOver) {
        Text(
            stringResource(R.string.dojo_game_over),
            style = MaterialTheme.typography.titleLarge,
            color = colors.error,
        )
        return
    }
    val flash = when (state.lastResult) {
        true -> colors.primary
        false -> colors.error
        null -> colors.onSurface
    }
    val text = buildAnnotatedString {
        val typed = state.typed
        if (typed.isNotEmpty() && typed.length <= state.target.length) {
            withStyle(SpanStyle(color = colors.primary)) { append(state.target.substring(0, typed.length)) }
            withStyle(SpanStyle(color = colors.onSurface)) { append(state.target.substring(typed.length)) }
        } else {
            withStyle(SpanStyle(color = if (state.typed.isEmpty()) flash else colors.onSurface)) {
                append(state.target)
            }
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
    )
}

private fun DojoLevel.labelRes(): Int = when (this) {
    DojoLevel.VOWELS -> R.string.dojo_level_vowels
    DojoLevel.CONSONANTS -> R.string.dojo_level_consonants
    DojoLevel.ALPHABET -> R.string.dojo_level_alphabet
    DojoLevel.WORDS -> R.string.dojo_level_words
}

private fun DojoMode.labelRes(): Int = when (this) {
    DojoMode.RANDOM -> R.string.dojo_mode_random
    DojoMode.GUIDED -> R.string.dojo_mode_guided
}
