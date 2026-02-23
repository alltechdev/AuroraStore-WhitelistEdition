package com.aurora.store.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aurora.store.data.room.download.Download
import com.aurora.store.data.room.download.DownloadConverter
import com.aurora.store.data.room.download.DownloadDao
import com.aurora.store.data.room.update.DismissedUpdate
import com.aurora.store.data.room.update.DismissedUpdateDao
import com.aurora.store.data.room.update.Update
import com.aurora.store.data.room.update.UpdateDao

@Database(
    entities = [Download::class, Update::class, DismissedUpdate::class],
    version = 6,
    exportSchema = true
)
@TypeConverters(DownloadConverter::class)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun updateDao(): UpdateDao
    abstract fun dismissedUpdateDao(): DismissedUpdateDao
}
