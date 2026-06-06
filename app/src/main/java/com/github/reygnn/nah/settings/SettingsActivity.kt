package com.github.reygnn.nah.settings

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.github.reygnn.nah.BuildConfig
import com.github.reygnn.nah.R
import com.github.reygnn.nah.ui.NahTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.provider.Settings as AndroidSettings
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SettingsRepository(applicationContext)
        setContent {
            NahTheme {
                SettingsScreen(
                    repository = repository,
                    onOpenImePicker = {
                        getSystemService<InputMethodManager>()?.showInputMethodPicker()
                    },
                    onOpenSystemKeyboardSettings = {
                        startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onManageUserWords = {
                        startActivity(Intent(this, UserWordsActivity::class.java))
                    },
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onOpenImePicker: () -> Unit,
    onOpenSystemKeyboardSettings: () -> Unit,
    onManageUserWords: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = Settings())

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() // sonst kollidiert der Titel mit der Statusleiste (edge-to-edge)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.settings_title, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(R.string.settings_tagline),
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider()

            Button(onClick = onOpenSystemKeyboardSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_enable_in_system))
            }
            Button(onClick = onOpenImePicker, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_pick_keyboard))
            }

            HorizontalDivider()

            SwitchRow(
                label = stringResource(R.string.settings_suggestion_bar),
                checked = settings.suggestionsEnabled,
                onChange = { value ->
                    scope.launch { repository.update { it.copy(suggestionsEnabled = value) } }
                },
            )
            SwitchRow(
                label = stringResource(R.string.settings_suggest_user_words),
                checked = settings.userWordsEnabled,
                onChange = { value ->
                    scope.launch { repository.update { it.copy(userWordsEnabled = value) } }
                },
            )
            Button(onClick = onManageUserWords, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_manage_user_words))
            }
            SwitchRow(
                label = stringResource(R.string.settings_auto_cap),
                checked = settings.autoCapEnabled,
                onChange = { value ->
                    scope.launch { repository.update { it.copy(autoCapEnabled = value) } }
                },
            )

            HorizontalDivider()

            SwitchRow(
                label = stringResource(R.string.settings_color_hints),
                checked = settings.letterColorHintsEnabled,
                onChange = { value ->
                    scope.launch { repository.update { it.copy(letterColorHintsEnabled = value) } }
                },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // weight(1f): das Label nimmt die Restbreite und umbricht bei Bedarf zweizeilig, statt
        // den Switch zu verdrängen (das längste Label „Lern-Farben (Vokale & häufige Konsonanten)"
        // passt einzeilig nicht). end-Padding hält Text und Switch auseinander.
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
