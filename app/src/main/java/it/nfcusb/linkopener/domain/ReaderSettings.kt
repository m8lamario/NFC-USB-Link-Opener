package it.nfcusb.linkopener.domain

import it.nfcusb.linkopener.parse.TagCodeExtractor
import it.nfcusb.linkopener.parse.UrlDebouncer

object BaudRates {
    const val DEFAULT = 115_200
    const val MIN = 300
    const val MAX = 3_000_000

    fun parse(text: String): Int? = text.toIntOrNull()?.takeIf { it in MIN..MAX }
}

data class ReaderSettings(
    val baudRate: Int = BaudRates.DEFAULT,
    val autoOpen: Boolean = true,
    val startOnAttach: Boolean = true,
    val whitelistEnabled: Boolean = false,
    val allowedDomainsText: String = "",
    val openUnlisted: Boolean = false,
    val debounceMs: Long = UrlDebouncer.WINDOW_MS,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val internalBrowser: Boolean = false,
    val selectedDeviceKey: String? = null,
    val armed: Boolean = false,
    val codePattern: String = TagCodeExtractor.DEFAULT_PATTERN,
) {
    fun effectiveDebounce(): Long = debounceMs.coerceIn(MIN_DEBOUNCE_MS, MAX_DEBOUNCE_MS)

    companion object {
        const val MIN_DEBOUNCE_MS = 200L
        const val MAX_DEBOUNCE_MS = 10_000L
    }
}
