package com.readingtracker.reminder

import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.data.BookActions
import com.readingtracker.ui.book.BookActivity
import kotlinx.coroutines.launch

/** Окно «Дочитана?» при открытии трекера. Закрыть без ответа = спросить через неделю. */
class StatusPrompt(private val activity: AppCompatActivity) {

    private var showing = false

    fun maybeShow() {
        if (showing) return
        activity.lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val book = AppDatabase.getInstance(activity).bookDao()
                .getBooksToAsk(now - BookActions.THREE_WEEKS_MS, now)
                .firstOrNull() ?: return@launch
            if (activity.isFinishing) return@launch
            show(book)
        }
    }

    private fun show(book: Book) {
        showing = true
        val reason = if (book.needsStatusCheck) "Вы начали новую книгу."
        else "Книга начата ${BookActions.daysOnBook(book)} дн. назад."
        val items = arrayOf("Дочитана", "Брошена", "Отложена", "Открыть карточку книги", "Спросить через неделю")

        var answered = false
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val title = TextView(activity).apply {
            text = "${Book.displayName(book)}\n$reason Дочитана?"
            textSize = 17f
            setPadding(pad, pad, pad, pad / 2)
        }
        AlertDialog.Builder(activity)
            .setCustomTitle(title)
            .setItems(items) { _, which ->
                answered = true
                activity.lifecycleScope.launch {
                    when (which) {
                        0 -> BookActions.setStatus(activity, book.id, Book.STATUS_FINISHED)
                        1 -> BookActions.setStatus(activity, book.id, Book.STATUS_ABANDONED)
                        2 -> BookActions.setStatus(activity, book.id, Book.STATUS_PAUSED)
                        3 -> {
                            BookActions.askLater(activity, book.id)
                            BookActivity.open(activity, book.id)
                        }
                        else -> BookActions.askLater(activity, book.id)
                    }
                    showing = false
                    if (which in 0..2) maybeShow()
                }
            }
            .setOnCancelListener {
                if (!answered) activity.lifecycleScope.launch { BookActions.askLater(activity, book.id) }
                showing = false
            }
            .setOnDismissListener { showing = false }
            .show()
    }
}
