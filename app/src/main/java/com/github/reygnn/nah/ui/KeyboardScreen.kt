package com.github.reygnn.nah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.reygnn.nah.viewmodel.KeyboardUiState
import com.github.reygnn.nah.viewmodel.KeyboardViewModel

/** **Untergrenze** für den Abstand der Tasten zur Bildschirmunterkante (über die Gestenzone).
 *  Auf das Pixel 9a (Gestennavigation) getunt: zu wenig = Kollision mit der Wischleiste, zu viel =
 *  unnötig hoher dunkler Streifen unten. Im Querformat deutlich kleiner, sonst frisst die
 *  fünfreihige Tastatur zu viel der knappen Höhe.
 *
 *  Der tatsächlich verwendete Abstand ist `max(diese Untergrenze, realer Navigationsleisten-Inset)`
 *  (siehe [KeyboardContent]). Der reale Inset wird dabei nur als **Wert** gelesen — NICHT über den
 *  `navigationBarsPadding()`-Modifier, der in der IME-ComposeView die Höhe auf den Inset kollabieren
 *  liess (leere Tastatur). Am Zielgerät dominiert die Untergrenze (Gesten-Inset < Untergrenze), das
 *  Verhalten bleibt also unverändert; auf einem Gerät mit höherer Leiste (3-Knopf-Navigation) trägt
 *  der reale Inset und die unterste Reihe kollidiert nicht mehr. Schlimmstenfalls ein No-op gleich
 *  dem alten festen Wert — nie schlechter. */
private val BOTTOM_INSET_PORTRAIT: Dp = 48.dp
private val BOTTOM_INSET_LANDSCAPE: Dp = 8.dp

/** Höhe einer Tastenreihe. Im Querformat niedriger, damit die fünf Reihen plus
 *  Vorschlagsleiste nicht über die verfügbare Höhe hinauswachsen. */
private val ROW_HEIGHT_PORTRAIT: Dp = 58.dp
private val ROW_HEIGHT_LANDSCAPE: Dp = 42.dp

/** Einstiegspunkt, der den IME-Service mit dem ViewModel verbindet. */
@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NahTheme {
        KeyboardContent(
            state = state,
            onKey = viewModel::onKey,
            onAlternative = viewModel::onAlternative,
            onSuggestion = viewModel::onSuggestionTap,
        )
    }
}

/** Reine, zustandslose Darstellung — direkt in Previews/Tests verwendbar. */
@Composable
fun KeyboardContent(
    state: KeyboardUiState,
    onKey: (com.github.reygnn.nah.layout.KeyboardKey) -> Unit,
    onAlternative: (String) -> Unit,
    onSuggestion: (String) -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Den realen Navigationsleisten-Inset NUR als Wert lesen (nicht via navigationBarsPadding(),
    // das die IME-Höhe kollabieren liess). Untergrenze ODER realer Inset, je nachdem was grösser
    // ist: am Pixel 9a dominiert die Untergrenze (unverändert), auf höheren Leisten der Inset.
    val systemNavBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floor = if (landscape) BOTTOM_INSET_LANDSCAPE else BOTTOM_INSET_PORTRAIT
    val bottomInset = maxOf(floor, systemNavBottom)
    val rowHeight = if (landscape) ROW_HEIGHT_LANDSCAPE else ROW_HEIGHT_PORTRAIT
    // Die Tastenanordnung ist optimizer-generiert und in LTR definiert; sie darf NIE von der
    // System-Locale gespiegelt werden. Eine RTL-Systemsprache (Arabisch/Hebräisch/…) würde sonst
    // jede reihenbasierte Row spiegeln und damit die gesamte optimierte Anordnung (das Kernver-
    // sprechen) umkehren — lautlos, weil letterPositions() rein aus Spalten-Indizes rechnet und
    // der Reise-Test es nicht merkt. Fest LTR erzwingen (umfasst auch die SuggestionBar darunter).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                .padding(bottom = bottomInset)
                .padding(2.dp),
        ) {
            // Sichtbar (mit fester Höhe), sobald die Funktion aktiv ist — auch ohne
            // aktuelle Vorschläge. So springen die Tasten beim Tippen nicht in der Höhe;
            // ist die Funktion aus, fehlt die Leiste ganz (kein verschwendeter Platz).
            if (state.suggestionBarVisible) {
                SuggestionBar(
                    suggestions = state.suggestions,
                    onSuggestion = onSuggestion,
                )
            }
            state.layout.rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                ) {
                    row.forEach { key ->
                        // Einfügen-Taste ist inaktiv, wenn die Zwischenablage keinen Text hat.
                        val enabled = !(
                            key is com.github.reygnn.nah.layout.FunctionKey &&
                                key.action == com.github.reygnn.nah.layout.KeyAction.PASTE &&
                                !state.pasteAvailable
                            )
                        TapKey(
                            key = key,
                            shift = state.shift,
                            colorHints = state.colorHints,
                            enabled = enabled,
                            modifier = Modifier
                                .weight(key.weight)
                                .fillMaxHeight(),
                            onKey = onKey,
                            onAlternative = onAlternative,
                        )
                    }
                }
            }
        }
    }
}
