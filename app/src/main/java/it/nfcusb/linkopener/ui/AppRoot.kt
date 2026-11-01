package it.nfcusb.linkopener.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import it.nfcusb.linkopener.NfcUsbLinkApp

private enum class AppPage {
    Operator,
    History,
    Settings,
}

@Composable
fun AppRoot(app: NfcUsbLinkApp) {
    var pageName by rememberSaveable { mutableStateOf(AppPage.Operator.name) }
    val page = AppPage.valueOf(pageName)
    val operatorViewModel: OperatorViewModel = viewModel(factory = OperatorViewModelFactory(app.runtime))
    val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModelFactory(app.history, app.runtime))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(app.runtime, app.history))

    BackHandler(enabled = page != AppPage.Operator) {
        pageName = AppPage.Operator.name
    }

    when (page) {
        AppPage.Operator -> OperatorScreen(
            viewModel = operatorViewModel,
            onOpenHistory = { pageName = AppPage.History.name },
            onOpenSettings = { pageName = AppPage.Settings.name },
        )
        AppPage.History -> HistoryScreen(
            viewModel = historyViewModel,
            onBack = { pageName = AppPage.Operator.name },
        )
        AppPage.Settings -> SettingsScreen(
            viewModel = settingsViewModel,
            onBack = { pageName = AppPage.Operator.name },
        )
    }
}
