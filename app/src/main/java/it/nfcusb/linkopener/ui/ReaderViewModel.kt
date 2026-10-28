package it.nfcusb.linkopener.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.nfcusb.linkopener.domain.BaudRates
import it.nfcusb.linkopener.domain.LogEntry
import it.nfcusb.linkopener.domain.ReaderPhase
import it.nfcusb.linkopener.domain.SerialDeviceChoice
import it.nfcusb.linkopener.parse.UrlLineParser
import it.nfcusb.linkopener.session.ReaderRuntime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ReaderUiState(
    val phase: ReaderPhase,
    val armed: Boolean,
    val statusLabel: String,
    val errorMessage: String?,
    val devices: List<SerialDeviceChoice>,
    val selectedDeviceKey: String?,
    val baudRateText: String,
    val baudInvalid: Boolean,
    val autoOpen: Boolean,
    val allowedDomainsText: String,
    val domainsInvalid: Boolean,
    val logText: String,
)

class ReaderViewModel(
    private val runtime: ReaderRuntime,
) : ViewModel() {
    private val baudDraft = kotlinx.coroutines.flow.MutableStateFlow(runtime.settings.value.baudRate.toString())
    private val domainsDraft = kotlinx.coroutines.flow.MutableStateFlow(runtime.settings.value.allowedDomainsText)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    val state: StateFlow<ReaderUiState> = combine(
        runtime.snapshot,
        baudDraft,
        domainsDraft,
    ) { snapshot, baud, domains ->
        ReaderUiState(
            phase = snapshot.phase,
            armed = snapshot.settings.armed,
            statusLabel = runtime.statusLabel(snapshot.phase),
            errorMessage = snapshot.errorMessage,
            devices = snapshot.devices,
            selectedDeviceKey = snapshot.settings.selectedDeviceKey,
            baudRateText = baud,
            baudInvalid = BaudRates.parse(baud) == null,
            autoOpen = snapshot.settings.autoOpen,
            allowedDomainsText = domains,
            domainsInvalid = UrlLineParser.invalidDomainLines(domains).isNotEmpty(),
            logText = formatLog(snapshot.log),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currentState())

    fun start() = runtime.arm()

    fun stop() = runtime.disarm()

    fun retry() = runtime.retry()

    fun selectDevice(key: String) = runtime.selectDevice(key)

    fun onBaudChange(text: String) {
        val digits = text.filter { it.isDigit() }.take(7)
        baudDraft.value = digits
        BaudRates.parse(digits)?.let(runtime::updateBaud)
    }

    fun onAutoOpenChange(enabled: Boolean) = runtime.updateAutoOpen(enabled)

    fun onDomainsChange(text: String) {
        domainsDraft.value = text
        runtime.updateDomains(text)
    }

    fun onNotificationsDenied() = runtime.onNotificationsDenied()

    fun simulate() = runtime.simulate()

    private fun currentState(): ReaderUiState {
        val snapshot = runtime.snapshot.value
        return ReaderUiState(
            phase = snapshot.phase,
            armed = snapshot.settings.armed,
            statusLabel = runtime.statusLabel(snapshot.phase),
            errorMessage = snapshot.errorMessage,
            devices = snapshot.devices,
            selectedDeviceKey = snapshot.settings.selectedDeviceKey,
            baudRateText = baudDraft.value,
            baudInvalid = BaudRates.parse(baudDraft.value) == null,
            autoOpen = snapshot.settings.autoOpen,
            allowedDomainsText = domainsDraft.value,
            domainsInvalid = UrlLineParser.invalidDomainLines(domainsDraft.value).isNotEmpty(),
            logText = formatLog(snapshot.log),
        )
    }

    private fun formatLog(entries: List<LogEntry>): String {
        if (entries.isEmpty()) return ""
        return entries.joinToString("\n") { entry ->
            val time = Instant.ofEpochMilli(entry.timestampMs)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(timeFormat)
            "$time  ${entry.message}"
        }
    }
}

class ReaderViewModelFactory(
    private val runtime: ReaderRuntime,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReaderViewModel(runtime) as T
        }
        error("ViewModel non supportato: ${modelClass.name}")
    }
}
