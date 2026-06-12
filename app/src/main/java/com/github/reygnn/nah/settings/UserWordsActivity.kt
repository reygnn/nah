package com.github.reygnn.nah.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.reygnn.nah.R
import com.github.reygnn.nah.data.suggestions.LearnedWordRepository
import com.github.reygnn.nah.data.suggestions.UserWordError
import com.github.reygnn.nah.data.suggestions.UserWordRepository
import com.github.reygnn.nah.data.suggestions.UserWordValidation
import com.github.reygnn.nah.ui.NahTheme
import kotlinx.coroutines.launch

/** Verwaltung der eigenen Wörter: oben die **kuratierten** (hinzufügen/bearbeiten/entfernen, werden
 *  wörtlich committet), darunter die beim Tippen **gelernten** (nur entfernen, werden wie Wörterbuch-
 *  Wörter an Shift/Caps angepasst). */
class UserWordsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = UserWordRepository(applicationContext)
        val learnedRepository = LearnedWordRepository(applicationContext)
        setContent {
            NahTheme {
                UserWordsScreen(repository = repository, learnedRepository = learnedRepository)
            }
        }
    }
}

@Composable
fun UserWordsScreen(repository: UserWordRepository, learnedRepository: LearnedWordRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val words by repository.words.collectAsStateWithLifecycle(initialValue = emptySet())
    val learnedWords by learnedRepository.words.collectAsStateWithLifecycle(initialValue = emptySet())
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        // Eine scrollbare Liste über beide Abschnitte (kuratiert + gelernt), damit sie zusammen
        // scrollen statt verschachtelter LazyColumns. Statische Kopfzeilen liegen als eigene items.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() // sonst kollidiert der Titel mit der Statusleiste (edge-to-edge)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(stringResource(R.string.user_words_title), style = MaterialTheme.typography.headlineMedium)
            }
            item {
                Text(stringResource(R.string.user_words_description), style = MaterialTheme.typography.bodyMedium)
            }
            item {
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
                        label = { Text(stringResource(R.string.user_words_add_label)) },
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
                                    error = reason.message(context)
                                }
                            }
                        },
                        enabled = input.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.user_words_add_button))
                    }
                }
            }

            item { HorizontalDivider() }

            if (words.isEmpty()) {
                item {
                    Text(stringResource(R.string.user_words_empty), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(words.sortedBy { it.lowercase() }, key = { "curated:$it" }) { word ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Antippen zum Korrigieren — grosser Tap-Bereich (Fat-Finger).
                        Text(
                            word,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editing = word }
                                .padding(vertical = 12.dp),
                        )
                        TextButton(onClick = { scope.launch { repository.remove(word) } }) {
                            Text(stringResource(R.string.user_words_remove))
                        }
                    }
                }
            }

            // Abschnitt „Gelernte Wörter": beim Tippen gespeichert, werden wie Wörterbuch-Wörter
            // gecast (nicht wörtlich). Nur entfernbar — neue kommen über das Lesezeichen-Chip dazu.
            item { HorizontalDivider() }
            item {
                Text(
                    stringResource(R.string.user_words_learned_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Text(
                    stringResource(R.string.user_words_learned_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (learnedWords.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.user_words_learned_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(learnedWords.sortedBy { it.lowercase() }, key = { "learned:$it" }) { word ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            word,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 12.dp),
                        )
                        TextButton(onClick = { scope.launch { learnedRepository.remove(word) } }) {
                            Text(stringResource(R.string.user_words_remove))
                        }
                    }
                }
            }
        }
    }

    val target = editing
    if (target != null) {
        var draft by remember(target) { mutableStateOf(target) }
        var dialogError by remember(target) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.user_words_edit_title)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; dialogError = null },
                    singleLine = true,
                    isError = dialogError != null,
                    supportingText = dialogError?.let { msg -> { Text(msg) } },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val reason = repository.update(target, draft)
                            if (reason == null) editing = null else dialogError = reason.message(context)
                        }
                    },
                    enabled = draft.isNotBlank(),
                ) { Text(stringResource(R.string.user_words_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text(stringResource(R.string.user_words_cancel)) }
            },
        )
    }
}

private fun UserWordError.message(context: Context): String = when (this) {
    UserWordError.TooShort ->
        context.getString(R.string.user_word_error_too_short, UserWordValidation.MIN_LENGTH)
    UserWordError.TooLong ->
        context.getString(R.string.user_word_error_too_long, UserWordValidation.MAX_LENGTH)
    UserWordError.InvalidCharacters -> context.getString(R.string.user_word_error_invalid)
    UserWordError.AlreadyExists -> context.getString(R.string.user_word_error_exists)
}
