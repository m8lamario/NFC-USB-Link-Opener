package it.nfcusb.linkopener.session

import android.content.Context
import it.nfcusb.linkopener.domain.BaudRates
import it.nfcusb.linkopener.domain.ReaderSettings

class ReaderPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(): ReaderSettings {
        val savedBaud = prefs.getInt(KEY_BAUD, BaudRates.DEFAULT)
        val baud = if (savedBaud in BaudRates.MIN..BaudRates.MAX) savedBaud else BaudRates.DEFAULT
        return ReaderSettings(
            baudRate = baud,
            autoOpen = prefs.getBoolean(KEY_AUTO, true),
            allowedDomainsText = prefs.getString(KEY_DOMAINS, "").orEmpty(),
            selectedDeviceKey = prefs.getString(KEY_DEVICE, null),
            armed = prefs.getBoolean(KEY_ARMED, false),
        )
    }

    fun save(settings: ReaderSettings) {
        prefs.edit()
            .putInt(KEY_BAUD, settings.baudRate)
            .putBoolean(KEY_AUTO, settings.autoOpen)
            .putString(KEY_DOMAINS, settings.allowedDomainsText)
            .putString(KEY_DEVICE, settings.selectedDeviceKey)
            .putBoolean(KEY_ARMED, settings.armed)
            .apply()
    }

    private companion object {
        const val NAME = "reader"
        const val KEY_BAUD = "baud"
        const val KEY_AUTO = "auto_open"
        const val KEY_DOMAINS = "domains"
        const val KEY_DEVICE = "device"
        const val KEY_ARMED = "armed"
    }
}
