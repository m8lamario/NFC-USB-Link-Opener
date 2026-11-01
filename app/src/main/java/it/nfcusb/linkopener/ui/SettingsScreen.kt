package it.nfcusb.linkopener.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.nfcusb.linkopener.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    var baudText by remember { mutableStateOf(state.settings.baudRate.toString()) }
    var debounceText by remember { mutableStateOf(state.settings.debounceMs.toString()) }
    val copied = stringResource(R.string.copied)
    val exported = stringResource(R.string.exported)
    val shareLog = stringResource(R.string.share_log)
    val shareHistory = stringResource(R.string.share_history)
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = viewModel.exportCsv()
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            snackbar.showSnackbar(exported)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.section_reader))
            Text(state.technicalStatus, style = MaterialTheme.typography.titleMedium)
            state.technicalMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            DevicePicker(state, viewModel::selectDevice)
            OutlinedTextField(
                value = baudText,
                onValueChange = {
                    baudText = it.filter { char -> char.isDigit() }.take(7)
                    viewModel.onBaudChange(baudText)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_baud)) },
                supportingText = { Text(stringResource(R.string.hint_baud)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedButton(onClick = viewModel::reconnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_reconnect))
            }
            if (state.settings.armed) {
                OutlinedButton(onClick = viewModel::stopReader, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_stop))
                }
            }

            SectionTitle(stringResource(R.string.section_security))
            ToggleRow(stringResource(R.string.label_whitelist), state.settings.whitelistEnabled, viewModel::onWhitelistChange)
            OutlinedTextField(
                value = state.settings.allowedDomainsText,
                onValueChange = viewModel::onDomainsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_domains)) },
                supportingText = {
                    Text(
                        if (state.domainsInvalid) {
                            stringResource(R.string.invalid_domain_lines)
                        } else {
                            stringResource(R.string.hint_domains)
                        },
                    )
                },
                isError = state.domainsInvalid,
                minLines = 3,
            )
            ToggleRow(
                stringResource(R.string.label_open_unlisted),
                state.settings.openUnlisted,
                viewModel::onOpenUnlistedChange,
            )
            OutlinedTextField(
                value = state.settings.codePattern,
                onValueChange = viewModel::onPatternChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_pattern)) },
                supportingText = {
                    Text(
                        if (state.patternInvalid) {
                            stringResource(R.string.invalid_pattern)
                        } else {
                            stringResource(R.string.hint_pattern)
                        },
                    )
                },
                isError = state.patternInvalid,
                singleLine = true,
            )

            SectionTitle(stringResource(R.string.section_behavior))
            ToggleRow(stringResource(R.string.label_auto_open), state.settings.autoOpen, viewModel::onAutoOpenChange)
            ToggleRow(stringResource(R.string.label_start_on_attach), state.settings.startOnAttach, viewModel::onStartOnAttachChange)
            OutlinedTextField(
                value = debounceText,
                onValueChange = {
                    debounceText = it.filter { char -> char.isDigit() }.take(5)
                    viewModel.onDebounceChange(debounceText)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_debounce)) },
                supportingText = { Text(stringResource(R.string.hint_debounce)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            ToggleRow(stringResource(R.string.label_vibration), state.settings.vibrationEnabled, viewModel::onVibrationChange)
            ToggleRow(stringResource(R.string.label_sound), state.settings.soundEnabled, viewModel::onSoundChange)
            Text(stringResource(R.string.label_browser), style = MaterialTheme.typography.titleSmall)
            RadioRow(
                label = stringResource(R.string.browser_external),
                selected = !state.settings.internalBrowser,
                onClick = { viewModel.onInternalBrowserChange(false) },
            )
            RadioRow(
                label = stringResource(R.string.browser_internal),
                selected = state.settings.internalBrowser,
                onClick = { viewModel.onInternalBrowserChange(true) },
            )
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_clear_history))
            }
            OutlinedButton(onClick = { saveCsv.launch("letture.csv") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_export_csv))
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_TEXT, viewModel.exportCsv())
                        }
                        context.startActivity(Intent.createChooser(send, shareHistory))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_share_data)) }

            SectionTitle(stringResource(R.string.section_diagnostics))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.logText.ifBlank { stringResource(R.string.log_empty) },
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("log", state.logText))
                        scope.launch { snackbar.showSnackbar(copied) }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_copy)) }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, state.logText)
                        }
                        context.startActivity(Intent.createChooser(send, shareLog))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_share)) }
            }
            Text(stringResource(R.string.simulation_title), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = viewModel::simulateValid, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sim_valid))
            }
            OutlinedButton(onClick = viewModel::simulateDuplicate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sim_duplicate))
            }
            OutlinedButton(onClick = viewModel::simulateInvalid, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sim_invalid))
            }
            OutlinedButton(onClick = viewModel::simulateUnauthorized, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sim_domain))
            }
            OutlinedButton(onClick = viewModel::simulateDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sim_disconnect))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePicker(state: SettingsUiState, onSelect: (String) -> Unit) {
    when {
        state.devices.isEmpty() -> Text(stringResource(R.string.log_no_device))
        state.devices.size == 1 -> Text(stringResource(R.string.device_single, state.devices.first().label))
        else -> {
            var expanded by remember { mutableStateOf(false) }
            val selected = state.devices.find { it.key == state.settings.selectedDeviceKey }?.label
                ?: stringResource(R.string.device_prompt)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selected,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.label_device)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.devices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.label) },
                            onClick = {
                                onSelect(device.key)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}
