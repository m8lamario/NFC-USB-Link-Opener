package it.nfcusb.linkopener.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanEventDao {
    @Query(
        """
        SELECT * FROM scan_events
        WHERE code LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%'
        ORDER BY timestampMs DESC
        LIMIT 1000
        """,
    )
    fun observe(query: String): Flow<List<ScanEventEntity>>

    @Query("SELECT * FROM scan_events ORDER BY timestampMs DESC")
    suspend fun all(): List<ScanEventEntity>

    @Insert
    suspend fun insert(event: ScanEventEntity)

    @Query("DELETE FROM scan_events")
    suspend fun clear()
}
