package com.readingtracker.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.readingtracker.Constants
import com.readingtracker.R
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.data.BookActions
import com.readingtracker.ui.book.BookActivity

/** Уведомления «Дочитала?» по правилу трёх недель. */
object ReminderNotifier {

    suspend fun check(context: Context) {
        val db = AppDatabase.getInstance(context)
        val now = System.currentTimeMillis()
        val books = db.bookDao().getBooksToNotify(
            threeWeeksAgo = now - BookActions.THREE_WEEKS_MS,
            now = now,
            weekAgo = now - BookActions.WEEK_MS
        )
        for (book in books) {
            if (notify(context, book)) {
                db.bookDao().updateBook(book.copy(lastNotifiedAt = now))
            }
        }
    }

    private fun notify(context: Context, book: Book): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        val id = BookActions.notificationId(book.id)

        fun action(status: String, req: Int): PendingIntent = PendingIntent.getBroadcast(
            context, id * 10 + req,
            Intent(context, StatusActionReceiver::class.java)
                .putExtra(StatusActionReceiver.EXTRA_BOOK_ID, book.id)
                .putExtra(StatusActionReceiver.EXTRA_STATUS, status),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, BookActivity::class.java)
                .putExtra(BookActivity.EXTRA_BOOK_ID, book.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val days = BookActions.daysOnBook(book)
        val n = NotificationCompat.Builder(context, Constants.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_book_notify)
            .setContentTitle("${Book.displayName(book)} — дочитана?")
            .setContentText("Книга начата $days дн. назад, статуса пока нет")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setDeleteIntent(action(StatusActionReceiver.LATER, 0))
            .addAction(0, "Дочитала", action(Book.STATUS_FINISHED, 1))
            .addAction(0, "Бросила", action(Book.STATUS_ABANDONED, 2))
            .addAction(0, "Отложила", action(Book.STATUS_PAUSED, 3))
            .build()
        return try {
            nm.notify(id, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
