package com.aurora.store.data.room.update

import androidx.room.Entity

/**
 * Tracks updates that have been dismissed or attempted by the user.
 * This prevents updates from showing again even if installation fails.
 */
@Entity(tableName = "dismissed_update", primaryKeys = ["packageName", "versionCode"])
data class DismissedUpdate(
    val packageName: String,
    val versionCode: Long,
    val dismissedAt: Long = System.currentTimeMillis()
)
