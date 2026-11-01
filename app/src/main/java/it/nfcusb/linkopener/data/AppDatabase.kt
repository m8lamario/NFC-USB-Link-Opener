package it.nfcusb.linkopener.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ScanEventEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanEventDao(): ScanEventDao
}
