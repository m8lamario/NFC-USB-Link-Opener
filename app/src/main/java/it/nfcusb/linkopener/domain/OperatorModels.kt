package it.nfcusb.linkopener.domain

enum class OperatorStatus {
    Ready,
    ConnectReader,
    WaitingTag,
    Disconnected,
    ReadError,
}

enum class VisualOutcome {
    Success,
    Duplicate,
    Failure,
}

enum class ScanOutcome {
    Opened,
    Duplicate,
    Rejected,
    Error,
}

data class LastReading(
    val code: String,
    val url: String,
    val atMs: Long,
    val outcome: VisualOutcome,
    val detail: String,
    val successToken: Int,
)

data class SessionCounters(
    val valid: Int = 0,
    val duplicates: Int = 0,
    val errors: Int = 0,
)

data class ScanEvent(
    val id: Long,
    val timestampMs: Long,
    val url: String,
    val code: String,
    val outcome: ScanOutcome,
    val detail: String,
)
