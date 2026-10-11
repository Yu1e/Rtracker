package com.readingtracker.data

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.readingtracker.widget.WidgetUpdater

/** Общие действия над книгой: из трекера, уведомления, карточки. */
object BookActions {

    const val DAY_MS = 24 * 60 * 60 * 1000L
    const val WEEK_MS = 7 * DAY_MS
    const val THREE_WEEKS_MS = 21 * DAY_MS

    fun notificationId(bookId: Long) = 2000 + (bookId % 100_000).toInt()

    /** Правило дат для статуса. */
    fun applyStatus(book: Book, status: String): Book {
        val now = System.currentTimeMillis()
        val endDate = when (status) {
            Book.STATUS_FINISHED, Book.STATUS_ABANDONED -> book.endDate ?: now
            Book.STATUS_READING -> null
            else -> book.endDate
        }
        return book.copy(
            status = status,
            endDate = endDate,
            needsStatusCheck = if (status == Book.STATUS_READING) book.needsStatusCheck else false,
            statusAskAt = if (status == Book.STATUS_READING) book.statusAskAt else null
        )
    }

    suspend fun setStatus(context: Context, bookId: Long, status: String) {
        val db = AppDatabase.getInstance(context)
        val book = db.bookDao().getBookById(bookId) ?: return
        db.bookDao().updateBook(applyStatus(book, status))
        NotificationManagerCompat.from(context).cancel(notificationId(bookId))
        WidgetUpdater.update(context)
    }

    /** «Позже»: спросить снова через неделю. */
    suspend fun askLater(context: Context, bookId: Long) {
        val db = AppDatabase.getInstance(context)
        val book = db.bookDao().getBookById(bookId) ?: return
        db.bookDao().updateBook(book.copy(statusAskAt = System.currentTimeMillis() + WEEK_MS))
        NotificationManagerCompat.from(context).cancel(notificationId(bookId))
    }

    suspend fun delete(context: Context, bookId: Long) {
        val db = AppDatabase.getInstance(context)
        val book = db.bookDao().getBookById(bookId) ?: return
        db.sessionDao().deleteForBook(bookId)
        db.bookDao().deleteBook(book)
        NotificationManagerCompat.from(context).cancel(notificationId(bookId))
        WidgetUpdater.update(context)
    }

    /**
     * Новая книга из читалки или файла. Если такая уже есть — возвращает её.
     * У предыдущей книги без статуса появляется вопрос «дочитана?» в трекере.
     */
    suspend fun addNewBook(context: Context, title: String, author: String, pkg: String, appName: String): Long {
        val db = AppDatabase.getInstance(context)
        db.bookDao().findBook(title, author)?.let { return it.id }
        val previousId = db.sessionDao().getLastBookIdAnyApp()
        val newId = db.bookDao().insertBook(
            Book(title = title, author = author, appPackage = pkg, appName = appName)
        )
        if (previousId != null && previousId != newId) {
            val prev = db.bookDao().getBookById(previousId)
            if (prev != null && prev.status == Book.STATUS_READING) {
                db.bookDao().updateBook(prev.copy(needsStatusCheck = true, statusAskAt = null))
            }
        }
        WidgetUpdater.update(context)
        return newId
    }

    /** Сколько календарных дней книга в работе (включая первый и последний). */
    fun daysOnBook(book: Book, now: Long = System.currentTimeMillis()): Int {
        val end = book.endDate ?: now
        return ((end - book.startDate) / DAY_MS).toInt().coerceAtLeast(0) + 1
    }

    fun isStatusDue(book: Book, now: Long = System.currentTimeMillis()): Boolean =
        book.status == Book.STATUS_READING &&
            (book.needsStatusCheck || book.startDate <= now - THREE_WEEKS_MS) &&
            (book.statusAskAt == null || book.statusAskAt <= now)
}
