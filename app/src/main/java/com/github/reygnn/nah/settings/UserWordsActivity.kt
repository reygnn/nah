package com.github.reygnn.nah.settings

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
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
import com.github.reygnn.nah.data.suggestions.WordBackup
import com.github.reygnn.nah.ui.NahTheme
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "UserWordsActivity"

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
    // Welcher Eintrag wird gerade bearbeitet — inkl. aus welchem Store (kuratiert vs. gelernt), damit
    // der Bearbeiten-Dialog beim Speichern das richtige Repository trifft.
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    // Backup via the system file picker (SAF) — no storage permission, the user picks the location.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(WordBackup.MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val message = exportWords(context, uri, repository, learnedRepository)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val message = importWords(context, uri, repository, learnedRepository)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

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
                Text(
                    stringResource(R.string.user_words_backup_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            // Check before opening the picker: CreateDocument already creates the
                            // file, and an empty export must never be offered as a backup target.
                            scope.launch {
                                if (repository.words.first().isEmpty() && learnedRepository.words.first().isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.user_words_export_empty),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    exportLauncher.launch(WordBackup.fileName(Instant.now(), ZoneId.systemDefault()))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.user_words_export)) }
                    OutlinedButton(
                        // Some providers report .json as text/plain or octet-stream — accept those
                        // too; decode() rejects anything that isn't a nah backup anyway.
                        onClick = {
                            importLauncher.launch(
                                arrayOf(WordBackup.MIME_TYPE, "text/plain", "application/octet-stream"),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.user_words_import)) }
                }
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
                                .clickable { editing = EditTarget(word, learned = false) }
                                .padding(vertical = 12.dp),
                        )
                        TextButton(onClick = { scope.launch { repository.remove(word) } }) {
                            Text(stringResource(R.string.user_words_remove))
                        }
                    }
                }
            }

            // Abschnitt „Gelernte Wörter": beim Tippen gespeichert, werden wie Wörterbuch-Wörter
            // gecast (nicht wörtlich). Antippen korrigiert die Schreibweise (z. B. wenn die Basis durch
            // Auto-Cap am Satzanfang gross eingefangen wurde); neue kommen über das Lesezeichen-Chip dazu.
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
                        // Wie kuratiert: Antippen zum Korrigieren der Schreibweise.
                        Text(
                            word,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editing = EditTarget(word, learned = true) }
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
        var draft by remember(target) { mutableStateOf(target.word) }
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
                            // Denselben Eintrag im richtigen Store aktualisieren (beide erben update()
                            // aus StringSetWordStore).
                            val reason = if (target.learned) {
                                learnedRepository.update(target.word, draft)
                            } else {
                                repository.update(target.word, draft)
                            }
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

/** Writes both lists to [uri] as JSON; returns the user-facing result message. */
private suspend fun exportWords(
    context: Context,
    uri: Uri,
    repository: UserWordRepository,
    learnedRepository: LearnedWordRepository,
): String {
    val userWords = repository.words.first()
    val learnedWords = learnedRepository.words.first()
    // Both empty is far more likely a lost store (a corrupt file is silently reset to empty, a read
    // IOException is caught to empty) than a real state worth saving — writing it could overwrite
    // the user's only good backup, so refuse instead.
    if (userWords.isEmpty() && learnedWords.isEmpty()) {
        return context.getString(R.string.user_words_export_empty)
    }
    val json = WordBackup.encode(userWords, learnedWords, Instant.now(), ZoneId.systemDefault())
    return try {
        withContext(Dispatchers.IO) {
            // "wt" truncates when overwriting an existing file, so no stale tail remains.
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                ?: throw IOException("No output stream for $uri")
        }
        context.getString(R.string.user_words_export_done, userWords.size, learnedWords.size)
    } catch (e: IOException) {
        Log.w(TAG, "Export failed", e)
        context.getString(R.string.user_words_export_failed)
    } catch (e: SecurityException) {
        Log.w(TAG, "Export failed", e)
        context.getString(R.string.user_words_export_failed)
    }
}

/** Reads a backup from [uri] and merges it into both stores (add-only); returns the result message. */
private suspend fun importWords(
    context: Context,
    uri: Uri,
    repository: UserWordRepository,
    learnedRepository: LearnedWordRepository,
): String {
    val text: String? = try {
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("No input stream for $uri")
            input.use {
                // Read one byte past the cap to detect oversized files without loading them whole;
                // null = too large.
                val bytes = it.readNBytes(WordBackup.MAX_FILE_BYTES + 1)
                if (bytes.size > WordBackup.MAX_FILE_BYTES) null else bytes.decodeToString()
            }
        }
    } catch (e: IOException) {
        Log.w(TAG, "Import failed", e)
        return context.getString(R.string.user_words_import_failed)
    } catch (e: SecurityException) {
        Log.w(TAG, "Import failed", e)
        return context.getString(R.string.user_words_import_failed)
    }
    if (text == null) return context.getString(R.string.user_words_import_too_large)
    val backup = WordBackup.decode(text) ?: return context.getString(R.string.user_words_import_invalid)
    val addedUser = repository.addAll(backup.userWords)
    val addedLearned = learnedRepository.addAll(backup.learnedWords)
    return context.getString(R.string.user_words_import_done, addedUser, addedLearned)
}

/** Der gerade im Dialog bearbeitete Eintrag samt Herkunft: [learned] = true → gelernter Store
 *  ([LearnedWordRepository]), sonst kuratiert ([UserWordRepository]). Steuert, welches Repository
 *  das Speichern trifft. */
private data class EditTarget(val word: String, val learned: Boolean)

private fun UserWordError.message(context: Context): String = when (this) {
    UserWordError.TooShort ->
        context.getString(R.string.user_word_error_too_short, UserWordValidation.MIN_LENGTH)
    UserWordError.TooLong ->
        context.getString(R.string.user_word_error_too_long, UserWordValidation.MAX_LENGTH)
    UserWordError.InvalidCharacters -> context.getString(R.string.user_word_error_invalid)
    UserWordError.AlreadyExists -> context.getString(R.string.user_word_error_exists)
}
