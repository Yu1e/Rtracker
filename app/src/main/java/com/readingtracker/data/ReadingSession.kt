package com.readingtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One reading session = screen on + reading app in foreground, until screen off or app leaves.
 */
@Entity(tableName = "reading_sessions")
data class ReadingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val appPackage: String
) {
    val durationMinutes: Long
        get() = endTime?.let { (it - startTime) / 60_000 } ?: 0
}
