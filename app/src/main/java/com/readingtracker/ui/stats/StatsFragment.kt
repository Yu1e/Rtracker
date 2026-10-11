package com.readingtracker.ui.stats

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.readingtracker.R
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.databinding.FragmentStatsBinding
import com.readingtracker.stats.BarChartView
import com.readingtracker.stats.HeatmapView
import com.readingtracker.stats.Stats
import com.readingtracker.stats.StatsCalc
import com.readingtracker.ui.book.BookActivity
import com.readingtracker.ui.book.formatMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class StatsFragment : Fragment() {

    private var _b: FragmentStatsBinding? = null
    private val b get() = _b!!
    private val dp get() = resources.displayMetrics.density

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentStatsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val books = db.bookDao().getAllBooksSync()
            val sessions = db.sessionDao().getAllSessions()
            val st = withContext(Dispatchers.Default) { StatsCalc.compute(books, sessions) }
            if (_b != null) render(st)
        }
    }

    // ── Построение экрана ─────────────────────────────────────────────────────

    private fun render(s: Stats) {
        val c = b.container
        c.removeAllViews()
        header("Статистика", 22f)

        // Крупные цифры
        tiles(
            "Сегодня" to (if (s.todayMinutes > 0) formatMinutes(s.todayMinutes) else "0 мин"),
            "Стрик" to "${s.currentStreak} ${daysWord(s.currentStreak)}",
            "В этом месяце" to formatHours(s.monthMinutes)
        )
        if (s.currentStreak > 0 && !s.readToday) {
            note("Сегодня ещё не читалось — стрик продолжится, если открыть книгу до полуночи.")
        }
        if (s.bestStreak > s.currentStreak && s.currentStreak > 0) {
            note("До рекорда стрика (${s.bestStreak}) осталось ${s.bestStreak - s.currentStreak} ${daysWord(s.bestStreak - s.currentStreak)}.")
        } else if (s.currentStreak > 0 && s.currentStreak == s.bestStreak) {
            note("Это твой лучший стрик!")
        }

        // Календарь
        section("Календарь чтения")
        val heat = HeatmapView(requireContext())
        val dayInfo = smallText("Нажми на день, чтобы увидеть время")
        heat.setData(s.dayMinutes, s.today)
        heat.onDaySelected = { day, m ->
            dayInfo.text = "${day.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru")))}: " +
                if (m > 0) formatMinutes(m) else "не читалось"
        }
        c.addView(heat)
        heatLegend()
        c.addView(dayInfo)

        // Последние 30 дней
        section("Минуты по дням, последние 30 дней")
        val days = (29 downTo 0).map { s.today.minusDays(it.toLong()) }
        chart(days.map { it.dayOfMonth.toString() }, days.map { (s.dayMinutes[it] ?: 0).toFloat() },
            labelEvery = 5, highlightLast = true) { formatMinutes(it.toLong()) }

        // Месяц
        section("Этот месяц")
        kv("Время чтения", formatHours(s.monthMinutes) + compare(s.monthMinutes, s.prevMonthMinutes, this::formatHours))
        kv("Дней с чтением", "${s.monthReadingDays}")
        kv("Книг дочитано", "${s.monthFinished}" + compareInt(s.monthFinished, s.prevMonthFinished))
        kv("Книг начато", "${s.monthStarted}")
        kv("За эту неделю", formatHours(s.weekMinutes))

        section("Часы чтения по месяцам")
        chart(s.monthsLabels, s.monthsMinutes.map { it / 60f }, labelEvery = 2, highlightLast = true) {
            formatHours((it * 60).toLong())
        }
        section("Дочитано книг по месяцам")
        chart(s.monthsLabels, s.monthsFinished.map { it.toFloat() }, labelEvery = 2, highlightLast = true) {
            "${it.toInt()} кн."
        }

        // Когда читаю
        section("Когда я читаю")
        kv("По времени суток", s.chronotype())
        chart((0..23).map { "$it" }, s.hourMinutes.map { it.toFloat() }, labelEvery = 3) {
            formatMinutes(it.toLong())
        }
        smallText("Часы суток, сколько минут всего прочитано в этот час").also { c.addView(it) }
        val wd = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
        chart(wd, s.weekdayMinutes.map { it.toFloat() }) { formatMinutes(it.toLong()) }
        smallText("Дни недели, минуты за всё время").also { c.addView(it) }

        // Книги
        section("Книги")
        kv("Дочитано всего", "${s.finishedTotal}")
        kv("Читаю сейчас (без статуса)", "${s.readingTotal}")
        kv("Отложено", "${s.pausedTotal}")
        kv("Брошено", "${s.abandonedTotal}")
        kv("Всего за чтением", formatHours(s.totalMinutes))

        section("Сколько уходит на книгу")
        note("Медиана — «серединное» значение: одна очень толстая книга её не сдвигает. Считается по дочитанным.")
        kv("Время, медиана (всё время)", if (s.medianMinutesAll > 0) formatHours(s.medianMinutesAll) else "—")
        kv("Время, среднее (всё время)", if (s.meanMinutesAll > 0) formatHours(s.meanMinutesAll) else "—")
        kv("Время, медиана (этот месяц)", s.medianMinutesMonth?.let { formatHours(it) } ?: "—")
        kv("Время, среднее (этот месяц)", s.meanMinutesMonth?.let { formatHours(it) } ?: "—")
        kv("Дней на книгу, медиана (всё время)", s.medianDaysAll?.let { fmtDays(it) } ?: "—")
        kv("Дней на книгу, среднее (всё время)", s.meanDaysAll?.let { fmtDays(it) } ?: "—")
        kv("Дней на книгу, медиана (этот месяц)", s.medianDaysMonth?.let { fmtDays(it) } ?: "—")
        kv("Дней на книгу, среднее (этот месяц)", s.meanDaysMonth?.let { fmtDays(it) } ?: "—")

        section("Рекорды")
        s.fastestByDays?.let { (bk, d) -> bookLine("Быстрее всего", bk, "$d ${daysWord(d)}") }
        s.slowestByDays?.let { (bk, d) -> bookLine("Дольше всего", bk, "$d ${daysWord(d)}") }
        s.mostTime?.let { bookLine("Больше всего времени", it, formatHours(it.totalMinutes)) }
        s.leastTime?.let { bookLine("Меньше всего времени", it, formatHours(it.totalMinutes)) }
        if (s.fastestByDays == null) note("Появятся после первой дочитанной книги.")

        section("Год")
        kv("Дочитано в этом году", "${s.yearFinished}" + compareInt(s.yearFinished, s.prevYearFinished, "к прошлому году"))
        kv("Время в этом году", formatHours(s.yearMinutes) + compare(s.yearMinutes, s.prevYearMinutes, this::formatHours, "к прошлому году"))
        kv("Разных авторов в этом году", "${s.authorsThisYear}")

        section("Авторы")
        if (s.topAuthorsByBooks.isEmpty()) note("Пока нет книг с указанным автором.")
        s.topAuthorsByBooks.forEachIndexed { i, (a, n) -> kv("${i + 1}. $a", "$n кн.") }
        if (s.topAuthorsByTime.isNotEmpty()) {
            smallText("По времени чтения:").also { it.setPadding(0, (8 * dp).toInt(), 0, 0); c.addView(it) }
            s.topAuthorsByTime.forEachIndexed { i, (a, m) -> kv("${i + 1}. $a", formatHours(m)) }
        }

        section("Оценки")
        if (s.avgRating == null) note("Оценки ставятся в карточке книги, в любой момент.")
        else {
            kv("Средняя оценка", String.format(Locale("ru"), "%.1f из 10", s.avgRating) + " (${s.ratedCount} кн.)")
            s.topRated.forEach { bookLine("★ ${it.rating}", it, "") }
        }

        section("Приложения")
        s.apps.forEach { (name, n, m) -> kv(name, "$n кн., ${formatHours(m)}") }
    }

    // ── Элементы ──────────────────────────────────────────────────────────────

    private fun header(text: String, size: Float) {
        b.container.addView(TextView(requireContext()).apply {
            this.text = text; textSize = size; setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, (12 * dp).toInt())
        })
    }

    private fun section(text: String) {
        b.container.addView(TextView(requireContext()).apply {
            this.text = text; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
            setPadding(0, (24 * dp).toInt(), 0, (8 * dp).toInt())
        })
    }

    private fun smallText(text: String) = TextView(requireContext()).apply {
        this.text = text; textSize = 12f
        setTextColor(themeColor(android.R.attr.textColorSecondary))
        setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
    }

    private fun note(text: String) { b.container.addView(smallText(text)) }

    private fun kv(label: String, value: String) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
        }
        row.addView(TextView(requireContext()).apply {
            text = label; textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = value; textSize = 14f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.END
            setPadding((8 * dp).toInt(), 0, 0, 0)
        })
        b.container.addView(row)
    }

    private fun bookLine(label: String, book: Book, value: String) {
        val tv = TextView(requireContext()).apply {
            text = "$label: ${Book.displayName(book)}" + if (value.isNotEmpty()) " — $value" else ""
            textSize = 14f
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            setOnClickListener { BookActivity.open(requireContext(), book.id) }
        }
        b.container.addView(tv)
    }

    private fun tiles(vararg items: Pair<String, String>) {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEachIndexed { i, (label, value) ->
            val card = MaterialCardView(requireContext()).apply {
                radius = 10 * dp
                cardElevation = 1 * dp
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) marginStart = (8 * dp).toInt()
                }
            }
            val inner = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            }
            inner.addView(TextView(requireContext()).apply {
                text = value; textSize = 20f; setTypeface(typeface, Typeface.BOLD)
            })
            inner.addView(TextView(requireContext()).apply {
                text = label; textSize = 12f; setTextColor(themeColor(android.R.attr.textColorSecondary))
            })
            card.addView(inner)
            row.addView(card)
        }
        b.container.addView(row)
    }

    private fun chart(
        labels: List<String>, values: List<Float>, labelEvery: Int = 1,
        highlightLast: Boolean = false, format: (Float) -> String
    ) {
        val v = BarChartView(requireContext())
        v.setData(labels, values, labelEvery, highlightLast, format)
        b.container.addView(v)
    }

    private fun heatLegend() {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, 0)
        }
        val names = listOf("не читалось", "<15 мин", "<30", "<60", "час+")
        (0..4).forEach { i ->
            row.addView(View(requireContext()).apply {
                setBackgroundColor(com.readingtracker.ui.home.Ribbons.heat(requireContext(), i))
                layoutParams = LinearLayout.LayoutParams((12 * dp).toInt(), (12 * dp).toInt()).apply {
                    marginStart = if (i == 0) 0 else (8 * dp).toInt(); marginEnd = (3 * dp).toInt()
                }
            })
            row.addView(TextView(requireContext()).apply {
                text = names[i]; textSize = 11f
                setTextColor(themeColor(android.R.attr.textColorSecondary))
            })
        }
        b.container.addView(row)
    }

    // ── Форматирование ────────────────────────────────────────────────────────

    private fun themeColor(attr: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(requireContext(), tv.resourceId) else tv.data
    }

    /** Без дробей: «1 ч 19 мин». */
    private fun formatHours(minutes: Long): String = formatMinutes(minutes)

    private fun fmtDays(d: Double): String =
        if (d == d.toInt().toDouble()) "${d.toInt()}" else String.format(Locale("ru"), "%.1f", d)

    private fun daysWord(n: Int): String {
        val n100 = abs(n) % 100; val n10 = n100 % 10
        return when {
            n100 in 11..14 -> "дней"
            n10 == 1 -> "день"
            n10 in 2..4 -> "дня"
            else -> "дней"
        }
    }

    private fun compare(cur: Long, prev: Long, f: (Long) -> String, what: String = "к прошлому месяцу"): String {
        if (prev == 0L && cur == 0L) return ""
        val diff = cur - prev
        return when {
            diff > 0 -> "  (+${f(diff)} $what)"
            diff < 0 -> "  (−${f(-diff)} $what)"
            else -> "  (как в прошлый раз)"
        }
    }

    private fun compareInt(cur: Int, prev: Int, what: String = "к прошлому месяцу"): String {
        if (prev == 0 && cur == 0) return ""
        val diff = cur - prev
        return when {
            diff > 0 -> "  (+$diff $what)"
            diff < 0 -> "  (−${-diff} $what)"
            else -> "  (столько же)"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
