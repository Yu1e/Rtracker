package com.readingtracker.ui.book

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.readingtracker.Constants
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.data.BookActions
import com.readingtracker.databinding.ActivityBookBinding
import com.readingtracker.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** Карточка книги: просмотр, правка, ручное добавление. */
class BookActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "book_id"

        fun open(context: Context, bookId: Long) {
            context.startActivity(Intent(context, BookActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        fun create(context: Context) {
            context.startActivity(Intent(context, BookActivity::class.java))
        }
    }

    private lateinit var b: ActivityBookBinding
    private val db by lazy { AppDatabase.getInstance(this) }
    private val fmt = SimpleDateFormat("d MMMM yyyy", Locale("ru"))

    private var book: Book? = null
    private var startDate = System.currentTimeMillis()
    private var endDate: Long? = null
    private lateinit var sources: List<Pair<String, String>>
    private var status = Book.STATUS_READING
    private var genres = mutableListOf<String>()
    private val tiles by lazy {
        listOf(b.stReading to Book.STATUS_READING, b.stFinished to Book.STATUS_FINISHED,
            b.stPaused to Book.STATUS_PAUSED, b.stAbandoned to Book.STATUS_ABANDONED,
            b.stRereading to Book.STATUS_REREADING)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.readingtracker.ui.RtTheme.apply(this)
        super.onCreate(savedInstanceState)
        b = ActivityBookBinding.inflate(layoutInflater)
        setContentView(b.root)

        sources = Constants.sourceOptions(this)
        b.spSource.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            sources.map { it.first })

        // Адрес — только у книги «В интернете»
        b.spSource.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                b.urlBlock.visibility = if (sources.getOrNull(pos)?.second == Book.PKG_ONLINE) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        b.rating.onChange = { updateRatingLabel(it) }
        b.btnStartDate.setOnClickListener { pickDate(startDate) { startDate = it; updateDates() } }
        b.btnEndDate.setOnClickListener { pickEndDate() }
        b.btnSave.setOnClickListener { save() }
        b.tvGenres.setOnClickListener { pickGenres() }
        updateGenres()
        for ((tile, st) in tiles) {
            tile.status = st
            tile.setOnClickListener { pickStatus(st) }
        }

        lifecycleScope.launch {
            val authors = db.bookDao().getAllAuthors()
            b.etAuthor.setAdapter(ArrayAdapter(this@BookActivity,
                android.R.layout.simple_dropdown_item_1line, authors))
        }

        val id = intent.getLongExtra(EXTRA_BOOK_ID, -1)
        if (id > 0) loadBook(id) else setupNew()
    }

    private fun setupNew() {
        b.tvHeader.text = "Новая книга"
        showStatus(Book.STATUS_READING)
        b.spSource.setSelection(sources.indexOfFirst { it.second == Book.PKG_PAPER }.coerceAtLeast(0))
        updateRatingLabel(0)
        updateDates()
        b.manualTimeBlock.visibility = View.VISIBLE
    }

    private fun showManual(minutes: Long) {
        b.manualTimeBlock.visibility = View.VISIBLE
        if (minutes > 0) {
            b.etManualHours.setText((minutes / 60).toString())
            b.etManualMinutes.setText((minutes % 60).toString())
        }
    }

    /** Часы и минуты из полей; пустые поля — ноль, минуты больше 59 тоже принимаются. */
    private fun manualFromFields(): Long {
        val h = b.etManualHours.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val m = b.etManualMinutes.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        return (h * 60 + m).coerceAtLeast(0)
    }

    private fun loadBook(id: Long) {
        lifecycleScope.launch {
            val bk = db.bookDao().getBookById(id)
            if (bk == null) { finish(); return@launch }
            book = bk
            b.tvHeader.text = "Книга"
            b.etTitle.setText(bk.title)
            b.etAuthor.setText(bk.author, false)
            val idx = sources.indexOfFirst { it.second == bk.appPackage }
                .takeIf { it >= 0 } ?: sources.indexOfFirst { it.first == bk.appName }
            if (idx >= 0) b.spSource.setSelection(idx)
            startDate = bk.startDate
            endDate = bk.endDate
            updateDates()
            showStatus(bk.status)
            b.rating.value = bk.rating ?: 0
            updateRatingLabel(bk.rating ?: 0)
            b.etNotes.setText(bk.notes)
            b.etUrl.setText(bk.url)
            genres = Book.splitGenres(bk.genres).toMutableList()
            updateGenres()

            showBookStats(bk)
            showReadings(bk)

            // Цитаты книги
            val nQuotes = db.quoteDao().countForBook(bk.id)
            b.btnQuotes.visibility = if (nQuotes > 0) View.VISIBLE else View.GONE
            b.btnQuotes.text = "цитаты: $nQuotes"
            b.btnQuotes.setOnClickListener { com.readingtracker.ui.quotes.QuotesActivity.open(this@BookActivity, bk.id) }
            b.btnAddQuote.visibility = View.VISIBLE
            b.btnAddQuote.setOnClickListener {
                com.readingtracker.ui.quotes.QuoteDialog.show(this@BookActivity, bk.id) {
                    lifecycleScope.launch {
                        val n = db.quoteDao().countForBook(bk.id)
                        b.btnQuotes.visibility = if (n > 0) View.VISIBLE else View.GONE
                        b.btnQuotes.text = "цитаты: $n"
                    }
                }
            }

            val launch = packageManager.getLaunchIntentForPackage(bk.appPackage)
            if (bk.appPackage == Book.PKG_ONLINE && bk.url.isNotBlank()) {
                // Книга в интернете: открывается в браузере, трекер следит за адресом
                b.btnOpenReader.visibility = View.VISIBLE
                b.btnOpenReader.text = "Открыть в браузере"
                b.btnOpenReader.setOnClickListener {
                    com.readingtracker.service.WebReading.open(this@BookActivity, bk.id, bk.url)
                }
            } else if (launch != null) {
                b.btnOpenReader.visibility = View.VISIBLE
                b.btnOpenReader.text = "Открыть в ${bk.appName}"
                b.btnOpenReader.setOnClickListener {
                    com.readingtracker.launcher.ReaderLauncher.open(this@BookActivity, bk.appPackage)
                }
            }
            b.btnDelete.visibility = View.VISIBLE
            b.btnDelete.setOnClickListener { confirmDelete(bk) }
        }
    }

    private fun showStatus(st: String) {
        status = st
        for ((tile, s) in tiles) tile.checked = s == st
        updateDates()
    }

    /** Нажатие на плитку: рамка переходит, у сохранённой книги статус меняется сразу. */
    private fun pickStatus(st: String) {
        if (st == status) return
        showStatus(st)
        val id = book?.id ?: return
        lifecycleScope.launch {
            BookActions.setStatus(this@BookActivity, id, st)
            val fresh = db.bookDao().getBookById(id) ?: return@launch
            book = fresh
            endDate = fresh.endDate
            updateDates()
            showReadings(fresh)
        }
    }

    /**
     * Прочтения — только у книги, которую перечитывают после первой карточки:
     * «Первое: с 2 по 19 октября 2026, 7 ч 12 мин», … и «Всего за все прочтения».
     */
    private suspend fun showReadings(bk: Book) {
        val parts = com.readingtracker.data.Readings.parts(bk, db.readingDao().forBook(bk.id), db.sessionDao().getSessionsForBook(bk.id))
        if (parts.size < 2) { b.tvReadings.visibility = View.GONE; return }
        val text = buildString {
            for (p in parts) {
                val r = p.reading
                val shown = bk.copy(startDate = r.startDate, endDate = r.endDate, status = r.status)
                val state = when (r.status) {
                    Book.STATUS_REREADING, Book.STATUS_READING -> ", идёт"
                    Book.STATUS_PAUSED -> ", отложено"
                    Book.STATUS_ABANDONED -> ", брошено"
                    else -> ""
                }
                append(com.readingtracker.data.Readings.ordinal(r.number)).append(": ")
                    .append(com.readingtracker.export.Exporter.whenText(shown)).append(state)
                    .append(", ").append(formatMinutes(p.minutes)).append('\n')
            }
            append("Всего за все прочтения: ").append(formatMinutes(parts.sumOf { it.minutes }))
        }
        b.tvReadings.text = text
        b.tvReadings.visibility = View.VISIBLE
    }

    private suspend fun showBookStats(bk: Book) {
        val sessions = db.sessionDao().getSessionsForBook(bk.id)
        // Книга внесена руками (нет записанных подходов) или время уже добавлялось вручную
        if (sessions.isEmpty() || bk.manualMinutes > 0) showManual(bk.manualMinutes)
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val readingDays = sessions.map { dayFmt.format(Date(it.startTime)) }.toSet().size
        val text = buildString {
            appendLine("Время за чтением: ${formatMinutes(bk.totalMinutes)}" +
                if (bk.manualMinutes > 0 && sessions.isNotEmpty()) " (из них вручную ${formatMinutes(bk.manualMinutes)})" else "")
            appendLine("Дней на книгу: ${BookActions.daysOnBook(bk)}" +
                if (bk.endDate == null) " (и продолжается)" else "")
            appendLine("Дней с чтением: $readingDays")
            if (readingDays > 0) appendLine("В среднем за день чтения: ${formatMinutes(bk.totalMinutes / readingDays)}")
            if (bk.pagesTotal > 0) appendLine("Страницы: ${bk.pagesRead} из ${bk.pagesTotal}")
            appendLine("Подходов к книге: ${sessions.size}")
            sessions.lastOrNull()?.let {
                appendLine("Последний раз: ${SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(it.startTime))}")
            }
        }.trim()
        b.tvBookStats.text = text
        b.tvBookStats.visibility = View.VISIBLE
    }

    private fun updateGenres() {
        b.tvGenres.text = if (genres.isEmpty()) "не указан  ▾" else genres.joinToString(", ") + "  ▾"
    }

    /** Жанры галочками: у книги их может быть несколько. */
    private fun pickGenres() {
        val all = Book.GENRES
        val checked = BooleanArray(all.size) { all[it] in genres }
        AlertDialog.Builder(this)
            .setTitle("Жанр")
            .setMultiChoiceItems(all.toTypedArray(), checked) { _, which, on -> checked[which] = on }
            .setPositiveButton("Готово") { _, _ ->
                // Жанры не из списка (если появятся) сохраняются
                val extra = genres.filter { it !in all }
                genres = (all.filterIndexed { i, _ -> checked[i] } + extra).toMutableList()
                updateGenres()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateRatingLabel(v: Int) {
        b.tvRating.text = if (v == 0) "Оценка: без оценки" else "Оценка: $v из 10"
    }

    private fun updateDates() {
        b.btnStartDate.text = "Начата\n${fmt.format(Date(startDate))}"
        // Дата окончания — это дата, когда книга дочитана (у брошенной — когда брошена)
        val word = if (status == Book.STATUS_ABANDONED) "Брошена" else "Дочитана"
        b.btnEndDate.text = endDate?.let { "$word\n${fmt.format(Date(it))}" } ?: "$word\n—"
    }

    private fun pickDate(initial: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(this, { _, y, m, d ->
            val c = Calendar.getInstance().apply {
                timeInMillis = initial
                set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
            }
            onPicked(c.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    /** Дата «Дочитана»: книга, которая читалась или была отложена, сразу становится дочитанной. */
    private fun pickEndDate() {
        if (endDate == null) {
            pickDate(System.currentTimeMillis()) { setEnd(it) }
        } else {
            AlertDialog.Builder(this)
                .setItems(arrayOf("Изменить дату", "Убрать дату")) { _, which ->
                    if (which == 0) pickDate(endDate!!) { setEnd(it) }
                    else {
                        endDate = null
                        // Без даты книга снова читается
                        if (status == Book.STATUS_FINISHED) setStatusKeepingEnd(Book.STATUS_READING, null)
                        updateDates()
                    }
                }.show()
        }
    }

    private fun setEnd(date: Long) {
        endDate = date
        if (Book.isOpen(status)) setStatusKeepingEnd(Book.STATUS_FINISHED, date)
        updateDates()
    }

    /** Статус меняется сразу (как плиткой), выбранная дата окончания сохраняется. */
    private fun setStatusKeepingEnd(st: String, end: Long?) {
        showStatus(st)
        val id = book?.id ?: return
        lifecycleScope.launch {
            BookActions.setStatus(this@BookActivity, id, st)
            val fresh = db.bookDao().getBookById(id) ?: return@launch
            val fixed = fresh.copy(endDate = end)
            db.bookDao().updateBook(fixed)
            BookActions.syncLastReading(this@BookActivity, fixed)
            book = fixed
            showReadings(fixed)
        }
    }

    private fun save() {
        val title = b.etTitle.text.toString().trim()
        val author = b.etAuthor.text.toString().trim()
        if (title.isEmpty() && author.isEmpty()) {
            b.etTitle.error = "Заполни название или автора"
            return
        }
        val source = sources[b.spSource.selectedItemPosition.coerceAtLeast(0)]
        val rating = b.rating.value.takeIf { it > 0 }
        val notes = b.etNotes.text.toString()

        val base = (book ?: Book(title = title, author = author,
            appPackage = source.second, appName = source.first)).copy(
            title = title,
            author = author,
            appPackage = source.second,
            appName = source.first,
            startDate = startDate,
            endDate = endDate,
            rating = rating,
            notes = notes,
            genres = Book.joinGenres(genres),
            url = if (source.second == Book.PKG_ONLINE) b.etUrl.text?.toString()?.trim() ?: "" else (book?.url ?: "")
        ).let { bk ->
            if (b.manualTimeBlock.visibility != View.VISIBLE) bk
            else {
                val manual = manualFromFields()
                bk.copy(
                    manualMinutes = manual,
                    totalMinutes = (bk.totalMinutes - bk.manualMinutes + manual).coerceAtLeast(0)
                )
            }
        }
        var updated = BookActions.applyStatus(base, status)
        // Явно выбранная дата окончания сохраняется
        if (endDate != null && !Book.isReading(status)) updated = updated.copy(endDate = endDate)

        lifecycleScope.launch {
            if (book == null) {
                val id = db.bookDao().insertBook(updated)
                // Новая карточка сразу с «Перечитываю» — в статистику перечитываний
                if (status == Book.STATUS_REREADING) BookActions.markNewAsReread(this@BookActivity, id)
            } else {
                db.bookDao().updateBook(updated)
                BookActions.syncLastReading(this@BookActivity, updated)
            }
            WidgetUpdater.update(this@BookActivity)
            Toast.makeText(this@BookActivity, "Сохранено", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun confirmDelete(bk: Book) {
        AlertDialog.Builder(this)
            .setTitle("Удалить книгу?")
            .setMessage("${Book.displayName(bk)} и всё время чтения по ней будут удалены.")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    BookActions.delete(this@BookActivity, bk.id)
                    finish()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}

fun formatMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "$h ч $m мин"
        h > 0 -> "$h ч"
        else -> "$m мин"
    }
}
