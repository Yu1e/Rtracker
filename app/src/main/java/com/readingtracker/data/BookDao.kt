package com.readingtracker.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY startDate DESC")
    fun getAllBooks(): LiveData<List<Book>>

    @Query("SELECT * FROM books ORDER BY startDate DESC")
    suspend fun getAllBooksSync(): List<Book>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): Book?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: Long): LiveData<Book?>

    @Query("SELECT * FROM books WHERE title = :title AND author = :author LIMIT 1")
    suspend fun findBook(title: String, author: String): Book?

    @Query("SELECT DISTINCT author FROM books WHERE author != '' ORDER BY author")
    suspend fun getAllAuthors(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<Book>)

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    /** Страницы из читалки: отдельным запросом, чтобы не затереть время, которое пишет служба. */
    @Query("UPDATE books SET pagesRead = :read, pagesTotal = :total WHERE id = :id")
    suspend fun setPages(id: Long, read: Int, total: Int)

    @Query("UPDATE books SET author = :author WHERE id = :id AND author = ''")
    suspend fun fillAuthor(id: Long, author: String)

    @Query("DELETE FROM books")
    suspend fun deleteAll()

    /**
     * Книги, о статусе которых пора спросить в трекере:
     * без статуса и (начата новая после неё, или прошло 3 недели), и срок «позже» истёк.
     */
    @Query("""
        SELECT * FROM books
        WHERE status = 'reading'
          AND (needsStatusCheck = 1 OR startDate <= :threeWeeksAgo)
          AND (statusAskAt IS NULL OR statusAskAt <= :now)
        ORDER BY startDate ASC
    """)
    suspend fun getBooksToAsk(threeWeeksAgo: Long, now: Long): List<Book>

    /** Для уведомлений: только правило трёх недель. */
    @Query("""
        SELECT * FROM books
        WHERE status = 'reading'
          AND startDate <= :threeWeeksAgo
          AND (statusAskAt IS NULL OR statusAskAt <= :now)
          AND (lastNotifiedAt IS NULL OR lastNotifiedAt <= :weekAgo)
    """)
    suspend fun getBooksToNotify(threeWeeksAgo: Long, now: Long, weekAgo: Long): List<Book>
}
