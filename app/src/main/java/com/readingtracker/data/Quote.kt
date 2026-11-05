package com.readingtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * Цитата из книги. Ловится нажатием «Копировать» в читалке (пузырёк поверх неё),
 * правится на экране цитат, выгружается в Obsidian.
 */
@Entity(tableName = "quotes")
data class Quote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val text: String,
    /** Теги через «|», без «#». */
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** «Сохранить, поправлю потом»: на экране цитат помечена. */
    val draft: Boolean = false
) {
    companion object {
        fun splitTags(s: String): List<String> = s.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        fun joinTags(list: List<String>): String = list.map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }.distinct().joinToString("|")
        /** Теги из ввода: через пробел, запятую или точку с запятой; «#» необязателен. */
        fun parseTags(s: String): List<String> =
            s.split(Regex("[,;\\s]+")).map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }.distinct()
    }
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    suspend fun getAll(): List<Quote>

    @Query("SELECT * FROM quotes WHERE bookId = :bookId ORDER BY createdAt")
    suspend fun forBook(bookId: Long): List<Quote>

    @Query("SELECT COUNT(*) FROM quotes WHERE bookId = :bookId")
    suspend fun countForBook(bookId: Long): Int

    @Query("SELECT bookId FROM quotes")
    suspend fun bookIds(): List<Long>

    @Query("SELECT tags FROM quotes WHERE tags != '' ORDER BY createdAt DESC LIMIT 200")
    suspend fun recentTagRows(): List<String>

    @Insert
    suspend fun insert(q: Quote): Long

    @Insert
    suspend fun insertAll(list: List<Quote>)

    @Update
    suspend fun update(q: Quote)

    @Delete
    suspend fun delete(q: Quote)

    @Query("DELETE FROM quotes WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM quotes")
    suspend fun deleteAll()
}
