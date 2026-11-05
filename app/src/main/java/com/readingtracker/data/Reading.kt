package com.readingtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * Прочтение книги. Строки появляются только с перечитыванием: при первом «Перечитываю»
 * у книги с карточкой прежнее чтение становится прочтением № 1, новое — № 2 и т. д.
 * Новая карточка, у которой сразу «Перечитываю», получает одно прочтение с reread = true.
 * Время прочтения считается по сеансам между его началом и началом следующего.
 */
@Entity(tableName = "readings")
data class Reading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val number: Int,
    val startDate: Long,
    val endDate: Long? = null,
    val status: String,
    /** Это перечитывание (а не первое прочтение). */
    val reread: Boolean
)

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings ORDER BY bookId, number")
    suspend fun getAll(): List<Reading>

    @Query("SELECT * FROM readings WHERE bookId = :bookId ORDER BY number")
    suspend fun forBook(bookId: Long): List<Reading>

    @Insert
    suspend fun insert(r: Reading): Long

    @Insert
    suspend fun insertAll(list: List<Reading>)

    @Update
    suspend fun update(r: Reading)

    @Query("DELETE FROM readings WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM readings")
    suspend fun deleteAll()
}
