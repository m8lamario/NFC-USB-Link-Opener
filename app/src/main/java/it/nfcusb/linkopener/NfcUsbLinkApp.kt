package it.nfcusb.linkopener

import android.app.Application
import it.nfcusb.linkopener.session.ReaderRuntime

class NfcUsbLinkApp : Application() {
    lateinit var runtime: ReaderRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = ReaderRuntime(this)
        runtime.install()
    }
}
