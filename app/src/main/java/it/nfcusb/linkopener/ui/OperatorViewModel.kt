package it.nfcusb.linkopener.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.nfcusb.linkopener.domain.LastReading
import it.nfcusb.linkopener.domain.OperatorStatus
import it.nfcusb.linkopener.domain.SessionCounters
import it.nfcusb.linkopener.session.ReaderRuntime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class OperatorUiState(
    val status: OperatorStatus,
    val statusLabel: String,
    val armed: Boolean,
    val lastReading: LastReading?,
    val counters: SessionCounters,
)

class OperatorViewModel(
    private val runtime: ReaderRuntime,
) : ViewModel() {
    val state: StateFlow<OperatorUiState> = runtime.snapshot
        .map { snapshot ->
            OperatorUiState(
                status = snapshot.operatorStatus,
                statusLabel = runtime.operatorLabel(snapshot.operatorStatus),
                armed = snapshot.settings.armed,
                lastReading = snapshot.lastReading,
                counters = snapshot.counters,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())

    fun start() = runtime.arm()

    fun onNotificationsDenied() = runtime.onNotificationsDenied()

    private fun current(): OperatorUiState {
        val snapshot = runtime.snapshot.value
        return OperatorUiState(
            status = snapshot.operatorStatus,
            statusLabel = runtime.operatorLabel(snapshot.operatorStatus),
            armed = snapshot.settings.armed,
            lastReading = snapshot.lastReading,
            counters = snapshot.counters,
        )
    }
}

class OperatorViewModelFactory(
    private val runtime: ReaderRuntime,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OperatorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OperatorViewModel(runtime) as T
        }
        error("ViewModel non supportato: ${modelClass.name}")
    }
}
