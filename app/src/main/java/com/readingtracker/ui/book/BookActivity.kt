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
import com.readingtracker.R
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBookBinding.inflate(layoutInflater)
        setContentView(b.root)

        sources = Constants.sourceOptions(this)
        b.spSource.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            sources.map { it.first })

        b.slRating.addOnChangeListener { _, value, _ -> updateRatingLabel(value.toInt()) }
        b.btnStartDate.setOnClickListener { pickDate(startDate) { startDate = it; updateDates() } }
        b.btnEndDate.setOnClickListener { pickEndDate() }
        b.btnSave.setOnClickListener { save() }

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
        b.rbReading.isChecked = true
        b.spSource.setSelection(sources.indexOfFirst { it.second == Book.PKG_PAPER }.coerceAtLeast(0))
        updateRatingLabel(0)
        updateDates()
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
            when (bk.status) {
                Book.STATUS_FINISHED -> b.rbFinished.isChecked = true
                Book.STATUS_ABANDONED -> b.rbAbandoned.isChecked = true
                Book.STATUS_PAUSED -> b.rbPaused.isChecked = true
                else -> b.rbReading.isChecked = true
            }
            b.slRating.value = (bk.rating ?: 0).toFloat()
            updateRatingLabel(bk.rating ?: 0)
            b.etNotes.setText(bk.notes)

            showBookStats(bk)

            val launch = packageManager.getLaunchIntentForPackage(bk.appPackage)
            if (launch != null) {
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

    private suspend fun showBookStats(bk: Book) {
        val sessions = db.sessionDao().getSessionsForBook(bk.id)
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val readingDays = sessions.map { dayFmt.format(Date(it.startTime)) }.toSet().size
        val text = buildString {
            appendLine("Время за чтением: ${formatMinutes(bk.totalMinutes)}")
            appendLine("Дней на книгу: ${BookActions.daysOnBook(bk)}" +
                if (bk.endDate == null) " (и продолжается)" else "")
            appendLine("Дней, когда читала: $readingDays")
            if (readingDays > 0) appendLine("В среднем за день чтения: ${formatMinutes(bk.totalMinutes / readingDays)}")
            appendLine("Подходов к книге: ${sessions.size}")
            sessions.lastOrNull()?.let {
                appendLine("Последний раз: ${SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(it.startTime))}")
            }
        }.trim()
        b.tvBookStats.text = text
        b.tvBookStats.visibility = View.VISIBLE
    }

    private fun updateRatingLabel(v: Int) {
        b.tvRating.text = if (v == 0) "Оценка: без оценки" else "Оценка: $v из 10"
    }

    private fun updateDates() {
        b.btnStartDate.text = "Начата\n${fmt.format(Date(startDate))}"
        b.btnEndDate.text = endDate?.let { "Закончена\n${fmt.format(Date(it))}" } ?: "Закончена\n—"
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

    private fun pickEndDate() {
        if (endDate == null) {
            pickDate(System.currentTimeMillis()) { endDate = it; updateDates() }
        } else {
            AlertDialog.Builder(this)
                .setItems(arrayOf("Изменить дату", "Убрать дату окончания")) { _, which ->
                    if (which == 0) pickDate(endDate!!) { endDate = it; updateDates() }
                    else { endDate = null; updateDates() }
                }.show()
        }
    }

    private fun save() {
        val title = b.etTitle.text.toString().trim()
        val author = b.etAuthor.text.toString().trim()
        if (title.isEmpty() && author.isEmpty()) {
            b.etTitle.error = "Заполните название или автора"
            return
        }
        val status = when (b.rgStatus.checkedRadioButtonId) {
            R.id.rbFinished -> Book.STATUS_FINISHED
            R.id.rbAbandoned -> Book.STATUS_ABANDONED
            R.id.rbPaused -> Book.STATUS_PAUSED
            else -> Book.STATUS_READING
        }
        val source = sources[b.spSource.selectedItemPosition.coerceAtLeast(0)]
        val rating = b.slRating.value.toInt().takeIf { it > 0 }
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
            notes = notes
        )
        var updated = BookActions.applyStatus(base, status)
        // Явно выбранная дата окончания сохраняется
        if (endDate != null && status != Book.STATUS_READING) updated = updated.copy(endDate = endDate)

        lifecycleScope.launch {
            if (book == null) db.bookDao().insertBook(updated) else db.bookDao().updateBook(updated)
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
