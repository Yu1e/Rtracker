package com.readingtracker.ui.books

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.readingtracker.R
import com.readingtracker.data.Book
import com.readingtracker.databinding.ItemBookBinding
import java.text.SimpleDateFormat
import java.util.*

class BooksAdapter(
    private val onStatusChange: (Book, String) -> Unit,
    private val onDelete: (Book) -> Unit
) : ListAdapter<Book, BooksAdapter.ViewHolder>(Diff()) {

    private val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: ViewHolder, pos: Int) = h.bind(getItem(pos))

    inner class ViewHolder(private val b: ItemBookBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(book: Book) {
            b.tvTitle.text = book.title
            b.tvAuthor.text = if (book.author.isNotBlank()) book.author else "Автор не указан"
            b.tvApp.text = book.appName
            b.tvStartDate.text = "Начата: ${fmt.format(Date(book.startDate))}"

            b.tvStatus.text = when (book.status) {
                Book.STATUS_READING   -> "📖 Читаю"
                Book.STATUS_FINISHED  -> "✅ Дочитана"
                Book.STATUS_ABANDONED -> "❌ Брошена"
                Book.STATUS_PAUSED    -> "⏸ Отложена"
                else -> book.status
            }

            if (book.endDate != null) {
                b.tvEndDate.text = "Завершена: ${fmt.format(Date(book.endDate))}"
                b.tvEndDate.visibility = android.view.View.VISIBLE
            } else {
                b.tvEndDate.visibility = android.view.View.GONE
            }

            val h = book.totalMinutes / 60
            val m = book.totalMinutes % 60
            b.tvTime.text = when {
                h > 0 -> "${h} ч ${m} мин"
                m > 0 -> "${m} мин"
                else  -> "—"
            }

            b.btnMenu.setOnClickListener { v ->
                PopupMenu(v.context, v).apply {
                    menuInflater.inflate(R.menu.book_item_menu, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_finished  -> onStatusChange(book, Book.STATUS_FINISHED)
                            R.id.action_abandoned -> onStatusChange(book, Book.STATUS_ABANDONED)
                            R.id.action_paused    -> onStatusChange(book, Book.STATUS_PAUSED)
                            R.id.action_reading   -> onStatusChange(book, Book.STATUS_READING)
                            R.id.action_delete    -> onDelete(book)
                        }
                        true
                    }
                    show()
                }
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(a: Book, b: Book) = a.id == b.id
        override fun areContentsTheSame(a: Book, b: Book) = a == b
    }
}
