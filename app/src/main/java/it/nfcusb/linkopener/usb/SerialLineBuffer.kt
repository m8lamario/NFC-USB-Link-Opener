package it.nfcusb.linkopener.usb

import java.io.ByteArrayOutputStream

data class AppendResult(
    val lines: List<String>,
    val overflow: Boolean,
)

/**
 * Accumula byte finché arriva LF. Accetta anche CRLF.
 * Se una riga supera [maxBytes], la scarta fino al newline successivo.
 */
class SerialLineBuffer(
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    private val pending = ByteArrayOutputStream()
    private var discarding = false

    fun append(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): AppendResult {
        val lines = ArrayList<String>()
        var overflow = false
        val end = offset + length
        var index = offset
        while (index < end) {
            val value = bytes[index]
            if (discarding) {
                if (value == LF) discarding = false
                index++
                continue
            }
            if (value == LF) {
                lines += decodePending()
                pending.reset()
            } else if (pending.size() >= maxBytes) {
                pending.reset()
                discarding = true
                overflow = true
            } else {
                pending.write(value.toInt())
            }
            index++
        }
        return AppendResult(lines, overflow)
    }

    fun clear() {
        pending.reset()
        discarding = false
    }

    private fun decodePending(): String {
        return pending.toByteArray().toString(Charsets.UTF_8).trimEnd('\r')
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 16_384
        private const val LF = '\n'.code.toByte()
    }
}
