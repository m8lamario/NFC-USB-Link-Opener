package it.nfcusb.linkopener.domain

data class ReaderSettings(
    val baudRate: Int = BaudRates.DEFAULT,
    val autoOpen: Boolean = true,
    val allowedDomainsText: String = "",
    val selectedDeviceKey: String? = null,
    val armed: Boolean = false,
)

object BaudRates {
    const val DEFAULT = 115_200
    const val MIN = 300
    const val MAX = 3_000_000

    fun parse(text: String): Int? = text.toIntOrNull()?.takeIf { it in MIN..MAX }
}
