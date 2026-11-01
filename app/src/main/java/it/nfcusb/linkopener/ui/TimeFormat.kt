package it.nfcusb.linkopener.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTimeFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

fun formatDateTime(epochMs: Long): String {
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(dateTimeFormat)
}

fun formatTime(epochMs: Long): String {
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(timeFormat)
}
