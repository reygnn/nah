package com.github.reygnn.nah.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.reygnn.nah.data.suggestions.UserWordError
import com.github.reygnn.nah.data.suggestions.UserWordRepository
import com.github.reygnn.nah.ui.NahTheme
import kotlinx.coroutines.launch

/** Minimale Verwaltung der eigenen Wörter: hinzufügen, auflisten, einzeln entfernen. */
class UserWordsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = UserWordRepository(applicationContext)
        setContent {
            NahTheme {
                UserWordsScreen(repository = repository)
            }
        }
    }
}

@Composable
fun UserWordsScreen(repository: UserWordRepository) {
    val scope = rememberCoroutineScope()
    val words by repository.words.collectAsStateWithLifecycle(initialValue = emptySet())
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() // sonst kollidiert der Titel mit der Statusleiste (edge-to-edge)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Eigene Wörter", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Erscheinen in der Vorschlagsleiste, sobald „Eigene Wörter vorschlagen“ " +
                    "aktiviert ist. Sie ersetzen nie fertigen Text — nur auf Antippen.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Wort hinzufügen") },
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                )
                Button(
                    onClick = {
                        val raw = input
                        scope.launch {
                            val reason = repository.add(raw)
                            if (reason == null) {
                                input = ""
                                error = null
                            } else {
                                error = reason.message()
                            }
                        }
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Text("Hinzufügen")
                }
            }

            HorizontalDivider()

            if (words.isEmpty()) {
                Text("Noch keine eigenen Wörter.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(words.sortedBy { it.lowercase() }, key = { it }) { word ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(word, style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { scope.launch { repository.remove(word) } }) {
                                Text("Entfernen")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun UserWordError.message(): String = when (this) {
    UserWordError.TooShort -> "Mindestens 2 Zeichen"
    UserWordError.TooLong -> "Höchstens 50 Zeichen"
    UserWordError.InvalidCharacters -> "Nur Buchstaben (oder eine E-Mail-Adresse)"
    UserWordError.AlreadyExists -> "Schon in der Liste"
}
