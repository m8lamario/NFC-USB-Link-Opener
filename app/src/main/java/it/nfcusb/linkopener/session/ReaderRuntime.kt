package it.nfcusb.linkopener.session

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import it.nfcusb.linkopener.R
import it.nfcusb.linkopener.data.HistoryRepository
import it.nfcusb.linkopener.data.SettingsRepository
import it.nfcusb.linkopener.domain.LastReading
import it.nfcusb.linkopener.domain.LogEntry
import it.nfcusb.linkopener.domain.OperatorStatus
import it.nfcusb.linkopener.domain.ReaderPhase
import it.nfcusb.linkopener.domain.ReaderSettings
import it.nfcusb.linkopener.domain.ScanOutcome
import it.nfcusb.linkopener.domain.SerialDeviceChoice
import it.nfcusb.linkopener.domain.SessionCounters
import it.nfcusb.linkopener.domain.VisualOutcome
import it.nfcusb.linkopener.parse.CheckResult
import it.nfcusb.linkopener.parse.DomainPolicy
import it.nfcusb.linkopener.parse.ParsedLine
import it.nfcusb.linkopener.parse.ReadingCheck
import it.nfcusb.linkopener.parse.SampleLines
import it.nfcusb.linkopener.parse.TagCodeExtractor
import it.nfcusb.linkopener.parse.UrlDebouncer
import it.nfcusb.linkopener.parse.UrlLineParser
import it.nfcusb.linkopener.service.ReaderForegroundService
import it.nfcusb.linkopener.service.ReaderNotifier
import it.nfcusb.linkopener.usb.UsbSerialSession
import it.nfcusb.linkopener.usb.usbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

data class ReaderSnapshot(
    val phase: ReaderPhase,
    val operatorStatus: OperatorStatus,
    val devices: List<SerialDeviceChoice>,
    val settings: ReaderSettings,
    val log: List<LogEntry>,
    val technicalMessage: String?,
    val lastReading: LastReading?,
    val counters: SessionCounters,
    val serviceRunning: Boolean,
)

class ReaderRuntime(
    private val app: Application,
    private val settingsRepository: SettingsRepository,
    private val history: HistoryRepository,
) {
    private val usbManager = app.getSystemService(UsbManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectMutex = Mutex()
    private val debouncer = UrlDebouncer()
    private val session = UsbSerialSession(
        onLine = ::onSerialLine,
        onOverflow = ::onLineOverflow,
        onError = ::onSessionError,
    )
    private val feedback = FeedbackPlayer(app)

    val notifier = ReaderNotifier(app)
    private val linkOpener = LinkOpener(app, notifier)

    private val phaseState = MutableStateFlow(ReaderPhase.NotConnected)
    private val devicesState = MutableStateFlow<List<SerialDeviceChoice>>(emptyList())
    private val settingsState = MutableStateFlow(ReaderSettings())
    private val logState = MutableStateFlow<List<LogEntry>>(emptyList())
    private val technicalMessage = MutableStateFlow<String?>(null)
    private val lastReading = MutableStateFlow<LastReading?>(null)
    private val countersState = MutableStateFlow(SessionCounters())
    private val serviceRunningState = MutableStateFlow(false)

    val phase: StateFlow<ReaderPhase> = phaseState
    val settings: StateFlow<ReaderSettings> = settingsState

    val snapshot: StateFlow<ReaderSnapshot> = combine(
        combine(
            phaseState,
            devicesState,
            settingsState,
            logState,
            technicalMessage,
        ) { phase, devices, settings, log, message ->
            CoreSlice(phase, devices, settings, log, message)
        },
        combine(lastReading, countersState, serviceRunningState) { reading, counters, running ->
            TailSlice(reading, counters, running)
        },
    ) { core, tail ->
        ReaderSnapshot(
            phase = core.phase,
            operatorStatus = operatorStatus(core.phase, tail.reading),
            devices = core.devices,
            settings = core.settings,
            log = core.log,
            technicalMessage = core.message,
            lastReading = tail.reading,
            counters = tail.counters,
            serviceRunning = tail.running,
        )
    }.stateIn(scope, SharingStarted.Eagerly, emptySnapshot())

    @Volatile
    private var acceptPort = false

    private var everActive = false
    private var pendingServiceStart = false
    private var suppressAutoPermission = false
    private var attachGeneration = 0
    private var attachRetryJob: Job? = null
    private var successToken = 0

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION || !settingsState.value.armed) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (!granted) {
                suppressAutoPermission = true
                phaseState.value = if (everActive) ReaderPhase.Unplugged else ReaderPhase.NotConnected
                note(app.getString(R.string.log_permission_denied))
                return
            }
            connectSelected(logIfMissing = false)
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val name = intent.usbDevice()?.deviceName
                    val current = session.openDeviceName ?: selectedChoice()?.deviceName
                    refreshDevices()
                    if (name != null && name == current && settingsState.value.armed) {
                        session.close()
                        everActive = true
                        phaseState.value = ReaderPhase.Unplugged
                        technicalMessage.value = null
                        addLog(app.getString(R.string.log_detached))
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> onUsbAttach()
            }
        }
    }

    fun install() {
        val loaded = runBlocking(Dispatchers.IO) { settingsRepository.load() }
        settingsState.value = loaded
        debouncer.windowMs = loaded.effectiveDebounce()
        notifier.ensureChannels()
        refreshDevices()
        ContextCompat.registerReceiver(
            app,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            app,
            usbReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun operatorLabel(status: OperatorStatus = snapshot.value.operatorStatus): String {
        return when (status) {
            OperatorStatus.Ready -> app.getString(R.string.status_ready)
            OperatorStatus.ConnectReader -> app.getString(R.string.status_connect)
            OperatorStatus.WaitingTag -> app.getString(R.string.status_waiting_tag)
            OperatorStatus.Disconnected -> app.getString(R.string.status_disconnected)
            OperatorStatus.ReadError -> app.getString(R.string.status_read_error)
        }
    }

    fun technicalLabel(phase: ReaderPhase = phaseState.value): String {
        return when (phase) {
            ReaderPhase.NotConnected -> app.getString(R.string.tech_not_connected)
            ReaderPhase.WaitingPermission -> app.getString(R.string.tech_waiting)
            ReaderPhase.Active -> app.getString(R.string.tech_connected)
            ReaderPhase.Unplugged -> app.getString(R.string.tech_unplugged)
            ReaderPhase.ReadError -> app.getString(R.string.tech_read_error)
        }
    }

    fun ongoingNotification() = notifier.ongoing(operatorLabel())

    fun onActivityStarted() {
        refreshDevices()
        if (!settingsState.value.armed) return
        if (phaseState.value == ReaderPhase.WaitingPermission) return
        if (phaseState.value == ReaderPhase.Active && serviceRunningState.value && session.isOpen) return
        if (suppressAutoPermission && !pendingServiceStart) return
        if (devicesState.value.isEmpty()) {
            if (everActive || phaseState.value == ReaderPhase.Unplugged) {
                phaseState.value = ReaderPhase.Unplugged
            }
            return
        }
        connectSelected(logIfMissing = false)
    }

    fun onUsbAttach() {
        val generation = ++attachGeneration
        attachRetryJob?.cancel()
        suppressAutoPermission = false
        if (activateAttachedReader()) return
        if (!settingsState.value.startOnAttach && !settingsState.value.armed) return
        attachRetryJob = scope.launch {
            repeat(ATTACH_RETRIES) {
                delay(ATTACH_RETRY_MS)
                if (generation != attachGeneration) return@launch
                if (activateAttachedReader()) return@launch
            }
        }
    }

    fun arm() {
        attachGeneration++
        attachRetryJob?.cancel()
        resetSession()
        phaseState.value = ReaderPhase.NotConnected
        updateSettings { it.copy(armed = true) }
        suppressAutoPermission = false
        technicalMessage.value = null
        connectSelected(logIfMissing = true)
    }

    fun disarm() {
        val wasArmed = settingsState.value.armed
        attachGeneration++
        attachRetryJob?.cancel()
        acceptPort = false
        pendingServiceStart = false
        suppressAutoPermission = false
        everActive = false
        updateSettings { it.copy(armed = false) }
        session.close()
        phaseState.value = ReaderPhase.NotConnected
        technicalMessage.value = null
        if (serviceRunningState.value) {
            serviceRunningState.value = false
            app.stopService(Intent(app, ReaderForegroundService::class.java))
        }
        if (wasArmed) addLog(app.getString(R.string.log_stopped))
    }

    fun reconnect() {
        if (!settingsState.value.armed) {
            arm()
            return
        }
        suppressAutoPermission = false
        technicalMessage.value = null
        session.close()
        connectSelected(logIfMissing = true)
    }

    fun selectDevice(key: String) {
        updateSettings { it.copy(selectedDeviceKey = key) }
        if (!settingsState.value.armed) return
        suppressAutoPermission = false
        if (serviceRunningState.value) {
            session.close()
            openSelectedPort()
        } else {
            connectSelected(logIfMissing = true)
        }
    }

    fun updateSettings(block: (ReaderSettings) -> ReaderSettings) {
        val updated = block(settingsState.value)
        settingsState.value = updated
        debouncer.windowMs = updated.effectiveDebounce()
        scope.launch(Dispatchers.IO) { settingsRepository.save(updated) }
    }

    fun onNotificationsDenied() {
        addLog(app.getString(R.string.log_notifications_denied))
    }

    fun simulateValid() {
        handleLine(SampleLines.VALID)
    }

    fun simulateDuplicate() {
        handleLine(SampleLines.VALID)
        handleLine(SampleLines.DUPLICATE)
    }

    fun simulateInvalid() {
        handleLine(SampleLines.INVALID)
    }

    fun simulateUnauthorized() {
        record(
            code = "P04a",
            url = SampleLines.UNAUTHORIZED.removePrefix(UrlLineParser.PREFIX),
            outcome = VisualOutcome.Failure,
            detail = app.getString(R.string.outcome_domain),
            historyOutcome = ScanOutcome.Rejected,
            countErrors = true,
            countDuplicates = false,
            countValid = false,
            failureFeedback = true,
        )
    }

    fun simulateDisconnect() {
        session.close()
        everActive = true
        phaseState.value = ReaderPhase.Unplugged
        technicalMessage.value = null
        addLog(app.getString(R.string.log_simulated_disconnect))
    }

    fun reopen(url: String): Boolean {
        val settings = settingsState.value
        return when (val check = ReadingCheck.check(url, currentPolicy(), settings.codePattern)) {
            is CheckResult.Accepted -> {
                linkOpener.open(check.url, settings.internalBrowser)
                true
            }
            is CheckResult.Rejected -> false
        }
    }

    fun onServiceStarted() {
        serviceRunningState.value = true
        acceptPort = true
        pendingServiceStart = false
        openSelectedPort()
    }

    fun onServiceDestroyed() {
        acceptPort = false
        serviceRunningState.value = false
        session.close()
        if (settingsState.value.armed && phaseState.value == ReaderPhase.Active) {
            phaseState.value = ReaderPhase.Unplugged
            addLog(app.getString(R.string.log_service_stopped))
        }
    }

    fun onForegroundStartFailed() {
        acceptPort = false
        serviceRunningState.value = false
        pendingServiceStart = false
        phaseState.value = if (everActive) ReaderPhase.Unplugged else ReaderPhase.NotConnected
        note(app.getString(R.string.log_fgs_failed))
    }

    private fun activateAttachedReader(): Boolean {
        refreshDevices()
        if (devicesState.value.isEmpty()) return false
        if (!settingsState.value.armed) {
            if (!settingsState.value.startOnAttach) return false
            resetSession()
            updateSettings { it.copy(armed = true) }
            technicalMessage.value = null
            addLog(app.getString(R.string.log_auto_started))
        }
        connectSelected(logIfMissing = false)
        return true
    }

    private fun connectSelected(logIfMissing: Boolean) {
        if (!settingsState.value.armed) return
        refreshDevices()
        val choices = devicesState.value
        if (choices.isEmpty()) {
            phaseState.value = if (everActive) ReaderPhase.Unplugged else ReaderPhase.NotConnected
            if (logIfMissing) note(app.getString(R.string.log_no_device))
            return
        }
        if (selectedChoice() == null && choices.size == 1) {
            updateSettings { it.copy(selectedDeviceKey = choices.first().key) }
        }
        val choice = selectedChoice()
        if (choice == null) {
            phaseState.value = if (everActive) ReaderPhase.Unplugged else ReaderPhase.NotConnected
            technicalMessage.value = app.getString(R.string.error_select_device)
            return
        }
        val device = usbDevice(choice)
        if (device == null) {
            phaseState.value = ReaderPhase.Unplugged
            return
        }
        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return
        }
        if (serviceRunningState.value) {
            openSelectedPort()
        } else {
            startReaderService()
        }
    }

    private fun startReaderService() {
        pendingServiceStart = true
        try {
            ContextCompat.startForegroundService(
                app,
                Intent(app, ReaderForegroundService::class.java),
            )
        } catch (_: Exception) {
            note(app.getString(R.string.log_service_deferred))
        }
    }

    private fun openSelectedPort() {
        scope.launch {
            connectMutex.withLock {
                if (!settingsState.value.armed || !serviceRunningState.value) return@withLock
                val choice = selectedChoice()
                if (choice == null) {
                    technicalMessage.value = app.getString(R.string.error_select_device)
                    return@withLock
                }
                val device = usbDevice(choice)
                if (device == null) {
                    everActive = true
                    phaseState.value = ReaderPhase.Unplugged
                    return@withLock
                }
                if (!usbManager.hasPermission(device)) {
                    requestPermission(device)
                    return@withLock
                }
                val baud = settingsState.value.baudRate
                if (session.isOpen && session.openDeviceName == device.deviceName && session.baudRate == baud) {
                    phaseState.value = ReaderPhase.Active
                    everActive = true
                    technicalMessage.value = null
                    return@withLock
                }
                val opened = withContext(Dispatchers.IO) {
                    runCatching { session.open(usbManager, device, choice.portIndex, baud) }
                }
                if (!acceptPort || !settingsState.value.armed) {
                    session.close()
                    return@withLock
                }
                opened.onSuccess {
                    phaseState.value = ReaderPhase.Active
                    everActive = true
                    technicalMessage.value = null
                    addLog(app.getString(R.string.log_port_open, baud))
                }.onFailure {
                    session.close()
                    everActive = true
                    phaseState.value = ReaderPhase.ReadError
                    note(app.getString(R.string.log_open_failed))
                }
            }
        }
    }

    private fun requestPermission(device: UsbDevice) {
        phaseState.value = ReaderPhase.WaitingPermission
        technicalMessage.value = null
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or pendingMutableFlag()
        val pending = PendingIntent.getBroadcast(
            app,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(app.packageName),
            flags,
        )
        usbManager.requestPermission(device, pending)
    }

    private fun onSerialLine(line: String) {
        handleLine(line)
    }

    private fun onLineOverflow() {
        addLog(app.getString(R.string.log_line_overflow))
    }

    private fun onSessionError(@Suppress("UNUSED_PARAMETER") error: IOException) {
        if (!settingsState.value.armed) return
        everActive = true
        session.close()
        phaseState.value = ReaderPhase.ReadError
        countersState.value = countersState.value.copy(errors = countersState.value.errors + 1)
        note(app.getString(R.string.log_read_error))
        val current = settingsState.value
        feedback.failure(current.vibrationEnabled, current.soundEnabled)
    }

    private fun handleLine(line: String) {
        if (line.isNotBlank()) addLog(line.take(MAX_LOG_TEXT))
        debouncer.windowMs = settingsState.value.effectiveDebounce()
        when (val parsed = UrlLineParser.parse(line, currentPolicy())) {
            ParsedLine.Ignored -> Unit
            is ParsedLine.Malformed -> record(
                code = "—",
                url = parsed.rawUrl.ifBlank { "—" },
                outcome = VisualOutcome.Failure,
                detail = app.getString(R.string.outcome_invalid),
                historyOutcome = ScanOutcome.Rejected,
                countErrors = true,
                countDuplicates = false,
                countValid = false,
                failureFeedback = true,
            )
            is ParsedLine.RejectedDomain -> record(
                code = TagCodeExtractor.extract(parsed.url) ?: "—",
                url = parsed.url,
                outcome = VisualOutcome.Failure,
                detail = app.getString(R.string.outcome_domain),
                historyOutcome = ScanOutcome.Rejected,
                countErrors = true,
                countDuplicates = false,
                countValid = false,
                failureFeedback = true,
            )
            is ParsedLine.Valid -> onValidUrl(parsed.url)
        }
    }

    private fun onValidUrl(url: String) {
        val settings = settingsState.value
        when (val check = ReadingCheck.check(url, currentPolicy(), settings.codePattern)) {
            is CheckResult.Rejected -> record(
                code = check.code ?: "—",
                url = check.url,
                outcome = VisualOutcome.Failure,
                detail = when (check.kind) {
                    CheckResult.Kind.Domain -> app.getString(R.string.outcome_domain)
                    CheckResult.Kind.Code -> app.getString(R.string.outcome_code)
                    CheckResult.Kind.Malformed -> app.getString(R.string.outcome_invalid)
                },
                historyOutcome = ScanOutcome.Rejected,
                countErrors = true,
                countDuplicates = false,
                countValid = false,
                failureFeedback = true,
            )
            is CheckResult.Accepted -> {
                if (!settings.autoOpen) {
                    record(
                        code = check.code,
                        url = check.url,
                        outcome = VisualOutcome.Failure,
                        detail = app.getString(R.string.outcome_auto_off),
                        historyOutcome = ScanOutcome.Rejected,
                        countErrors = true,
                        countDuplicates = false,
                        countValid = false,
                        failureFeedback = true,
                    )
                    return
                }
                if (!debouncer.shouldOpen(check.url)) {
                    record(
                        code = check.code,
                        url = check.url,
                        outcome = VisualOutcome.Duplicate,
                        detail = app.getString(R.string.outcome_duplicate),
                        historyOutcome = ScanOutcome.Duplicate,
                        countErrors = false,
                        countDuplicates = true,
                        countValid = false,
                        failureFeedback = false,
                    )
                    return
                }
                val opened = linkOpener.open(check.url, settings.internalBrowser)
                val detail = if (opened == OpenOutcome.Opened) {
                    app.getString(R.string.outcome_opened)
                } else {
                    app.getString(R.string.outcome_notified)
                }
                record(
                    code = check.code,
                    url = check.url,
                    outcome = VisualOutcome.Success,
                    detail = detail,
                    historyOutcome = ScanOutcome.Opened,
                    countErrors = false,
                    countDuplicates = false,
                    countValid = true,
                    failureFeedback = false,
                )
            }
        }
    }

    private fun record(
        code: String,
        url: String,
        outcome: VisualOutcome,
        detail: String,
        historyOutcome: ScanOutcome,
        countErrors: Boolean,
        countDuplicates: Boolean,
        countValid: Boolean,
        failureFeedback: Boolean,
    ) {
        val at = System.currentTimeMillis()
        if (outcome == VisualOutcome.Success) successToken += 1
        lastReading.value = LastReading(code, url, at, outcome, detail, successToken)
        val counters = countersState.value
        countersState.value = counters.copy(
            valid = counters.valid + if (countValid) 1 else 0,
            duplicates = counters.duplicates + if (countDuplicates) 1 else 0,
            errors = counters.errors + if (countErrors) 1 else 0,
        )
        if (phaseState.value == ReaderPhase.ReadError && outcome == VisualOutcome.Success && session.isOpen) {
            phaseState.value = ReaderPhase.Active
        }
        addLog(detail)
        val current = settingsState.value
        if (outcome == VisualOutcome.Success) {
            feedback.success(current.vibrationEnabled, current.soundEnabled)
        } else if (failureFeedback) {
            feedback.failure(current.vibrationEnabled, current.soundEnabled)
        }
        scope.launch(Dispatchers.IO) {
            history.insert(at, url, code, historyOutcome, detail)
        }
    }

    private fun currentPolicy(): DomainPolicy {
        val settings = settingsState.value
        return DomainPolicy(
            enabled = settings.whitelistEnabled,
            domains = UrlLineParser.normalizeDomains(settings.allowedDomainsText),
            openUnlisted = settings.openUnlisted,
        )
    }

    private fun refreshDevices() {
        val choices = session.listChoices(usbManager)
        devicesState.value = choices
        val key = settingsState.value.selectedDeviceKey
        if (key != null && choices.none { it.key == key }) {
            val replacement = if (choices.size == 1) choices.first().key else null
            updateSettings { it.copy(selectedDeviceKey = replacement) }
        }
    }

    private fun selectedChoice(): SerialDeviceChoice? {
        val key = settingsState.value.selectedDeviceKey ?: return null
        return devicesState.value.find { it.key == key }
    }

    private fun usbDevice(choice: SerialDeviceChoice): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { it.deviceName == choice.deviceName }
    }

    private fun resetSession() {
        debouncer.clear()
        countersState.value = SessionCounters()
        lastReading.value = null
        successToken = 0
    }

    private fun note(message: String) {
        technicalMessage.value = message
        addLog(message)
    }

    private fun addLog(message: String) {
        logState.value = (listOf(LogEntry(System.currentTimeMillis(), message)) + logState.value).take(MAX_LOG_LINES)
    }

    private fun operatorStatus(phase: ReaderPhase, reading: LastReading?): OperatorStatus {
        return when (phase) {
            ReaderPhase.ReadError -> OperatorStatus.ReadError
            ReaderPhase.Unplugged -> OperatorStatus.Disconnected
            ReaderPhase.Active -> if (reading == null) OperatorStatus.Ready else OperatorStatus.WaitingTag
            ReaderPhase.NotConnected,
            ReaderPhase.WaitingPermission,
            -> OperatorStatus.ConnectReader
        }
    }

    private fun emptySnapshot(): ReaderSnapshot {
        return ReaderSnapshot(
            phase = ReaderPhase.NotConnected,
            operatorStatus = OperatorStatus.ConnectReader,
            devices = emptyList(),
            settings = ReaderSettings(),
            log = emptyList(),
            technicalMessage = null,
            lastReading = null,
            counters = SessionCounters(),
            serviceRunning = false,
        )
    }

    private fun pendingMutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    }

    private data class CoreSlice(
        val phase: ReaderPhase,
        val devices: List<SerialDeviceChoice>,
        val settings: ReaderSettings,
        val log: List<LogEntry>,
        val message: String?,
    )

    private data class TailSlice(
        val reading: LastReading?,
        val counters: SessionCounters,
        val running: Boolean,
    )

    companion object {
        const val ACTION_USB_PERMISSION = "it.nfcusb.linkopener.USB_PERMISSION"
        private const val ATTACH_RETRIES = 5
        private const val ATTACH_RETRY_MS = 300L
        private const val MAX_LOG_LINES = 50
        private const val MAX_LOG_TEXT = 180
    }
}
