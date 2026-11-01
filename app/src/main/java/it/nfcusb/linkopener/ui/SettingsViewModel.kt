package it.nfcusb.linkopener.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.nfcusb.linkopener.data.HistoryRepository
import it.nfcusb.linkopener.domain.BaudRates
import it.nfcusb.linkopener.domain.LogEntry
import it.nfcusb.linkopener.domain.ReaderPhase
import it.nfcusb.linkopener.domain.ReaderSettings
import it.nfcusb.linkopener.domain.SerialDeviceChoice
import it.nfcusb.linkopener.parse.TagCodeExtractor
import it.nfcusb.linkopener.parse.UrlLineParser
import it.nfcusb.linkopener.session.ReaderRuntime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val phase: ReaderPhase,
    val technicalStatus: String,
    val technicalMessage: String?,
    val devices: List<SerialDeviceChoice>,
    val settings: ReaderSettings,
    val logText: String,
    val domainsInvalid: Boolean,
    val patternInvalid: Boolean,
)

class SettingsViewModel(
    private val runtime: ReaderRuntime,
    private val history: HistoryRepository,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = runtime.snapshot
        .map { snapshot ->
            SettingsUiState(
                phase = snapshot.phase,
                technicalStatus = runtime.technicalLabel(snapshot.phase),
                technicalMessage = snapshot.technicalMessage,
                devices = snapshot.devices,
                settings = snapshot.settings,
                logText = formatLog(snapshot.log),
                domainsInvalid = UrlLineParser.invalidDomainLines(snapshot.settings.allowedDomainsText).isNotEmpty(),
                patternInvalid = !TagCodeExtractor.isPatternValid(snapshot.settings.codePattern),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())

    fun selectDevice(key: String) = runtime.selectDevice(key)

    fun reconnect() = runtime.reconnect()

    fun stopReader() = runtime.disarm()

    fun onBaudChange(text: String) {
        BaudRates.parse(text.filter { it.isDigit() }.take(7))?.let { baud ->
            runtime.updateSettings { it.copy(baudRate = baud) }
        }
    }

    fun onDebounceChange(text: String) {
        val value = text.filter { it.isDigit() }.take(5).toLongOrNull() ?: return
        if (value in ReaderSettings.MIN_DEBOUNCE_MS..ReaderSettings.MAX_DEBOUNCE_MS) {
            runtime.updateSettings { it.copy(debounceMs = value) }
        }
    }

    fun onAutoOpenChange(enabled: Boolean) = runtime.updateSettings { it.copy(autoOpen = enabled) }

    fun onStartOnAttachChange(enabled: Boolean) = runtime.updateSettings { it.copy(startOnAttach = enabled) }

    fun onWhitelistChange(enabled: Boolean) = runtime.updateSettings { it.copy(whitelistEnabled = enabled) }

    fun onDomainsChange(text: String) = runtime.updateSettings { it.copy(allowedDomainsText = text) }

    fun onOpenUnlistedChange(enabled: Boolean) = runtime.updateSettings { it.copy(openUnlisted = enabled) }

    fun onVibrationChange(enabled: Boolean) = runtime.updateSettings { it.copy(vibrationEnabled = enabled) }

    fun onSoundChange(enabled: Boolean) = runtime.updateSettings { it.copy(soundEnabled = enabled) }

    fun onInternalBrowserChange(enabled: Boolean) = runtime.updateSettings { it.copy(internalBrowser = enabled) }

    fun onPatternChange(text: String) = runtime.updateSettings { it.copy(codePattern = text.take(200)) }

    fun simulateValid() = runtime.simulateValid()

    fun simulateDuplicate() = runtime.simulateDuplicate()

    fun simulateInvalid() = runtime.simulateInvalid()

    fun simulateUnauthorized() = runtime.simulateUnauthorized()

    fun simulateDisconnect() = runtime.simulateDisconnect()

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    suspend fun exportCsv(): String = history.exportCsv()

    private fun current(): SettingsUiState {
        val snapshot = runtime.snapshot.value
        return SettingsUiState(
            phase = snapshot.phase,
            technicalStatus = runtime.technicalLabel(snapshot.phase),
            technicalMessage = snapshot.technicalMessage,
            devices = snapshot.devices,
            settings = snapshot.settings,
            logText = formatLog(snapshot.log),
            domainsInvalid = UrlLineParser.invalidDomainLines(snapshot.settings.allowedDomainsText).isNotEmpty(),
            patternInvalid = !TagCodeExtractor.isPatternValid(snapshot.settings.codePattern),
        )
    }

    private fun formatLog(entries: List<LogEntry>): String {
        if (entries.isEmpty()) return ""
        return entries.joinToString("\n") { entry ->
            "${formatTime(entry.timestampMs)}  ${entry.message}"
        }
    }
}

class SettingsViewModelFactory(
    private val runtime: ReaderRuntime,
    private val history: HistoryRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(runtime, history) as T
        }
        error("ViewModel non supportato: ${modelClass.name}")
    }
}
