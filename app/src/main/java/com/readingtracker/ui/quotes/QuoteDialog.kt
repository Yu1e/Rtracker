package com.readingtracker.ui.quotes

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.readingtracker.R
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.data.Quote
import com.readingtracker.ui.RtColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «＋ цитата» вручную (экран цитат, карточка книги): книга, текст, теги.
 * Книга заранее выбрана (из карточки) или последняя начатая из тех, что читаются.
 */
object QuoteDialog {

    fun show(activity: AppCompatActivity, presetBook: Long?, onSaved: () -> Unit) {
        activity.lifecycleScope.launch {
            val db = AppDatabase.getInstance(activity)
            val (books, tags) = withContext(Dispatchers.IO) {
                val all = db.bookDao().getAllBooksSync()
                // Сначала те, что читаются и отложены, потом остальные; свежие выше
                val sorted = all.filter { Book.isOpen(it.status) }.sortedByDescending { it.startDate } +
                    all.filter { !Book.isOpen(it.status) }.sortedByDescending { it.endDate ?: it.startDate }
                sorted to db.quoteDao().recentTagRows().flatMap { Quote.splitTags(it) }.distinct().take(14)
            }
            if (books.isEmpty()) { Toast.makeText(activity, "Сначала добавь книгу", Toast.LENGTH_SHORT).show(); return@launch }
            build(activity, books, presetBook ?: books.first().id, tags, onSaved)
        }
    }

    private fun build(activity: AppCompatActivity, books: List<Book>, preset: Long, tags: List<String>, onSaved: () -> Unit) {
        val dp = activity.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        var bookId = preset
        fun name(id: Long) = books.firstOrNull { it.id == id }?.let { Book.displayName(it) } ?: "выбрать книгу"

        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20f), px(8f), px(20f), 0) }
        val bookView = TextView(activity).apply {
            text = "${name(bookId)}  ▾"; textSize = 15f
            setTextColor(RtColors.c(activity, R.color.rt_ink))
            setPadding(0, px(4f), 0, px(8f)); maxLines = 2
            contentDescription = "книга, сменить"
        }
        bookView.setOnClickListener {
            val menu = PopupMenu(activity, bookView)
            books.forEachIndexed { i, b -> menu.menu.add(0, i, i, Book.displayName(b)) }
            menu.setOnMenuItemClickListener { item ->
                bookId = books[item.itemId].id
                bookView.text = "${name(bookId)}  ▾"
                true
            }
            menu.show()
        }
        val etText = EditText(activity).apply {
            hint = "текст цитаты"; textSize = 15f; minLines = 3; maxLines = 12
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val etTags = EditText(activity).apply {
            hint = if (tags.isEmpty()) "теги через пробел" else "теги через пробел: " + tags.take(4).joinToString(" ") { "#$it" }
            textSize = 14f; isSingleLine = true
        }
        box.addView(bookView); box.addView(etText); box.addView(etTags)

        AlertDialog.Builder(activity)
            .setTitle("цитата")
            .setView(box)
            .setPositiveButton("Сохранить") { _, _ ->
                val t = etText.text.toString().trim()
                if (t.isEmpty()) { Toast.makeText(activity, "Цитата пустая", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val q = Quote(bookId = bookId, text = t, tags = Quote.joinTags(Quote.parseTags(etTags.text.toString())))
                activity.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { AppDatabase.getInstance(activity).quoteDao().insert(q) }
                    Toast.makeText(activity, "Цитата сохранена", Toast.LENGTH_SHORT).show()
                    onSaved()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
