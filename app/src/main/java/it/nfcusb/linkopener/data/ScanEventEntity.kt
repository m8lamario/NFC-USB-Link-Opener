package it.nfcusb.linkopener.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_events")
data class ScanEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val url: String,
    val code: String,
    val outcome: String,
    val detail: String,
)
