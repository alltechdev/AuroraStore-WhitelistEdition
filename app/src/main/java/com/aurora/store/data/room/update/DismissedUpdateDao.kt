package com.aurora.store.data.room.update

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DismissedUpdateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dismissedUpdate: DismissedUpdate)

    @Query("SELECT * FROM dismissed_update")
    fun getAll(): Flow<List<DismissedUpdate>>

    @Query("SELECT EXISTS(SELECT 1 FROM dismissed_update WHERE packageName = :packageName AND versionCode = :versionCode)")
    suspend fun isDismissed(packageName: String, versionCode: Long): Boolean

    @Query("DELETE FROM dismissed_update WHERE packageName = :packageName")
    suspend fun deleteForPackage(packageName: String)

    @Query("DELETE FROM dismissed_update")
    suspend fun deleteAll()
}
