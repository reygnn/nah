package com.github.reygnn.nah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.reygnn.nah.R

/**
 * Nicht-eingreifende Vorschlagsleiste mit einem Hamburger-Button links, der die
 * Einstellungen öffnet. Der Button füllt den linken Rand, sodass die Leiste nie
 * komplett leer wirkt, wenn gerade keine Vorschläge da sind (sie reserviert feste
 * Höhe, damit die Tasten beim Tippen nicht springen). Antippen eines Vorschlags
 * ersetzt nur das aktuelle unfertige Präfix (siehe
 * [com.github.reygnn.nah.viewmodel.KeyboardViewModel.onSuggestionTap]) — nie ein
 * fertiges Wort. Die Leiste ist insgesamt nur sichtbar, wenn die Vorschlagsfunktion
 * aktiviert ist (standardmässig aus).
 */
@Composable
fun SuggestionBar(
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hamburger → Einstellungen. Quadratischer Tap-Bereich am linken Rand.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clickable { onOpenSettings() }
                .padding(11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NahIcons.Menu,
                contentDescription = stringResource(R.string.settings_open_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        suggestions.take(3).forEach { word ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSuggestion(word) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = word,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
