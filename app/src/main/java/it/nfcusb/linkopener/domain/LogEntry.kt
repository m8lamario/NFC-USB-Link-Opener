package it.nfcusb.linkopener.domain

data class LogEntry(
    val timestampMs: Long,
    val message: String,
)
