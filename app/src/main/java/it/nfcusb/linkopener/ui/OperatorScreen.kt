package it.nfcusb.linkopener.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.nfcusb.linkopener.R
import it.nfcusb.linkopener.domain.OperatorStatus
import it.nfcusb.linkopener.domain.VisualOutcome

private val SuccessContainer = Color(0xFFD7F5E5)
private val SuccessContent = Color(0xFF0D5C3D)
private val DuplicateContainer = Color(0xFFD3E3FD)
private val DuplicateContent = Color(0xFF0B3F8A)
private val FailureContainer = Color(0xFFF9DEDC)
private val FailureContent = Color(0xFF8C1D18)
private val FlashGreen = Color(0xFF1B8A4A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorScreen(
    viewModel: OperatorViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var askedNotifications by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) viewModel.onNotificationsDenied()
        if (!viewModel.state.value.armed) viewModel.start()
    }
    val flash = remember { Animatable(0f) }
    val successToken = state.lastReading?.takeIf { it.outcome == VisualOutcome.Success }?.successToken ?: 0
    LaunchedEffect(successToken) {
        if (successToken == 0) return@LaunchedEffect
        flash.snapTo(0.42f)
        flash.animateTo(0f, tween(durationMillis = 700))
    }
    LaunchedEffect(state.armed) {
        if (state.armed && needsNotificationPermission(context) && !askedNotifications) {
            askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(FlashGreen.copy(alpha = flash.value))
            },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.open_settings),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.statusLabel,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    color = statusColor(state.status),
                )
                ReadingCard(state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Counter(stringResource(R.string.counter_valid), state.counters.valid)
                    Counter(stringResource(R.string.counter_duplicates), state.counters.duplicates)
                    Counter(stringResource(R.string.counter_errors), state.counters.errors)
                }
                Button(
                    onClick = {
                        if (state.armed) return@Button
                        if (needsNotificationPermission(context) && !askedNotifications) {
                            askedNotifications = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.start()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                ) {
                    Text(
                        text = stringResource(if (state.armed) R.string.reader_running else R.string.action_start),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                TextButton(onClick = onOpenHistory) {
                    Text(stringResource(R.string.open_history))
                }
            }
        }
    }
}

@Composable
private fun ReadingCard(state: OperatorUiState) {
    val reading = state.lastReading
    val container: Color
    val content: Color
    when (reading?.outcome) {
        VisualOutcome.Success -> {
            container = SuccessContainer
            content = SuccessContent
        }
        VisualOutcome.Duplicate -> {
            container = DuplicateContainer
            content = DuplicateContent
        }
        VisualOutcome.Failure -> {
            container = FailureContainer
            content = FailureContent
        }
        null -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = reading?.code ?: "—",
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = reading?.detail ?: stringResource(R.string.no_reading),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (reading != null) {
                Text(
                    text = formatTime(reading.atMs),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value.toString(), style = MaterialTheme.typography.headlineMedium)
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun statusColor(status: OperatorStatus): Color {
    return when (status) {
        OperatorStatus.Disconnected,
        OperatorStatus.ReadError,
        -> MaterialTheme.colorScheme.error
        OperatorStatus.Ready,
        OperatorStatus.WaitingTag,
        -> MaterialTheme.colorScheme.primary
        OperatorStatus.ConnectReader -> MaterialTheme.colorScheme.onSurface
    }
}

private fun needsNotificationPermission(context: android.content.Context): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
}
