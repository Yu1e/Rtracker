package com.readingtracker.ui.stats

import android.app.DatePickerDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.CompoundButtonCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.readingtracker.Prefs
import com.readingtracker.R
import com.readingtracker.data.Book
import com.readingtracker.data.ReadingSession
import com.readingtracker.export.BookField
import com.readingtracker.export.ExportJob
import com.readingtracker.export.Exporter
import com.readingtracker.export.StatsPart
import com.readingtracker.export.TagPlace
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Лист «экспорт» снизу: статистика за период или карточки книг в .md.
 * Готовое задание отдаётся в onExport, папку выбирает экран статистики.
 */
class ExportSheet(
    private val fragment: Fragment,
    private val books: List<Book>,
    private val sessions: List<ReadingSession>,
    private val onExport: (ExportJob) -> Unit
) {
    private val ctx = fragment.requireContext()
    private val dp = ctx.resources.displayMetrics.density
    private val dialog = BottomSheetDialog(ctx)
    private val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    private var booksMode = false
    private val statuses = StatsFragment.STATUSES.map { it.first }.toMutableSet()

    // Статистика
    private var periodKey = "this_month"
    private var from: LocalDate? = null
    private var to: LocalDate? = null
    private var periodTitle = ""
    private var periodFile = ""
    private val parts = mutableSetOf(StatsPart.SUMMARY, StatsPart.DAYS, StatsPart.BOOKS, StatsPart.LISTS)
    private var csv = false

    // Карточки
    private var single = true
    private val fields = BookField.values().toMutableSet()

    // Теги
    private var tagsText = Prefs.exportTags(ctx)
    private var tagPlace = TagPlace.values().firstOrNull { it.key == Prefs.exportTagPlace(ctx) } ?: TagPlace.YAML

    private val today = LocalDate.now()

    init {
        setThisMonth()
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(col(R.color.rt_bg))
            addView(root)
        }
        root.setPadding(px(18), px(16), px(18), px(28))
        dialog.setContentView(scroll)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                it.setBackgroundColor(col(R.color.rt_bg))
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
                BottomSheetBehavior.from(it).skipCollapsed = true
            }
        }
        rebuild()
    }

    fun show() = dialog.show()

    // ── Содержимое ───────────────────────────────────────────────────────────

    private fun rebuild() {
        root.removeAllViews()
        root.addView(text("экспорт", 20f, bold = true))
        choice(listOf("stats" to "статистика", "books" to "карточки книг"), if (booksMode) "books" else "stats") {
            booksMode = it == "books"; rebuild()
        }

        if (!booksMode) {
            label("период")
            choice(listOf(
                "this_month" to "этот месяц",
                "month" to (if (periodKey == "month") periodTitle else "месяц…"),
                "year" to (if (periodKey == "year") periodTitle else "год…"),
                "all" to "всё время",
                "range" to (if (periodKey == "range") periodTitle else "с … по …")
            ), periodKey) { pickPeriod(it) }

            label("что войдёт")
            check("сводка и итоги", StatsPart.SUMMARY in parts) { toggle(parts, StatsPart.SUMMARY, it) }
            check("день за днём (минуты)", StatsPart.DAYS in parts) { toggle(parts, StatsPart.DAYS, it) }
            check("книги: время, статус, даты", StatsPart.BOOKS in parts) { toggle(parts, StatsPart.BOOKS, it) }
            check("когда читаю (часы, дни недели)", StatsPart.WHEN in parts) { toggle(parts, StatsPart.WHEN, it) }
            check("авторы, жанры, читалки, оценки", StatsPart.LISTS in parts) { toggle(parts, StatsPart.LISTS, it) }
            check("сеансы подробно", StatsPart.SESSIONS in parts) { toggle(parts, StatsPart.SESSIONS, it) }
            label("файлы")
            small("Основной файл — .md (таблицы откроются в Obsidian).")
            check("ещё и таблицы .csv (для Excel и других таблиц)", csv) { csv = it }
        } else {
            label("файлы")
            choice(listOf("one" to "все в один .md", "many" to "каждая книга — свой .md"), if (single) "one" else "many") {
                single = it == "one"; rebuild()
            }
            label("что в карточке")
            check("автор, название, жанр", BookField.MAIN in fields) { toggle(fields, BookField.MAIN, it) }
            check("статус, когда, источник", BookField.STATUS in fields) { toggle(fields, BookField.STATUS, it) }
            check("сколько времени, оценка", BookField.TIME in fields) { toggle(fields, BookField.TIME, it) }
            check("заметки", BookField.NOTES in fields) { toggle(fields, BookField.NOTES, it) }
            check(if (single) "цитаты (отдельными заметками, название книги — ссылка)" else "цитаты (после сведений о книге)",
                BookField.QUOTES in fields) { toggle(fields, BookField.QUOTES, it) }
        }

        label("какие книги")
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        for ((st, name) in StatsFragment.STATUSES) {
            row.addView(checkBox(name, st in statuses) { on ->
                if (on) statuses += st else statuses -= st
            })
        }
        root.addView(row)

        label("теги")
        val et = EditText(ctx).apply {
            setText(tagsText)
            hint = "через пробел или запятую: книги, чтение"
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 15f
            setTextColor(col(R.color.rt_ink))
            setHintTextColor(col(R.color.rt_ink3))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { tagsText = s?.toString() ?: "" }
            })
        }
        root.addView(et)
        small("Obsidian не считает тегом одни цифры: #2026 не сработает, #чтение2026 и #год/2026 — сработают.")
        choice(listOf(TagPlace.YAML.key to "в YAML", TagPlace.FIRST.key to "первой строкой", TagPlace.LAST.key to "последней строкой"),
            tagPlace.key) { k -> tagPlace = TagPlace.values().first { it.key == k }; rebuild() }

        val go = text(if (booksMode) "выбрать папку и сохранить" else "выбрать папку и сохранить файл", 16f, bold = true).apply {
            gravity = Gravity.CENTER
            setTextColor(col(R.color.rt_on_strong))
            background = GradientDrawable().apply { cornerRadius = 16 * dp; setColor(col(R.color.rt_strong)) }
            setPadding(0, px(13), 0, px(13))
            setOnClickListener { submit() }
        }
        root.addView(go, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(18) })
        Fonts.apply(root)
    }

    private fun submit() {
        if (statuses.isEmpty()) { toast("Отметь хотя бы один статус книг"); return }
        if (!booksMode && parts.isEmpty()) { toast("Отметь, что войдёт в файл"); return }
        if (booksMode && fields.isEmpty() && single) { toast("Отметь, что будет в карточке"); return }
        val tags = Exporter.parseTags(tagsText)
        Prefs.setExportTags(ctx, tagsText, tagPlace.key)
        val job = if (booksMode) ExportJob(
            books = true, statuses = statuses.toSet(), tags = tags, tagPlace = tagPlace,
            singleFile = single, fields = fields.toSet()
        ) else ExportJob(
            books = false, statuses = statuses.toSet(), tags = tags, tagPlace = tagPlace,
            from = from, to = to, periodTitle = periodTitle, periodFile = periodFile,
            parts = parts.toSet(), csv = csv
        )
        dialog.dismiss()
        onExport(job)
    }

    // ── Период ───────────────────────────────────────────────────────────────

    private fun setThisMonth() {
        periodKey = "this_month"
        from = today.withDayOfMonth(1); to = today
        periodTitle = "${MONTHS_NOM[today.monthValue - 1]} ${today.year}"
        periodFile = "%d-%02d".format(today.year, today.monthValue)
    }

    private fun firstDay(): LocalDate {
        val zone = ZoneId.systemDefault()
        val ms = (sessions.map { it.startTime } + books.map { it.startDate }).minOrNull() ?: return today
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    }

    private fun pickPeriod(key: String) {
        when (key) {
            "this_month" -> { setThisMonth(); rebuild() }
            "all" -> {
                periodKey = "all"; from = null; to = null
                periodTitle = "всё время"; periodFile = "всё время"; rebuild()
            }
            "month" -> {
                val start = firstDay().withDayOfMonth(1)
                val months = generateSequence(today.withDayOfMonth(1)) { it.minusMonths(1) }
                    .takeWhile { !it.isBefore(start) }.toList()
                AlertDialog.Builder(ctx).setTitle("месяц")
                    .setItems(months.map { "${MONTHS_NOM[it.monthValue - 1]} ${it.year}" }.toTypedArray()) { _, i ->
                        val m = months[i]
                        periodKey = "month"; from = m; to = minOf(m.plusMonths(1).minusDays(1), today)
                        periodTitle = "${MONTHS_NOM[m.monthValue - 1]} ${m.year}"
                        periodFile = "%d-%02d".format(m.year, m.monthValue)
                        rebuild()
                    }.show()
            }
            "year" -> {
                val years = (today.year downTo firstDay().year).toList()
                AlertDialog.Builder(ctx).setTitle("год")
                    .setItems(years.map { "$it" }.toTypedArray()) { _, i ->
                        val y = years[i]
                        periodKey = "year"; from = LocalDate.of(y, 1, 1); to = minOf(LocalDate.of(y, 12, 31), today)
                        periodTitle = "$y"; periodFile = "$y"
                        rebuild()
                    }.show()
            }
            "range" -> pickDate(from ?: today.withDayOfMonth(1), "с какого дня") { a ->
                pickDate(maxOf(a, to ?: today), "по какой день") { z0 ->
                    val z = if (z0.isBefore(a)) a else z0
                    periodKey = "range"; from = a; to = z
                    periodTitle = rangeTitle(a, z)
                    periodFile = "$a — $z"
                    rebuild()
                }
            }
        }
    }

    private fun rangeTitle(a: LocalDate, z: LocalDate): String {
        fun full(d: LocalDate) = "${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]} ${d.year}"
        return when {
            a == z -> full(a)
            a.year != z.year -> "с ${full(a)} по ${full(z)}"
            a.monthValue != z.monthValue -> "с ${a.dayOfMonth} ${MONTHS_GEN[a.monthValue - 1]} по ${full(z)}"
            else -> "с ${a.dayOfMonth} по ${full(z)}"
        }
    }

    private fun pickDate(initial: LocalDate, title: String, onPick: (LocalDate) -> Unit) {
        DatePickerDialog(ctx, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
            setTitle(title)
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    // ── Элементы ─────────────────────────────────────────────────────────────

    private fun <T> toggle(set: MutableSet<T>, v: T, on: Boolean) { if (on) set += v else set -= v }

    private fun label(t: String) {
        root.addView(text(t, 14f, bold = true).apply {
            setTextColor(col(R.color.rt_ink2)); setPadding(0, px(14), 0, px(6))
        })
    }

    private fun small(t: String) {
        root.addView(text(t, 12f).apply { setTextColor(col(R.color.rt_ink3)); setPadding(0, px(2), 0, px(4)) })
    }

    private fun check(t: String, on: Boolean, onChange: (Boolean) -> Unit) { root.addView(checkBox(t, on, onChange)) }

    private fun checkBox(t: String, on: Boolean, onChange: (Boolean) -> Unit) = CheckBox(ctx).apply {
        text = t; textSize = 14f; isChecked = on
        setTextColor(col(R.color.rt_ink))
        CompoundButtonCompat.setButtonTintList(this, android.content.res.ColorStateList.valueOf(col(R.color.rt_ink2)))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    /** Ряд вариантов-«пилюль», выбран один. Переносится на новую строку, если не помещается. */
    private fun choice(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
        val wrap = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        var line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        var used = 0f
        val maxW = ctx.resources.displayMetrics.widthPixels / dp - 36f
        wrap.addView(line)
        for ((key, label) in options) {
            val on = key == current
            val tv = text(label, 14f).apply {
                setTextColor(if (on) col(R.color.rt_on_strong) else col(R.color.rt_ink))
                background = GradientDrawable().apply {
                    cornerRadius = 16 * dp
                    setColor(if (on) col(R.color.rt_strong) else 0)
                    setStroke((1.3f * dp).toInt().coerceAtLeast(1), if (on) col(R.color.rt_strong) else col(R.color.rt_ink2))
                }
                setPadding(px(12), px(6), px(12), px(6))
                setOnClickListener { onPick(key) }
            }
            val w = tv.paint.measureText(label) / dp + 24f + 6f
            if (used + w > maxW && used > 0f) {
                line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                wrap.addView(line, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(6) })
                used = 0f
            }
            line.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(6) })
            used += w
        }
        root.addView(wrap, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) })
    }

    private fun text(t: String, size: Float, bold: Boolean = false) = TextView(ctx).apply {
        text = t; textSize = size; setTextColor(col(R.color.rt_ink))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun col(id: Int) = RtColors.c(ctx, id)
    private fun px(v: Int) = (v * dp).toInt()
    private fun toast(t: String) { Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show() }

    companion object {
        private val MONTHS_NOM = arrayOf(
            "январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
        )
        private val MONTHS_GEN = arrayOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
        )
    }
}
