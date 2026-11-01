package it.nfcusb.linkopener.parse

/**
 * Accetta lo stesso URL una sola volta dentro [windowMs] dall'ultima apertura accettata.
 * Un duplicato ignorato non sposta la finestra.
 */
class UrlDebouncer(
    windowMs: Long = WINDOW_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var windowMs: Long = windowMs
    private val lastAccepted = HashMap<String, Long>()

    fun clear() {
        lastAccepted.clear()
    }

    fun shouldOpen(url: String): Boolean {
        val time = now()
        val previous = lastAccepted[url]
        if (previous != null && time - previous < windowMs) {
            return false
        }
        lastAccepted[url] = time
        return true
    }

    companion object {
        const val WINDOW_MS = 1_500L
    }
}
