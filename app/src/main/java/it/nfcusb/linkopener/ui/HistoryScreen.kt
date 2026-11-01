package it.nfcusb.linkopener.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.nfcusb.linkopener.R
import it.nfcusb.linkopener.domain.ScanEvent
import it.nfcusb.linkopener.domain.ScanOutcome
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    val blocked = stringResource(R.string.error_link_blocked)
    val exported = stringResource(R.string.exported)
    val shareTitle = stringResource(R.string.share_history)
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = viewModel.exportCsv()
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(csv.toByteArray(Charsets.UTF_8))
            }
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
                    viewModel.clear()
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
                title = { Text(stringResource(R.string.history_title)) },
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
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.search_code)) },
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { saveCsv.launch("letture.csv") }) {
                    Text(stringResource(R.string.action_export_csv))
                }
                TextButton(onClick = {
                    scope.launch {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_TEXT, viewModel.exportCsv())
                        }
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    }
                }) { Text(stringResource(R.string.action_share)) }
                TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.action_clear_history))
                }
            }
            if (events.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(events, key = { it.id }) { event ->
                        HistoryRow(event) {
                            if (!viewModel.reopen(event)) {
                                scope.launch { snackbar.showSnackbar(blocked) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(event: ScanEvent, onReopen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.code, style = MaterialTheme.typography.headlineSmall)
            Text(outcomeLabel(event.outcome), style = MaterialTheme.typography.labelLarge)
            Text(formatDateTime(event.timestampMs), style = MaterialTheme.typography.bodyMedium)
            Text(event.url, style = MaterialTheme.typography.bodySmall)
            if (event.detail.isNotBlank()) {
                Text(event.detail, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onReopen) {
                Text(stringResource(R.string.action_reopen))
            }
        }
    }
}

@Composable
private fun outcomeLabel(outcome: ScanOutcome): String {
    return when (outcome) {
        ScanOutcome.Opened -> stringResource(R.string.history_opened)
        ScanOutcome.Duplicate -> stringResource(R.string.history_duplicate)
        ScanOutcome.Rejected -> stringResource(R.string.history_rejected)
        ScanOutcome.Error -> stringResource(R.string.history_error)
    }
}
