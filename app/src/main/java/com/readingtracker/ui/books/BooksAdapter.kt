package com.readingtracker.ui.books

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.readingtracker.R
import com.readingtracker.data.Book
import com.readingtracker.data.BookActions
import com.readingtracker.databinding.ItemBookBinding
import com.readingtracker.ui.book.formatMinutes
import java.text.SimpleDateFormat
import java.util.*

class BooksAdapter(
    private val onOpen: (Book) -> Unit,
    private val onStatusChange: (Book, String) -> Unit
) : ListAdapter<Book, BooksAdapter.ViewHolder>(Diff()) {

    private val fmt = SimpleDateFormat("d MMM yyyy", Locale("ru"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: ViewHolder, pos: Int) = h.bind(getItem(pos))

    inner class ViewHolder(private val b: ItemBookBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(book: Book) {
            b.tvTitle.text = book.title.ifBlank { "Без названия" }
            b.tvAuthor.text = book.author.ifBlank { "Автор не указан" }
            b.tvAsk.visibility = if (BookActions.isStatusDue(book)) View.VISIBLE else View.GONE

            val status = Book.statusLabel(book.status)
            val rating = book.rating?.let { "  ·  ★ $it/10" } ?: ""
            b.tvStatus.text = status + rating

            val dates = buildString {
                append(fmt.format(Date(book.startDate)))
                book.endDate?.let { append(" — ").append(fmt.format(Date(it))) }
            }
            val days = BookActions.daysOnBook(book)
            b.tvMeta.text = "${book.appName}  ·  $dates  ·  дней: $days"

            b.tvTime.text = if (book.totalMinutes > 0) formatMinutes(book.totalMinutes) else ""
            b.tvTime.visibility = if (book.totalMinutes > 0) View.VISIBLE else View.GONE

            b.root.setOnClickListener { onOpen(book) }
            b.btnMenu.setOnClickListener { v ->
                PopupMenu(v.context, v).apply {
                    menuInflater.inflate(R.menu.book_item_menu, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_open      -> onOpen(book)
                            R.id.action_finished  -> onStatusChange(book, Book.STATUS_FINISHED)
                            R.id.action_abandoned -> onStatusChange(book, Book.STATUS_ABANDONED)
                            R.id.action_paused    -> onStatusChange(book, Book.STATUS_PAUSED)
                            R.id.action_reading   -> onStatusChange(book, Book.STATUS_READING)
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
