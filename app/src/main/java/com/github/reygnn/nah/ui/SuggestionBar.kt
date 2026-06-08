package com.github.reygnn.nah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.reygnn.nah.data.suggestions.SuggestionRepository

/**
 * Nicht-eingreifende Vorschlagsleiste. Antippen eines Vorschlags ersetzt nur das aktuelle
 * unfertige Präfix (siehe
 * [com.github.reygnn.nah.viewmodel.KeyboardViewModel.onSuggestionTap]) — nie ein fertiges
 * Wort. Sie reserviert feste Höhe, damit die Tasten beim Tippen nicht springen, und ist
 * insgesamt nur sichtbar, wenn die Vorschlagsfunktion aktiviert ist (standardmässig aus).
 *
 * Die Einstellungen erreicht man NICHT mehr von hier (früher der Hamburger links): der Zugang
 * sitzt jetzt per Long-Press auf der Ebenen-Umschalttaste (SYM/ABC, siehe [com.github.reygnn.nah.layout.KeyAction.SETTINGS]),
 * damit er auch erreichbar bleibt, wenn diese Leiste mangels Vorschlägen gar nicht da ist.
 *
 * Horizontal scrollbar ([LazyRow]): die Chips sind content-breit (jedes Wort ganz sichtbar, kein
 * Abschneiden) und links gepackt; passen mehr Vorschläge als auf den Schirm, scrollt man sie heran.
 * So darf [SuggestionRepository.MAX_SUGGESTIONS] über 3 liegen, ohne dass die Chips quetschen.
 */
@Composable
fun SuggestionBar(
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Geteilte Obergrenze (kein hartcodiertes „3"): die Datenquelle kappt bereits auf
        // SuggestionRepository.MAX_SUGGESTIONS, hier dieselbe Konstante als zweite Sicherung.
        items(suggestions.take(SuggestionRepository.MAX_SUGGESTIONS)) { word ->
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onSuggestion(word) }
                    // Grosszügiges horizontales Padding: hält den Tap-Bereich auch bei kurzen
                    // Wörtern fingerfreundlich (content-breite Chips, kein weight mehr).
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = word,
                    color = MaterialTheme.colorScheme.onSurface,
                    // Wie die Tasten bewusst NICHT mit der System-Schriftskalierung wachsend
                    // (siehe nonScaledSp): die Leiste hat eine fixe Höhe (44.dp), skalierte `sp`
                    // würde den Vorschlag bei grossem Font-Scale vertikal abschneiden. Breite ist
                    // hier content-bestimmt, das Wort steht also immer ganz da (kein Ellipsis nötig).
                    fontSize = nonScaledSp(16.dp),
                    maxLines = 1,
                )
            }
        }
    }
}
