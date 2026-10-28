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
import it.nfcusb.linkopener.domain.LogEntry
import it.nfcusb.linkopener.domain.ReaderPhase
import it.nfcusb.linkopener.domain.ReaderSettings
import it.nfcusb.linkopener.domain.SerialDeviceChoice
import it.nfcusb.linkopener.parse.ParsedLine
import it.nfcusb.linkopener.parse.SampleLines
import it.nfcusb.linkopener.parse.UrlDebouncer
import it.nfcusb.linkopener.parse.UrlLineParser
import it.nfcusb.linkopener.service.ReaderForegroundService
import it.nfcusb.linkopener.service.ReaderNotifier
import it.nfcusb.linkopener.usb.UsbSerialSession
import it.nfcusb.linkopener.usb.usbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

data class ReaderSnapshot(
    val phase: ReaderPhase,
    val devices: List<SerialDeviceChoice>,
    val settings: ReaderSettings,
    val log: List<LogEntry>,
    val errorMessage: String?,
)

class ReaderRuntime(private val app: Application) {
    private val prefs = ReaderPreferences(app)
    private val usbManager = app.getSystemService(UsbManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectMutex = Mutex()
    private val debouncer = UrlDebouncer()
    private val session = UsbSerialSession(
        onLine = ::onSerialLine,
        onOverflow = ::onLineOverflow,
        onError = ::onSessionError,
    )

    val notifier = ReaderNotifier(app)
    private val linkOpener = LinkOpener(app, notifier)

    private val phaseState = MutableStateFlow(ReaderPhase.NotConnected)
    private val devicesState = MutableStateFlow<List<SerialDeviceChoice>>(emptyList())
    private val settingsState = MutableStateFlow(prefs.load())
    private val logState = MutableStateFlow<List<LogEntry>>(emptyList())
    private val errorState = MutableStateFlow<String?>(null)

    val phase: StateFlow<ReaderPhase> = phaseState.asStateFlow()
    val devices: StateFlow<List<SerialDeviceChoice>> = devicesState.asStateFlow()
    val settings: StateFlow<ReaderSettings> = settingsState.asStateFlow()
    val log: StateFlow<List<LogEntry>> = logState.asStateFlow()
    val errorMessage: StateFlow<String?> = errorState.asStateFlow()

    val snapshot: StateFlow<ReaderSnapshot> = combine(
        phaseState,
        devicesState,
        settingsState,
        logState,
        errorState,
    ) { phase, devices, settings, log, error ->
        ReaderSnapshot(phase, devices, settings, log, error)
    }.stateIn(
        scope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        ReaderSnapshot(
            phaseState.value,
            devicesState.value,
            settingsState.value,
            logState.value,
            errorState.value,
        ),
    )

    @Volatile
    private var serviceRunning = false

    @Volatile
    private var acceptPort = false

    private var everActive = false
    private var pendingServiceStart = false
    private var suppressAutoPermission = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION || !settingsState.value.armed) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (!granted) {
                suppressAutoPermission = true
                phaseState.value = if (everActive) ReaderPhase.Unplugged else ReaderPhase.NotConnected
                errorState.value = app.getString(R.string.log_permission_denied)
                addLog(app.getString(R.string.log_permission_denied))
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
                        errorState.value = null
                        addLog(app.getString(R.string.log_detached))
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> onUsbAttach()
            }
        }
    }

    fun install() {
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

    fun statusLabel(phase: ReaderPhase = phaseState.value): String {
        return when (phase) {
            ReaderPhase.NotConnected -> app.getString(R.string.status_not_connected)
            ReaderPhase.WaitingPermission -> app.getString(R.string.status_waiting)
            ReaderPhase.Active -> app.getString(R.string.status_active)
            ReaderPhase.Unplugged -> app.getString(R.string.status_unplugged)
        }
    }

    fun ongoingNotification() = notifier.ongoing(statusLabel())

    fun onActivityStarted() {
        refreshDevices()
        if (!settingsState.value.armed) return
        if (phaseState.value == ReaderPhase.WaitingPermission) return
        if (phaseState.value == ReaderPhase.Active && serviceRunning && session.isOpen) return
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
        refreshDevices()
        suppressAutoPermission = false
        if (settingsState.value.armed) {
            connectSelected(logIfMissing = false)
        }
    }

    fun arm() {
        updateSettings { it.copy(armed = true) }
        suppressAutoPermission = false
        errorState.value = null
        connectSelected(logIfMissing = true)
    }

    fun disarm() {
        val wasArmed = settingsState.value.armed
        acceptPort = false
        pendingServiceStart = false
        suppressAutoPermission = false
        everActive = false
        updateSettings { it.copy(armed = false) }
        session.close()
        phaseState.value = ReaderPhase.NotConnected
        errorState.value = null
        if (serviceRunning) {
            serviceRunning = false
            app.stopService(Intent(app, ReaderForegroundService::class.java))
        }
        if (wasArmed) addLog(app.getString(R.string.log_stopped))
    }

    fun retry() {
        suppressAutoPermission = false
        errorState.value = null
        connectSelected(logIfMissing = true)
    }

    fun selectDevice(key: String) {
        updateSettings { it.copy(selectedDeviceKey = key) }
        if (!settingsState.value.armed) return
        suppressAutoPermission = false
        if (serviceRunning) {
            session.close()
            openSelectedPort()
        } else {
            connectSelected(logIfMissing = true)
        }
    }

    fun updateBaud(baudRate: Int) {
        updateSettings { it.copy(baudRate = baudRate) }
    }

    fun updateAutoOpen(enabled: Boolean) {
        updateSettings { it.copy(autoOpen = enabled) }
    }

    fun updateDomains(text: String) {
        updateSettings { it.copy(allowedDomainsText = text) }
    }

    fun onNotificationsDenied() {
        addLog(app.getString(R.string.log_notifications_denied))
    }

    fun simulate() {
        SampleLines.sequence.forEach(::handleLine)
    }

    fun onServiceStarted() {
        serviceRunning = true
        acceptPort = true
        pendingServiceStart = false
        openSelectedPort()
    }

    fun onServiceDestroyed() {
        acceptPort = false
        serviceRunning = false
        session.close()
        if (settingsState.value.armed && phaseState.value == ReaderPhase.Active) {
            phaseState.value = ReaderPhase.Unplugged
            addLog(app.getString(R.string.log_service_stopped))
        }
    }

    fun onForegroundStartFailed() {
        acceptPort = false
        serviceRunning = false
        pendingServiceStart = false
        phaseState.value = if (everActive) ReaderPhase.Unplugged else ReaderPhase.NotConnected
        errorState.value = app.getString(R.string.log_fgs_failed)
        addLog(app.getString(R.string.log_fgs_failed))
    }

    private fun connectSelected(logIfMissing: Boolean) {
        if (!settingsState.value.armed) return
        refreshDevices()
        val choices = devicesState.value
        if (choices.isEmpty()) {
            phaseState.value = if (everActive) ReaderPhase.Unplugged else ReaderPhase.NotConnected
            if (logIfMissing) {
                errorState.value = app.getString(R.string.log_no_device)
                addLog(app.getString(R.string.log_no_device))
            }
            return
        }
        if (selectedChoice() == null && choices.size == 1) {
            updateSettings { it.copy(selectedDeviceKey = choices.first().key) }
        }
        val choice = selectedChoice()
        if (choice == null) {
            phaseState.value = if (everActive) ReaderPhase.Unplugged else ReaderPhase.NotConnected
            errorState.value = app.getString(R.string.error_select_device)
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
        if (serviceRunning) {
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
            errorState.value = app.getString(R.string.log_service_deferred)
            addLog(app.getString(R.string.log_service_deferred))
        }
    }

    private fun openSelectedPort() {
        scope.launch {
            connectMutex.withLock {
                if (!settingsState.value.armed || !serviceRunning) return@withLock
                val choice = selectedChoice()
                if (choice == null) {
                    errorState.value = app.getString(R.string.error_select_device)
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
                    errorState.value = null
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
                    errorState.value = null
                    addLog(app.getString(R.string.log_port_open, baud))
                }.onFailure {
                    session.close()
                    everActive = true
                    phaseState.value = ReaderPhase.Unplugged
                    errorState.value = app.getString(R.string.log_open_failed)
                    addLog(app.getString(R.string.log_open_failed))
                }
            }
        }
    }

    private fun requestPermission(device: UsbDevice) {
        phaseState.value = ReaderPhase.WaitingPermission
        errorState.value = null
        // Il sistema scrive EXTRA_DEVICE e EXTRA_PERMISSION_GRANTED: il PendingIntent deve essere mutabile.
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
        phaseState.value = ReaderPhase.Unplugged
        errorState.value = app.getString(R.string.log_read_error)
        addLog(app.getString(R.string.log_read_error))
    }

    private fun handleLine(line: String) {
        val domains = UrlLineParser.normalizeDomains(settingsState.value.allowedDomainsText)
        when (val parsed = UrlLineParser.parse(line, domains)) {
            ParsedLine.Ignored -> {
                if (line.isNotBlank()) {
                    addLog(app.getString(R.string.log_ignored, line.take(MAX_LOG_TEXT)))
                }
            }
            is ParsedLine.Malformed -> {
                val shown = parsed.rawUrl.ifBlank { "—" }.take(MAX_LOG_TEXT)
                addLog(app.getString(R.string.log_invalid_url, shown))
            }
            is ParsedLine.RejectedDomain -> addLog(app.getString(R.string.log_domain, parsed.host))
            is ParsedLine.Valid -> {
                if (!settingsState.value.autoOpen) {
                    addLog(app.getString(R.string.log_auto_off, parsed.url))
                    return
                }
                if (!debouncer.shouldOpen(parsed.url)) {
                    addLog(app.getString(R.string.log_duplicate, parsed.url))
                    return
                }
                val outcome = linkOpener.open(parsed.url)
                val message = if (outcome == OpenOutcome.Opened) {
                    app.getString(R.string.log_opened, parsed.url)
                } else {
                    app.getString(R.string.log_notified, parsed.url)
                }
                addLog(message)
            }
        }
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

    private fun updateSettings(block: (ReaderSettings) -> ReaderSettings) {
        val updated = block(settingsState.value)
        settingsState.value = updated
        prefs.save(updated)
    }

    private fun addLog(message: String) {
        val entry = LogEntry(System.currentTimeMillis(), message)
        logState.value = (listOf(entry) + logState.value).take(MAX_LOG_LINES)
    }

    private fun pendingMutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    }

    companion object {
        const val ACTION_USB_PERMISSION = "it.nfcusb.linkopener.USB_PERMISSION"
        private const val MAX_LOG_LINES = 50
        private const val MAX_LOG_TEXT = 180
    }
}
