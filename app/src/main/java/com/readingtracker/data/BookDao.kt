package com.readingtracker.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY startDate DESC")
    fun getAllBooks(): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE status = 'reading' ORDER BY startDate DESC")
    fun getReadingBooks(): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): Book?

    @Query("SELECT * FROM books WHERE title = :title AND author = :author LIMIT 1")
    suspend fun findBook(title: String, author: String): Book?

    @Query("SELECT * FROM books WHERE status = 'reading' ORDER BY startDate DESC LIMIT 1")
    suspend fun getLastReadingBook(): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    // ── Statistics ─────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM books WHERE status = 'finished'")
    fun getFinishedBooksCount(): LiveData<Int>

    @Query("""
        SELECT COUNT(*) FROM books 
        WHERE status = 'finished' 
          AND endDate >= :startOfMonth AND endDate < :endOfMonth
    """)
    suspend fun getFinishedBooksInMonth(startOfMonth: Long, endOfMonth: Long): Int

    @Query("SELECT SUM(totalMinutes) FROM books WHERE status = 'finished'")
    fun getTotalReadingMinutes(): LiveData<Long?>

    @Query("SELECT * FROM books WHERE status = 'reading' AND startDate < :thresholdDate")
    suspend fun getPotentiallyAbandonedBooks(thresholdDate: Long): List<Book>

    @Query("""
        SELECT author, COUNT(*) as bookCount 
        FROM books 
        GROUP BY author 
        ORDER BY bookCount DESC 
        LIMIT 10
    """)
    fun getTopAuthors(): LiveData<List<AuthorStat>>

    @Query("""
        SELECT appName, COUNT(*) as bookCount, SUM(totalMinutes) as totalMins
        FROM books
        GROUP BY appPackage
        ORDER BY totalMins DESC
    """)
    fun getStatsByApp(): LiveData<List<AppStat>>

    @Query("SELECT * FROM books WHERE status = 'finished' ORDER BY totalMinutes DESC")
    suspend fun getFinishedBooksSortedByTime(): List<Book>
}

data class AuthorStat(val author: String, val bookCount: Int)
data class AppStat(val appName: String, val bookCount: Int, val totalMins: Long)
