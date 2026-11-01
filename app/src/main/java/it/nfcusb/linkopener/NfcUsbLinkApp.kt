package it.nfcusb.linkopener

import android.app.Application
import androidx.room.Room
import it.nfcusb.linkopener.data.AppDatabase
import it.nfcusb.linkopener.data.HistoryRepository
import it.nfcusb.linkopener.data.SettingsRepository
import it.nfcusb.linkopener.session.ReaderRuntime

class NfcUsbLinkApp : Application() {
    lateinit var runtime: ReaderRuntime
        private set

    lateinit var history: HistoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "operator.db").build()
        history = HistoryRepository(database.scanEventDao())
        runtime = ReaderRuntime(this, SettingsRepository(this), history)
        runtime.install()
    }
}
