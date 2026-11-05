package com.readingtracker.ui.quotes

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.readingtracker.Prefs
import com.readingtracker.R
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.data.Quote
import com.readingtracker.export.Exporter
import com.readingtracker.export.TagPlace
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme
import com.readingtracker.ui.book.BookActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Цитаты: все выписки по книгам (свежие книги сверху), отбор по тегу, поиск, «поправить» у сохранённых
 * «на потом». Нажатие на цитату — правка текста и тегов или удаление. «⤓ в Obsidian» — на каждую
 * книгу с цитатами отдельная заметка с названием книги.
 */
class QuotesActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_BOOK = "book_id"
        private const val TAG_DRAFT = "\u0000draft"

        fun open(context: Context, bookId: Long? = null) {
            context.startActivity(Intent(context, QuotesActivity::class.java).apply {
                if (bookId != null) putExtra(EXTRA_BOOK, bookId)
            })
        }
    }

    private val dp get() = resources.displayMetrics.density
    private fun px(v: Float) = (v * dp).toInt()
    private fun col(id: Int) = RtColors.c(this, id)

    private var books: List<Book> = emptyList()
    private var quotes: List<Quote> = emptyList()
    private var onlyBook: Long? = null
    private var tag: String? = null
    private var query = ""

    private lateinit var list: LinearLayout
    private lateinit var tagsRow: LinearLayout
    private lateinit var titleView: TextView
    private val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) export(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        RtTheme.apply(this)
        super.onCreate(savedInstanceState)
        onlyBook = intent.getLongExtra(EXTRA_BOOK, -1L).takeIf { it > 0 }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(col(R.color.rt_bg))
            setPadding(px(16f), px(14f), px(16f), 0)
        }
        // Шапка: «цитаты» и выгрузка
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleView = TextView(this).apply {
            text = "цитаты"; textSize = 22f; setTypeface(typeface, Typeface.BOLD); setTextColor(col(R.color.rt_ink))
        }
        head.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(this).apply {
            text = "⤓ в Obsidian"; textSize = 14f; setTextColor(col(R.color.rt_ink))
            setPadding(px(12f), px(8f), px(4f), px(8f))
            setOnClickListener {
                if (quotes.isEmpty()) { toast("Цитат пока нет"); return@setOnClickListener }
                try { pickFolder.launch(Prefs.exportFolder(this@QuotesActivity)) }
                catch (e: Exception) { toast("Не открылось окно выбора папки: ${e.message}") }
            }
        })
        root.addView(head)

        // Поиск
        root.addView(EditText(this).apply {
            hint = "поиск по цитатам"
            textSize = 15f; setTextColor(col(R.color.rt_ink)); setHintTextColor(col(R.color.rt_ink3))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            background = GradientDrawable().apply { cornerRadius = 12 * dp; setStroke(px(1f), col(R.color.rt_ink3)) }
            setPadding(px(12f), px(9f), px(12f), px(9f))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim() ?: ""; render() }
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10f) })

        // Теги
        tagsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tagsRow)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8f) })

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, px(6f), 0, px(40f))
        }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@QuotesActivity)
            val (b, q) = withContext(Dispatchers.IO) { db.bookDao().getAllBooksSync() to db.quoteDao().getAll() }
            books = b; quotes = q
            render()
        }
    }

    private fun visible(): List<Quote> {
        val t = tag
        return quotes.filter { q -> matches(q, t) }
    }

    private fun matches(q: Quote, t: String?): Boolean =
        (onlyBook == null || q.bookId == onlyBook) &&
            (t == null || (t == TAG_DRAFT && q.draft) || Quote.splitTags(q.tags).contains(t)) &&
            (query.isEmpty() || q.text.contains(query, true) || q.tags.contains(query, true))
    }

    private fun render() {
        val one = onlyBook?.let { id -> books.firstOrNull { it.id == id } }
        titleView.text = if (one != null) "цитаты · ${Book.displayName(one)}" else "цитаты"
        renderTags()
        list.removeAllViews()
        val shown = visible()
        if (shown.isEmpty()) {
            list.addView(text(if (quotes.isEmpty())
                "Цитат пока нет. В читалке выдели текст и нажми «Копировать» — трекер предложит сохранить цитату."
                else "Ничего не нашлось.", 14f, col(R.color.rt_ink2)).apply { setPadding(0, px(16f), 0, 0) })
            return
        }
        // Книги: те, где цитаты свежее, — выше
        val byBook = shown.groupBy { it.bookId }.entries.sortedByDescending { e -> e.value.maxOf { it.createdAt } }
        for ((bookId, qs) in byBook) {
            val book = books.firstOrNull { it.id == bookId }
            if (onlyBook == null) {
                list.addView(text(book?.let { Book.displayName(it) } ?: "книга удалена", 16f, col(R.color.rt_ink), bold = true).apply {
                    setPadding(0, px(14f), 0, px(4f))
                    if (book != null) setOnClickListener { BookActivity.open(this@QuotesActivity, book.id) }
                })
            }
            for (q in qs.sortedBy { it.createdAt }) list.addView(quoteCard(q))
        }
    }

    private fun renderTags() {
        tagsRow.removeAllViews()
        val all = quotes.filter { onlyBook == null || it.bookId == onlyBook }
        val tags = all.flatMap { Quote.splitTags(it.tags) }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
        val chips = mutableListOf<Pair<String?, String>>(null to "все")
        if (all.any { it.draft }) chips += TAG_DRAFT to "● поправить"
        tags.forEach { chips += it to "#$it" }
        if (chips.size == 1) return
        for ((key, name) in chips) {
            val on = tag == key
            tagsRow.addView(TextView(this).apply {
                text = name; textSize = 13f
                setTextColor(if (on) RtColors.onColor(RtTheme.accent(this@QuotesActivity)) else col(R.color.rt_ink))
                setPadding(px(12f), px(6f), px(12f), px(6f))
                background = GradientDrawable().apply {
                    cornerRadius = 16 * dp
                    if (on) setColor(RtTheme.accent(this@QuotesActivity)) else setStroke(px(1f), col(R.color.rt_ink3))
                }
                setOnClickListener { tag = if (tag == key) null else key; render() }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(6f) })
        }
    }

    private fun quoteCard(q: Quote): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12f), px(10f), px(12f), px(10f))
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(RtColors.withAlpha(col(R.color.rt_ink), if (RtColors.isNight(this@QuotesActivity)) 0.08f else 0.05f))
            }
            setOnClickListener { edit(q) }
        }
        card.addView(text(q.text, 15f, col(R.color.rt_ink)).apply { maxLines = 12; ellipsize = android.text.TextUtils.TruncateAt.END })
        val meta = mutableListOf<String>()
        if (q.draft) meta += "● поправить"
        Quote.splitTags(q.tags).takeIf { it.isNotEmpty() }?.let { t -> meta += t.joinToString(" ") { "#$it" } }
        meta += Instant.ofEpochMilli(q.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
        card.addView(text(meta.joinToString("  ·  "), 12f, col(R.color.rt_ink2)).apply { setPadding(0, px(6f), 0, 0) })
        return LinearLayout(this).apply {
            setPadding(0, px(4f), 0, px(4f))
            addView(card, LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** Правка цитаты: текст и теги; «Удалить». Сохранённая правка снимает «поправить». */
    private fun edit(q: Quote) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20f), px(8f), px(20f), 0) }
        val etText = EditText(this).apply {
            setText(q.text); textSize = 15f; minLines = 3; maxLines = 12
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val etTags = EditText(this).apply {
            setText(Quote.splitTags(q.tags).joinToString(" ")); hint = "теги через пробел"; textSize = 14f; isSingleLine = true
        }
        box.addView(etText); box.addView(etTags)
        AlertDialog.Builder(this)
            .setTitle("цитата")
            .setView(box)
            .setPositiveButton("Сохранить") { _, _ ->
                val t = etText.text.toString().trim()
                if (t.isEmpty()) { toast("Цитата пустая"); return@setPositiveButton }
                save(q.copy(text = t, tags = Quote.joinTags(Quote.parseTags(etTags.text.toString())), draft = false))
            }
            .setNeutralButton("Удалить") { _, _ ->
                AlertDialog.Builder(this).setMessage("Удалить цитату?")
                    .setPositiveButton("Удалить") { _, _ -> remove(q) }
                    .setNegativeButton("Отмена", null).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun save(q: Quote) = lifecycleScope.launch {
        withContext(Dispatchers.IO) { AppDatabase.getInstance(this@QuotesActivity).quoteDao().update(q) }
        load()
    }

    private fun remove(q: Quote) = lifecycleScope.launch {
        withContext(Dispatchers.IO) { AppDatabase.getInstance(this@QuotesActivity).quoteDao().delete(q) }
        load()
    }

    /** На каждую книгу с цитатами — заметка с названием книги; одноимённые перезаписываются. */
    private fun export(folder: Uri) {
        Prefs.setExportFolder(this, folder)
        val ctx = applicationContext
        val pick = onlyBook
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val qs = if (pick == null) quotes else quotes.filter { it.bookId == pick }
                    val tags = Exporter.parseTags(Prefs.exportTags(ctx))
                    val place = TagPlace.values().firstOrNull { it.key == Prefs.exportTagPlace(ctx) } ?: TagPlace.YAML
                    Exporter.write(ctx, folder, Exporter.quoteNotes(books, qs, tags, place))
                }
            }
            r.fold(
                { n -> toast(if (n == 1) "Заметка сохранена" else "Сохранено заметок: $n") },
                { toast("Не сохранилось: ${it.message}") }
            )
        }
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun toast(t: String) { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }
}
