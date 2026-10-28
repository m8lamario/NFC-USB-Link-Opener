package it.nfcusb.linkopener.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import it.nfcusb.linkopener.NfcUsbLinkApp
import it.nfcusb.linkopener.session.ReaderRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReaderForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private lateinit var runtime: ReaderRuntime

    override fun onCreate() {
        super.onCreate()
        runtime = (application as NfcUsbLinkApp).runtime
        runtime.notifier.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runtime.disarm()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this,
                ReaderNotifier.ONGOING_ID,
                runtime.ongoingNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (_: Exception) {
            runtime.onForegroundStartFailed()
            stopSelf()
            return START_NOT_STICKY
        }
        runtime.onServiceStarted()
        observeJob?.cancel()
        observeJob = scope.launch {
            runtime.phase.collect {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(ReaderNotifier.ONGOING_ID, runtime.ongoingNotification())
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        if (::runtime.isInitialized) {
            runtime.onServiceDestroyed()
        }
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "it.nfcusb.linkopener.action.STOP_READER"
    }
}
