package it.nfcusb.linkopener.data

import it.nfcusb.linkopener.domain.ScanEvent
import it.nfcusb.linkopener.domain.ScanOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HistoryRepository(private val dao: ScanEventDao) {
    fun observe(query: String): Flow<List<ScanEvent>> {
        return dao.observe(query.trim()).map { rows -> rows.map { it.toDomain() } }
    }

    suspend fun insert(
        timestampMs: Long,
        url: String,
        code: String,
        outcome: ScanOutcome,
        detail: String,
    ) {
        dao.insert(
            ScanEventEntity(
                timestampMs = timestampMs,
                url = url,
                code = code,
                outcome = outcome.name,
                detail = detail,
            ),
        )
    }

    suspend fun clear() {
        dao.clear()
    }

    suspend fun exportCsv(): String {
        val header = "data,ora,codice,url,esito,dettaglio"
        val rows = dao.all().joinToString("\n") { event ->
            val zoned = Instant.ofEpochMilli(event.timestampMs).atZone(ZoneId.systemDefault())
            listOf(
                zoned.format(DATE),
                zoned.format(TIME),
                event.code,
                event.url,
                outcomeLabel(event.outcome),
                event.detail,
            ).joinToString(",") { cell -> csv(cell) }
        }
        return if (rows.isEmpty()) header else "$header\n$rows"
    }

    private fun ScanEventEntity.toDomain(): ScanEvent {
        return ScanEvent(
            id = id,
            timestampMs = timestampMs,
            url = url,
            code = code,
            outcome = runCatching { ScanOutcome.valueOf(outcome) }.getOrDefault(ScanOutcome.Error),
            detail = detail,
        )
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun outcomeLabel(name: String): String {
        return when (runCatching { ScanOutcome.valueOf(name) }.getOrNull()) {
            ScanOutcome.Opened -> "aperto"
            ScanOutcome.Duplicate -> "duplicato ignorato"
            ScanOutcome.Rejected -> "rifiutato"
            ScanOutcome.Error -> "errore"
            null -> name
        }
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
