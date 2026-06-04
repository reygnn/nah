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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.github.reygnn.nah.ui.NahTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = Settings())

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nah", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Einfinger-optimierte Schweizer Tastatur. Deterministisch, kein Autocorrect.",
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider()

            Button(onClick = onOpenSystemKeyboardSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Tastatur in den Systemeinstellungen aktivieren")
            }
            Button(onClick = onOpenImePicker, modifier = Modifier.fillMaxWidth()) {
                Text("Tastatur auswählen")
            }

            HorizontalDivider()

            SwitchRow(
                label = "Vorschlagsleiste",
                checked = settings.suggestionsEnabled,
                onChange = { value ->
                    scope.launch { repository.update { it.copy(suggestionsEnabled = value) } }
                },
            )
            SwitchRow(
                label = "Auto-Grossschreibung am Satzanfang",
                checked = settings.autoCapEnabled,
                onChange = { value ->
                    scope.launch { repository.update { it.copy(autoCapEnabled = value) } }
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
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
