package it.nfcusb.linkopener.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import it.nfcusb.linkopener.domain.BaudRates
import it.nfcusb.linkopener.domain.ReaderSettings
import it.nfcusb.linkopener.parse.UrlDebouncer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.operatorSettingsStore by preferencesDataStore(name = "operator_settings")

class SettingsRepository(private val context: Context) {
    private val mutex = Mutex()

    suspend fun load(): ReaderSettings = mutex.withLock {
        val current = context.operatorSettingsStore.data.first()
        if (current[Keys.READY] == true) return@withLock decode(current)
        val migrated = migrateLegacy()
        write(migrated)
        migrated
    }

    suspend fun save(settings: ReaderSettings) = mutex.withLock {
        write(settings)
    }

    private suspend fun write(settings: ReaderSettings) {
        context.operatorSettingsStore.edit { prefs ->
            prefs[Keys.READY] = true
            prefs[Keys.BAUD] = settings.baudRate
            prefs[Keys.AUTO] = settings.autoOpen
            prefs[Keys.START] = settings.startOnAttach
            prefs[Keys.WHITELIST] = settings.whitelistEnabled
            prefs[Keys.DOMAINS] = settings.allowedDomainsText
            prefs[Keys.OPEN_UNLISTED] = settings.openUnlisted
            prefs[Keys.DEBOUNCE] = settings.debounceMs
            prefs[Keys.VIBRATION] = settings.vibrationEnabled
            prefs[Keys.SOUND] = settings.soundEnabled
            prefs[Keys.BROWSER] = settings.internalBrowser
            prefs[Keys.ARMED] = settings.armed
            prefs[Keys.PATTERN] = settings.codePattern
            if (settings.selectedDeviceKey == null) {
                prefs.remove(Keys.DEVICE)
            } else {
                prefs[Keys.DEVICE] = settings.selectedDeviceKey
            }
        }
    }

    private fun decode(prefs: Preferences): ReaderSettings {
        val baud = prefs[Keys.BAUD] ?: BaudRates.DEFAULT
        val debounce = prefs[Keys.DEBOUNCE] ?: UrlDebouncer.WINDOW_MS
        return ReaderSettings(
            baudRate = baud.takeIf { it in BaudRates.MIN..BaudRates.MAX } ?: BaudRates.DEFAULT,
            autoOpen = prefs[Keys.AUTO] ?: true,
            startOnAttach = prefs[Keys.START] ?: true,
            whitelistEnabled = prefs[Keys.WHITELIST] ?: false,
            allowedDomainsText = prefs[Keys.DOMAINS].orEmpty(),
            openUnlisted = prefs[Keys.OPEN_UNLISTED] ?: false,
            debounceMs = debounce.coerceIn(ReaderSettings.MIN_DEBOUNCE_MS, ReaderSettings.MAX_DEBOUNCE_MS),
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            soundEnabled = prefs[Keys.SOUND] ?: true,
            internalBrowser = prefs[Keys.BROWSER] ?: false,
            selectedDeviceKey = prefs[Keys.DEVICE],
            armed = prefs[Keys.ARMED] ?: false,
            codePattern = prefs[Keys.PATTERN] ?: ReaderSettings().codePattern,
        )
    }

    private fun migrateLegacy(): ReaderSettings {
        val old = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (old.all.isEmpty()) return ReaderSettings()
        val domains = old.getString("domains", "").orEmpty()
        val baud = old.getInt("baud", BaudRates.DEFAULT)
        return ReaderSettings(
            baudRate = baud.takeIf { it in BaudRates.MIN..BaudRates.MAX } ?: BaudRates.DEFAULT,
            autoOpen = old.getBoolean("auto_open", true),
            startOnAttach = old.getBoolean("start_on_attach", true),
            whitelistEnabled = domains.isNotBlank(),
            allowedDomainsText = domains,
            selectedDeviceKey = old.getString("device", null),
            armed = old.getBoolean("armed", false),
        )
    }

    private object Keys {
        val READY = booleanPreferencesKey("ready")
        val BAUD = intPreferencesKey("baud")
        val AUTO = booleanPreferencesKey("auto_open")
        val START = booleanPreferencesKey("start_on_attach")
        val WHITELIST = booleanPreferencesKey("whitelist")
        val DOMAINS = stringPreferencesKey("domains")
        val OPEN_UNLISTED = booleanPreferencesKey("open_unlisted")
        val DEBOUNCE = longPreferencesKey("debounce")
        val VIBRATION = booleanPreferencesKey("vibration")
        val SOUND = booleanPreferencesKey("sound")
        val BROWSER = booleanPreferencesKey("internal_browser")
        val DEVICE = stringPreferencesKey("device")
        val ARMED = booleanPreferencesKey("armed")
        val PATTERN = stringPreferencesKey("code_pattern")
    }

    private companion object {
        const val LEGACY_PREFS = "reader"
    }
}
