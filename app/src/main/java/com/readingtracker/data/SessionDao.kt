package com.readingtracker.data

import androidx.room.*

@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: ReadingSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<ReadingSession>)

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

    /** Книга последней сессии в любом приложении. */
    @Query("""
        SELECT bookId FROM reading_sessions
        WHERE bookId IN (SELECT id FROM books)
        ORDER BY startTime DESC LIMIT 1
    """)
    suspend fun getLastBookIdAnyApp(): Long?

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startTime ASC")
    suspend fun getSessionsForBook(bookId: Long): List<ReadingSession>

    @Query("SELECT * FROM reading_sessions ORDER BY startTime ASC")
    suspend fun getAllSessions(): List<ReadingSession>

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM reading_sessions")
    suspend fun deleteAll()
}
