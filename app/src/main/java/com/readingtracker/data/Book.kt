package com.readingtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val appPackage: String,
    val appName: String,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val status: String = STATUS_READING,
    val rating: Int? = null,
    val totalMinutes: Long = 0
) {
    companion object {
        const val STATUS_READING = "reading"
        const val STATUS_FINISHED = "finished"
        const val STATUS_ABANDONED = "abandoned"
        const val STATUS_PAUSED = "paused"
    }
}
