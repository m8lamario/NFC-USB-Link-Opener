package it.nfcusb.linkopener.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.nfcusb.linkopener.R
import it.nfcusb.linkopener.domain.ReaderPhase
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied)
    val emptyLogMessage = stringResource(R.string.nothing_to_copy)
    val shareTitle = stringResource(R.string.share_title)
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) viewModel.onNotificationsDenied()
        viewModel.start()
    }

    fun startReader() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.start()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(state)
            if (state.armed) {
                Button(onClick = viewModel::stop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_stop))
                }
                if (state.phase != ReaderPhase.Active && state.phase != ReaderPhase.WaitingPermission) {
                    OutlinedButton(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            } else {
                Button(onClick = ::startReader, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_start))
                }
            }
            DeviceSection(state, viewModel::selectDevice)
            OutlinedTextField(
                value = state.baudRateText,
                onValueChange = viewModel::onBaudChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_baud)) },
                supportingText = {
                    Text(
                        if (state.baudInvalid) {
                            stringResource(R.string.error_baud)
                        } else {
                            stringResource(R.string.hint_baud)
                        },
                    )
                },
                isError = state.baudInvalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.label_auto_open),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(
                    checked = state.autoOpen,
                    onCheckedChange = viewModel::onAutoOpenChange,
                )
            }
            OutlinedTextField(
                value = state.allowedDomainsText,
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
            Text(stringResource(R.string.label_log), style = MaterialTheme.typography.titleMedium)
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
                        if (state.logText.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar(emptyLogMessage) }
                        } else {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("log", state.logText))
                            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_copy))
                }
                OutlinedButton(
                    onClick = {
                        if (state.logText.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar(emptyLogMessage) }
                        } else {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, state.logText)
                            }
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_share))
                }
            }
            OutlinedButton(onClick = viewModel::simulate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_simulate))
            }
            Text(
                text = stringResource(R.string.hint_simulate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(state: ReaderUiState) {
    val container = when (state.phase) {
        ReaderPhase.Active -> MaterialTheme.colorScheme.primaryContainer
        ReaderPhase.WaitingPermission -> MaterialTheme.colorScheme.tertiaryContainer
        ReaderPhase.Unplugged -> MaterialTheme.colorScheme.errorContainer
        ReaderPhase.NotConnected -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (state.phase) {
        ReaderPhase.Active -> MaterialTheme.colorScheme.onPrimaryContainer
        ReaderPhase.WaitingPermission -> MaterialTheme.colorScheme.onTertiaryContainer
        ReaderPhase.Unplugged -> MaterialTheme.colorScheme.onErrorContainer
        ReaderPhase.NotConnected -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.label_status), style = MaterialTheme.typography.labelLarge)
            Text(state.statusLabel, style = MaterialTheme.typography.headlineSmall)
            state.errorMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSection(state: ReaderUiState, onSelect: (String) -> Unit) {
    when {
        state.devices.isEmpty() -> {
            Text(
                text = stringResource(R.string.log_no_device),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.devices.size == 1 -> {
            Text(
                text = stringResource(R.string.device_single, state.devices.first().label),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        else -> {
            var expanded by remember { mutableStateOf(false) }
            val selected = state.devices.find { it.key == state.selectedDeviceKey }?.label
                ?: stringResource(R.string.device_prompt)
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
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
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
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
