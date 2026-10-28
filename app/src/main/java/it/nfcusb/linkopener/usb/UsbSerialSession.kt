package it.nfcusb.linkopener.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import it.nfcusb.linkopener.domain.SerialDeviceChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class UsbSerialSession(
    private val onLine: (String) -> Unit,
    private val onOverflow: () -> Unit,
    private val onError: (IOException) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val lineBuffer = SerialLineBuffer()
    @Volatile
    private var port: UsbSerialPort? = null
    private var connection: UsbDeviceConnection? = null
    private var readJob: Job? = null
    @Volatile
    private var generation = 0

    @Volatile
    var openDeviceName: String? = null
        private set

    @Volatile
    var baudRate: Int = 0
        private set

    val isOpen: Boolean
        get() = port != null

    fun listChoices(usbManager: UsbManager): List<SerialDeviceChoice> {
        val found = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).flatMap { driver ->
            driver.ports.mapIndexed { index, _ ->
                RawPort(
                    device = driver.device,
                    portIndex = index,
                    portCount = driver.ports.size,
                )
            }
        }
        val counts = found.groupingBy { portKey(it) }.eachCount()
        return found.map { raw ->
            val base = portKey(raw)
            val key = if (counts.getValue(base) > 1) "$base:${raw.device.deviceName}" else base
            SerialDeviceChoice(
                key = key,
                deviceName = raw.device.deviceName,
                vendorId = raw.device.vendorId,
                productId = raw.device.productId,
                portIndex = raw.portIndex,
                label = labelFor(raw),
            )
        }
    }

    fun open(usbManager: UsbManager, device: UsbDevice, portIndex: Int, baudRate: Int) {
        val serialPort: UsbSerialPort
        val openedConnection: UsbDeviceConnection
        val generationToken: Int
        synchronized(lock) {
            val previous = readJob
            readJob = null
            closeUnlocked()
            previous?.cancel()
            generationToken = ++generation
            openedConnection = usbManager.openDevice(device)
                ?: throw IOException("Permesso USB assente")
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            if (driver == null) {
                openedConnection.close()
                throw IOException("Nessun driver seriale compatibile")
            }
            val selected = driver.ports.getOrNull(portIndex)
            if (selected == null) {
                openedConnection.close()
                throw IOException("Porta seriale non trovata")
            }
            try {
                selected.open(openedConnection)
                selected.setParameters(
                    baudRate,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE,
                )
                // DTR e RTS non vengono forzati: su molti ESP32 alzarli resetta la scheda.
            } catch (error: Exception) {
                try {
                    selected.close()
                } catch (_: Exception) {
                }
                try {
                    openedConnection.close()
                } catch (_: Exception) {
                }
                throw if (error is IOException) error else IOException(error.message, error)
            }
            lineBuffer.clear()
            port = selected
            connection = openedConnection
            openDeviceName = device.deviceName
            this.baudRate = baudRate
            serialPort = selected
        }
        val job = scope.launch {
            val buffer = ByteArray(READ_BUFFER_BYTES)
            try {
                while (isActive && generationToken == generation) {
                    val count = serialPort.read(buffer, READ_TIMEOUT_MS)
                    if (count <= 0) continue
                    val result = synchronized(lock) {
                        if (generationToken != generation) {
                            null
                        } else {
                            lineBuffer.append(buffer, 0, count)
                        }
                    } ?: continue
                    if (result.overflow) {
                        withContext(Dispatchers.Main) { onOverflow() }
                    }
                    result.lines.forEach { line ->
                        withContext(Dispatchers.Main) { onLine(line) }
                    }
                }
            } catch (error: IOException) {
                if (generationToken == generation) {
                    withContext(Dispatchers.Main) { onError(error) }
                }
            }
        }
        synchronized(lock) {
            if (generationToken == generation) {
                readJob = job
            } else {
                job.cancel()
            }
        }
    }

    fun close() {
        val job = synchronized(lock) {
            val current = readJob
            readJob = null
            closeUnlocked()
            current
        }
        job?.cancel()
    }

    private fun closeUnlocked() {
        generation++
        val currentPort = port
        val currentConnection = connection
        port = null
        connection = null
        openDeviceName = null
        baudRate = 0
        lineBuffer.clear()
        try {
            currentPort?.close()
        } catch (_: Exception) {
        }
        try {
            currentConnection?.close()
        } catch (_: Exception) {
        }
    }

    private fun portKey(raw: RawPort): String {
        return "${raw.device.vendorId}:${raw.device.productId}:${raw.portIndex}"
    }

    private fun labelFor(raw: RawPort): String {
        val name = raw.device.productName?.takeIf { it.isNotBlank() } ?: "Dispositivo USB"
        val ids = "%04X:%04X".format(raw.device.vendorId, raw.device.productId)
        val portLabel = if (raw.portCount > 1) " · porta ${raw.portIndex + 1}" else ""
        return "$name ($ids)$portLabel"
    }

    private data class RawPort(
        val device: UsbDevice,
        val portIndex: Int,
        val portCount: Int,
    )

    private companion object {
        const val READ_TIMEOUT_MS = 200
        const val READ_BUFFER_BYTES = 4096
    }
}
