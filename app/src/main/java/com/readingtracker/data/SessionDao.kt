package com.readingtracker.data

import androidx.room.*

@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: ReadingSession): Long

    @Update
    suspend fun updateSession(session: ReadingSession)

    @Query("SELECT * FROM reading_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): ReadingSession?

    @Query("SELECT * FROM reading_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getOpenSession(): ReadingSession?

    @Query("""
        SELECT bookId FROM reading_sessions
        WHERE appPackage = :pkg
          AND bookId IN (SELECT id FROM books)
        ORDER BY startTime DESC LIMIT 1
    """)
    suspend fun getLastBookIdForApp(pkg: String): Long?

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startTime DESC")
    suspend fun getSessionsForBook(bookId: Long): List<ReadingSession>

    /**
     * Returns one row per calendar day: the date string (YYYY-MM-DD) and total minutes read.
     * Used for streak calculation and calendar heatmap.
     */
    @Query("""
        SELECT 
            DATE(startTime / 1000, 'unixepoch', 'localtime') AS day,
            SUM((COALESCE(endTime, :now) - startTime) / 60000) AS minutes
        FROM reading_sessions
        WHERE startTime >= :since
        GROUP BY day
        ORDER BY day DESC
    """)
    suspend fun getDailyMinutes(since: Long, now: Long): List<DayMinutes>

    /** Total minutes in a given calendar month, for stats screen. */
    @Query("""
        SELECT SUM((COALESCE(endTime, :now) - startTime) / 60000)
        FROM reading_sessions
        WHERE startTime >= :startOfMonth AND startTime < :endOfMonth
    """)
    suspend fun getMinutesInMonth(
        startOfMonth: Long,
        endOfMonth: Long,
        now: Long
    ): Long?
}

data class DayMinutes(val day: String, val minutes: Long)
