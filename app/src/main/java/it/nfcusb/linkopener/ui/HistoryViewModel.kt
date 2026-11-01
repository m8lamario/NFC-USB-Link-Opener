package it.nfcusb.linkopener.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.nfcusb.linkopener.data.HistoryRepository
import it.nfcusb.linkopener.domain.ScanEvent
import it.nfcusb.linkopener.session.ReaderRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val history: HistoryRepository,
    private val runtime: ReaderRuntime,
) : ViewModel() {
    val query = MutableStateFlow("")

    val events = query
        .flatMapLatest { history.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun reopen(event: ScanEvent): Boolean = runtime.reopen(event.url)

    fun clear() {
        viewModelScope.launch { history.clear() }
    }

    suspend fun exportCsv(): String = history.exportCsv()
}

class HistoryViewModelFactory(
    private val history: HistoryRepository,
    private val runtime: ReaderRuntime,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(history, runtime) as T
        }
        error("ViewModel non supportato: ${modelClass.name}")
    }
}
