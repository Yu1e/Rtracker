package com.readingtracker.ui.stats

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.CompoundButtonCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.readingtracker.Prefs
import com.readingtracker.R
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.data.ReadingSession
import com.readingtracker.databinding.FragmentStatsBinding
import com.readingtracker.export.ExportJob
import com.readingtracker.export.Exporter
import com.readingtracker.stats.BarChartView
import com.readingtracker.stats.HeatmapView
import com.readingtracker.stats.Stats
import com.readingtracker.stats.StatsCalc
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme
import com.readingtracker.ui.book.BookActivity
import com.readingtracker.ui.book.formatMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Статистика: разделы — карточки с названиями. Шапка стоит на месте: экспорт, полоса разделов
 * (тыц прокручивает к разделу) и галочки статусов. Галочки влияют на всё, кроме «сводки»,
 * и при каждом открытии экрана снова стоят все.
 */
class StatsFragment : Fragment() {

    companion object {
        const val SEC_SUMMARY = "summary"
        const val SEC_CALENDAR = "calendar"
        const val SEC_DAYS = "days"
        const val SEC_MONTH = "month"
        const val SEC_MONTHS = "months"
        const val SEC_YEAR = "year"
        const val SEC_WHEN = "when"
        const val SEC_BOOKS = "books"
        const val SEC_ABANDONED = "abandoned"
        const val SEC_PER_BOOK = "perbook"
        const val SEC_RECORDS = "records"
        const val SEC_AUTHORS = "authors"
        const val SEC_GENRES = "genres"
        const val SEC_RATINGS = "ratings"
        const val SEC_APPS = "apps"
        const val SEC_REREADS = "rereads"

        /** Раздел, к которому прокрутить при открытии (тыц с главного). */
        var pendingSection: String? = null

        val STATUSES = listOf(
            Book.STATUS_READING to "читаю", Book.STATUS_FINISHED to "дочитаны",
            Book.STATUS_PAUSED to "отложены", Book.STATUS_ABANDONED to "брошены"
        )
    }

    private var _b: FragmentStatsBinding? = null
    private val b get() = _b!!
    private val dp get() = resources.displayMetrics.density

    /** Отмеченные статусы. */
    private val selected = STATUSES.map { it.first }.toMutableSet()
    /** Ушли в карточку книги со статистики — по возвращении галочки не сбрасываются. */
    private var keepFilters = false

    private var books: List<Book> = emptyList()
    private var sessions: List<ReadingSession> = emptyList()
    private var readings: List<com.readingtracker.data.Reading> = emptyList()
    /** Пятая галочка: карточка «перечитывания». По умолчанию снята. */
    private var showRereads = false

    /** Разделы на экране: ключ → карточка; и пункты полосы разделов. */
    private val sections = LinkedHashMap<String, View>()
    private val navItems = LinkedHashMap<String, TextView>()
    private var activeNav: String? = null

    /** Текущее содержимое карточки, куда добавляются строки. */
    private lateinit var cur: LinearLayout

    // Экспорт: папка выбирается системным окном, туда пишутся файлы
    private var pendingJob: ExportJob? = null
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val job = pendingJob; pendingJob = null
        if (uri != null && job != null) writeExport(uri, job)
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentStatsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        b.tvTitle.setTextColor(ink())
        b.btnExport.setTextColor(ink())
        b.btnExport.background = pill(fill = 0, stroke = col(R.color.rt_ink2))
        b.btnExport.setOnClickListener { openExport() }
        b.headerLine.setBackgroundColor(RtColors.withAlpha(col(R.color.rt_ink3), 0.35f))
        b.scroll.setOnScrollChangeListener { _, _, _, _, _ -> updateActiveNav() }
        buildFilters()
        Fonts.apply(b.header)
        if (RtTheme.isEink(ctx)) b.scroll.isSmoothScrollingEnabled = false
    }

    override fun onResume() {
        super.onResume()
        if (!keepFilters && (selected.size != STATUSES.size || showRereads)) {
            selected.clear(); selected += STATUSES.map { it.first }
            showRereads = false
            buildFilters()
        }
        keepFilters = false
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            books = db.bookDao().getAllBooksSync()
            sessions = db.sessionDao().getAllSessions()
            readings = db.readingDao().getAll()
            renderAll()
        }
    }

    private fun renderAll() {
        val all = books; val ses = sessions; val rds = readings
        // «читаю» включает и перечитывание
        val sel = selected.toSet().let { if (Book.STATUS_READING in it) it + Book.STATUS_REREADING else it }
        viewLifecycleOwner.lifecycleScope.launch {
            val (full, filtered) = withContext(Dispatchers.Default) {
                // Перечитанная книга считается по прочтениям — полноценно, каждое прочтение
                val expanded = com.readingtracker.data.Readings.expand(all, ses, rds)
                val f = StatsCalc.compute(expanded, ses)
                if (selected.size == STATUSES.size) f to f
                else {
                    val fb = expanded.filter { it.status in sel }
                    f to StatsCalc.compute(fb, com.readingtracker.data.Readings.sessionsWithStatus(all, ses, rds, sel))
                }
            }
            if (_b == null) return@launch
            val y = b.scroll.scrollY
            render(full, filtered)
            if (pendingSection != null) scrollToPending() else b.scroll.post { b.scroll.scrollTo(0, y); updateActiveNav() }
        }
    }

    /** Прокрутка к разделу, о котором попросили с главного экрана. */
    fun scrollToPending() {
        val key = pendingSection ?: return
        if (_b == null) return
        val v = sections[key] ?: return
        pendingSection = null
        b.scroll.post { b.scroll.scrollTo(0, (v.top - 4 * dp).toInt().coerceAtLeast(0)); updateActiveNav() }
    }

    // ── Шапка: галочки и полоса разделов ──────────────────────────────────────

    private fun buildFilters() {
        val ctx = requireContext()
        b.filters.removeAllViews()
        for ((st, label) in STATUSES) {
            b.filters.addView(CheckBox(ctx).apply {
                text = label
                textSize = 13f
                setTextColor(col(R.color.rt_ink2))
                CompoundButtonCompat.setButtonTintList(this, android.content.res.ColorStateList.valueOf(col(R.color.rt_ink2)))
                isChecked = st in selected
                setOnCheckedChangeListener { btn, on ->
                    if (on) selected += st else selected -= st
                    // Хотя бы один статус остаётся
                    if (selected.isEmpty()) { selected += st; btn.isChecked = true; return@setOnCheckedChangeListener }
                    renderAll()
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = (6 * dp).toInt() })
        }
        // Пятая: отдельная статистика перечитываний
        b.filters.addView(CheckBox(ctx).apply {
            text = "перечитывания"
            textSize = 13f
            setTextColor(col(R.color.rt_ink2))
            CompoundButtonCompat.setButtonTintList(this, android.content.res.ColorStateList.valueOf(col(R.color.rt_ink2)))
            isChecked = showRereads
            setOnCheckedChangeListener { _, on ->
                showRereads = on
                if (on) pendingSection = SEC_REREADS
                renderAll()
            }
        })
        Fonts.apply(b.filters)
    }

    private fun buildNav() {
        val ctx = requireContext()
        b.nav.removeAllViews(); navItems.clear()
        for ((key, _) in sections) {
            val tv = TextView(ctx).apply {
                text = NAV_TITLES[key] ?: key
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding((11 * dp).toInt(), (6 * dp).toInt(), (11 * dp).toInt(), (6 * dp).toInt())
                setOnClickListener {
                    val v = sections[key] ?: return@setOnClickListener
                    val y = (v.top - 4 * dp).toInt().coerceAtLeast(0)
                    if (RtTheme.motion(ctx)) b.scroll.smoothScrollTo(0, y) else b.scroll.scrollTo(0, y)
                    setActive(key)
                }
            }
            b.nav.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = (6 * dp).toInt() })
            navItems[key] = tv
        }
        Fonts.apply(b.nav)
        activeNav = null
        setActive(sections.keys.firstOrNull())
    }

    private fun updateActiveNav() {
        if (_b == null) return
        val y = b.scroll.scrollY + (24 * dp).toInt()
        var key = sections.keys.firstOrNull()
        for ((k, v) in sections) if (v.top <= y) key = k
        // Докрутили до низа — последний раздел
        val child = b.scroll.getChildAt(0)
        if (child != null && b.scroll.scrollY + b.scroll.height >= child.height - 4) key = sections.keys.lastOrNull()
        setActive(key)
    }

    private fun setActive(key: String?) {
        if (key == activeNav) return
        activeNav = key
        for ((k, tv) in navItems) {
            val on = k == key
            tv.setTextColor(if (on) col(R.color.rt_on_strong) else ink())
            tv.background = if (on) pill(col(R.color.rt_strong), col(R.color.rt_strong))
            else pill(col(R.color.rt_tile), RtColors.withAlpha(col(R.color.rt_tile_stroke), 0.8f))
            if (on) b.navScroll.post {
                val x = tv.left - (b.navScroll.width - tv.width) / 2
                b.navScroll.smoothScrollTo(x.coerceAtLeast(0), 0)
            }
        }
    }

    private fun filterNote(): String? = when {
        selected.size == STATUSES.size -> null
        else -> "только: " + STATUSES.filter { it.first in selected }.joinToString(", ") { it.second }
    }

    // ── Построение экрана ─────────────────────────────────────────────────────

    private fun render(full: Stats, s: Stats) {
        val ctx = requireContext()
        b.container.removeAllViews()
        sections.clear()
        val note = filterNote()
        val statsOnly = Prefs.statsOnly(ctx)

        // Сводка — всегда по всему чтению
        card(SEC_SUMMARY, "сводка", null)
        tiles(
            "сегодня" to (if (full.todayMinutes > 0) formatMinutes(full.todayMinutes) else "0 мин"),
            "дней подряд" to "${full.currentStreak}",
            "за ${MONTHS_NOM[full.today.monthValue - 1]}" to formatMinutes(full.monthMinutes)
        )
        if (full.currentStreak > 0 && !full.readToday) {
            small("Сегодня ещё не читалось — серия продолжится, если открыть книгу до полуночи.")
        }
        if (!statsOnly) {
            if (full.bestStreak > full.currentStreak && full.currentStreak > 0) {
                kv("рекорд дней подряд", "${full.bestStreak}")
                kv("до рекорда", "${full.bestStreak - full.currentStreak}")
            } else if (full.currentStreak > 0 && full.currentStreak == full.bestStreak) {
                small("Это рекорд дней подряд.")
            }
        }

        // Календарь
        card(SEC_CALENDAR, "календарь", note)
        // Месяц клетками по дням недели: в прошедших днях минуты, в будущих — числа; тыц — дата и время
        val heat = com.readingtracker.stats.MonthGridView(ctx)
        val dayInfo = smallView("")
        dayInfo.visibility = View.GONE
        heat.setData(s.dayMinutes, s.today)
        heat.onDaySelected = { day, m ->
            dayInfo.visibility = View.VISIBLE
            dayInfo.text = "${day.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru")))}: " +
                if (m > 0) formatMinutes(m) else "не читалось"
        }
        cur.addView(heat)
        heatLegend()
        cur.addView(dayInfo)

        // Последние 30 дней
        card(SEC_DAYS, "минуты по дням", note ?: "30 дней")
        val days = (29 downTo 0).map { s.today.minusDays(it.toLong()) }
        chart(days.map { it.dayOfMonth.toString() }, days.map { (s.dayMinutes[it] ?: 0).toFloat() },
            labelEvery = 5, highlightLast = true) { formatMinutes(it.toLong()) }

        // Месяц
        card(SEC_MONTH, "этот месяц", note)
        kv("время чтения", formatMinutes(s.monthMinutes))
        kv("дней с чтением", "${s.monthReadingDays}")
        kv("книг дочитано", "${s.monthFinished}")
        kv("книг начато", "${s.monthStarted}")
        kv("за эту неделю", formatMinutes(s.weekMinutes))

        card(SEC_MONTHS, "по месяцам", note ?: "12 месяцев")
        small("часы чтения")
        chart(s.monthsLabels, s.monthsMinutes.map { it / 60f }, labelEvery = 2, highlightLast = true) {
            formatMinutes((it * 60).toLong())
        }
        small("дочитано книг")
        chart(s.monthsLabels, s.monthsFinished.map { it.toFloat() }, labelEvery = 2, highlightLast = true) {
            "книг: ${it.toInt()}"
        }

        card(SEC_YEAR, "год", note)
        kv("дочитано в этом году", "${s.yearFinished}")
        kv("время в этом году", formatMinutes(s.yearMinutes))
        kv("разных авторов в этом году", "${s.authorsThisYear}")

        // Когда читаю
        card(SEC_WHEN, "когда читаю", note)
        kv("по времени суток", s.chronotype())
        chart((0..23).map { "$it" }, s.hourMinutes.map { it.toFloat() }, labelEvery = 3) {
            formatMinutes(it.toLong())
        }
        small("Часы суток: сколько минут всего прочитано в этот час")
        val wd = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
        chart(wd, s.weekdayMinutes.map { it.toFloat() }) { formatMinutes(it.toLong()) }
        small("Дни недели: минуты за всё время")

        // Книги
        card(SEC_BOOKS, "книги", note)
        if (Book.STATUS_FINISHED in selected) kv("дочитано всего", "${s.finishedTotal}")
        if (Book.STATUS_READING in selected) kv("читаю сейчас", "${s.readingTotal}")
        if (Book.STATUS_PAUSED in selected) kv("отложено", "${s.pausedTotal}")
        if (Book.STATUS_ABANDONED in selected) kv("брошено", "${s.abandonedTotal}")
        kv("всего за чтением", formatMinutes(s.totalMinutes))

        // Брошенные — отдельная карточка
        if (Book.STATUS_ABANDONED in selected) {
            val a = s.abandoned
            card(SEC_ABANDONED, "брошенные", null)
            if (a.count == 0) small("Брошенных книг пока нет.")
            else {
                kv("брошено всего", "${a.count}")
                kv("в этом году", "${a.yearCount}")
                kv("время на них", formatMinutes(a.minutes))
                a.medianDay?.let { kv("бросается, медиана", "на ${fmtDays(it)}-й день") }
                a.medianMinutes?.let { kv("время до броска, медиана", formatMinutes(it)) }
                a.medianPercent?.let { kv("прочитано до броска, медиана", "$it %") }
                if (a.medianPercent == null) small("Процент прочитанного появится, когда у брошенных книг будут известны страницы (пока это ЛитРес).")
                a.topApp?.let { kv("чаще бросается в", it) }
                if (a.recent.isNotEmpty()) {
                    small("последние:")
                    a.recent.forEach { bookLine("", it, "") }
                }
            }
        }

        if (showRereads) rereadsCard()

        card(SEC_PER_BOOK, "на одну книгу", note)
        small("Медиана — «серединное» значение: одна очень толстая книга её не сдвигает. Считается по дочитанным.")
        kv("время, медиана (всё время)", if (s.medianMinutesAll > 0) formatMinutes(s.medianMinutesAll) else "—")
        kv("время, среднее (всё время)", if (s.meanMinutesAll > 0) formatMinutes(s.meanMinutesAll) else "—")
        kv("время, медиана (этот месяц)", s.medianMinutesMonth?.let { formatMinutes(it) } ?: "—")
        kv("время, среднее (этот месяц)", s.meanMinutesMonth?.let { formatMinutes(it) } ?: "—")
        kv("дней на книгу, медиана (всё время)", s.medianDaysAll?.let { fmtDays(it) } ?: "—")
        kv("дней на книгу, среднее (всё время)", s.meanDaysAll?.let { fmtDays(it) } ?: "—")
        kv("дней на книгу, медиана (этот месяц)", s.medianDaysMonth?.let { fmtDays(it) } ?: "—")
        kv("дней на книгу, среднее (этот месяц)", s.meanDaysMonth?.let { fmtDays(it) } ?: "—")

        if (!statsOnly) {
            card(SEC_RECORDS, "рекорды", note)
            s.fastestByDays?.let { (bk, d) -> bookLine("быстрее всего", bk, "дней: $d") }
            s.slowestByDays?.let { (bk, d) -> bookLine("дольше всего", bk, "дней: $d") }
            s.mostTime?.let { bookLine("больше всего времени", it, formatMinutes(it.totalMinutes)) }
            s.leastTime?.let { bookLine("меньше всего времени", it, formatMinutes(it.totalMinutes)) }
            if (s.fastestByDays == null) small("Появятся после первой дочитанной книги.")
        }

        card(SEC_AUTHORS, "авторы", note)
        if (s.topAuthorsByBooks.isEmpty()) small("Пока нет книг с указанным автором.")
        s.topAuthorsByBooks.forEachIndexed { i, (a, n) -> kv("${i + 1}. $a", "книг: $n") }
        if (s.topAuthorsByTime.isNotEmpty()) {
            small("по времени чтения:")
            s.topAuthorsByTime.forEachIndexed { i, (a, m) -> kv("${i + 1}. $a", formatMinutes(m)) }
        }

        card(SEC_GENRES, "жанры", note)
        if (s.genres.isEmpty()) small("Жанры ставятся в карточке книги, у книги их может быть несколько.")
        s.genres.forEach { (g, n, m) -> kv(g, "книг: $n · ${formatMinutes(m)}") }
        if (s.genres.isNotEmpty() && s.noGenre > 0) kv("жанр не указан", "книг: ${s.noGenre}")

        card(SEC_RATINGS, "оценки", note)
        if (s.avgRating == null) small("Оценки ставятся в карточке книги, в любой момент.")
        else {
            kv("средняя оценка", String.format(Locale("ru"), "%.1f из 10", s.avgRating))
            kv("книг с оценкой", "${s.ratedCount}")
            s.topRated.forEach { bookLine("★ ${it.rating}", it, "") }
        }

        card(SEC_APPS, "читалки", note)
        s.apps.forEach { (name, n, m) -> kv(name.ifBlank { "—" }, "книг: $n · ${formatMinutes(m)}") }

        Fonts.apply(b.container)
        buildNav()
    }

    /** Перечитывания: сколько книг и раз, время, дочитано, какие чаще. */
    private fun rereadsCard() {
        card(SEC_REREADS, "перечитывания", null)
        val rr = readings.filter { it.reread }
        if (rr.isEmpty()) { small("Перечитываний пока нет. Плитка «Перечитываю» — в карточке книги."); return }
        val now = System.currentTimeMillis()
        val bookById = books.associateBy { it.id }
        var minutes = 0L
        for ((id, list) in readings.groupBy { it.bookId }) {
            val bk = bookById[id] ?: continue
            com.readingtracker.data.Readings.parts(bk, list, sessions, now).filter { it.reading.reread }.forEach { minutes += it.minutes }
        }
        val year = java.time.LocalDate.now().year
        fun yearOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).year
        kv("книг перечитано", "${rr.map { it.bookId }.distinct().size}")
        kv("перечитываний всего", "${rr.size}")
        kv("дочитано при перечитывании", "${rr.count { it.status == Book.STATUS_FINISHED }}")
        kv("в этом году", "${rr.count { yearOf(it.startDate) == year }}")
        kv("время на перечитывание", formatMinutes(minutes))
        val top = readings.groupBy { it.bookId }.mapValues { (_, l) -> l.size }
            .filter { (id, _) -> rr.any { it.bookId == id } }
            .entries.sortedByDescending { it.value }.take(5)
        if (top.isNotEmpty()) {
            small("чаще всего:")
            top.forEach { (id, n) -> bookById[id]?.let { bookLine("", it, "прочтений: $n") } }
        }
    }

    // ── Карточки и строки ─────────────────────────────────────────────────────

    /** Новая карточка-раздел: матовое стекло, ч/б плитка или рамка чернил — по оформлению. */
    private fun card(key: String, title: String, sub: String?) {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val bg = RtTheme.resId(ctx, R.attr.rtTile18)
            if (bg != 0) setBackgroundResource(bg)
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
        }
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(ctx).apply {
            text = title; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (sub != null) head.addView(TextView(ctx).apply {
            text = sub; textSize = 12f; setTextColor(col(R.color.rt_ink3)); gravity = Gravity.END
        })
        box.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (8 * dp).toInt() })
        b.container.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (10 * dp).toInt() })
        sections[key] = box
        cur = box
    }

    private fun smallView(text: String) = TextView(requireContext()).apply {
        this.text = text; textSize = 12f
        setTextColor(col(R.color.rt_ink2))
        setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
    }

    private fun small(text: String) { cur.addView(smallView(text)) }

    private fun kv(label: String, value: String) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
        }
        row.addView(TextView(requireContext()).apply {
            text = label; textSize = 14f; setTextColor(ink())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = value; textSize = 14f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.END
            setTextColor(ink())
            setPadding((8 * dp).toInt(), 0, 0, 0)
        })
        cur.addView(row)
    }

    private fun bookLine(label: String, book: Book, value: String) {
        val tv = TextView(requireContext()).apply {
            text = (if (label.isNotEmpty()) "$label: " else "") + Book.displayName(book) +
                if (value.isNotEmpty()) " — $value" else ""
            textSize = 14f
            setTextColor(ink())
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            setOnClickListener { keepFilters = true; BookActivity.open(requireContext(), book.id) }
        }
        cur.addView(tv)
    }

    private fun tiles(vararg items: Pair<String, String>) {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEachIndexed { i, (label, value) ->
            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 12 * dp
                    // Плитка чуть темнее/светлее карточки, своей прозрачности цвета не трогаем
                    setColor(if (RtTheme.isEink(ctx)) col(R.color.rt_tile)
                        else RtColors.withAlpha(col(R.color.rt_ink), if (RtColors.isNight(ctx)) 0.10f else 0.06f))
                    if (RtTheme.isEink(ctx)) setStroke((1.5f * dp).toInt(), ink())
                }
            }
            inner.addView(TextView(ctx).apply {
                text = label; textSize = 12f; setTextColor(col(R.color.rt_ink2))
            })
            inner.addView(TextView(ctx).apply {
                text = value; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink())
            })
            row.addView(inner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                if (i > 0) marginStart = (8 * dp).toInt()
            })
        }
        cur.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (8 * dp).toInt() })
    }

    private fun chart(
        labels: List<String>, values: List<Float>, labelEvery: Int = 1,
        highlightLast: Boolean = false, format: (Float) -> String
    ) {
        val v = BarChartView(requireContext())
        v.setData(labels, values, labelEvery, highlightLast, format)
        cur.addView(v)
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
                background = GradientDrawable().apply { cornerRadius = 3 * dp; setColor(RtTheme.heat(requireContext(), i)) }
                layoutParams = LinearLayout.LayoutParams((12 * dp).toInt(), (12 * dp).toInt()).apply {
                    marginStart = if (i == 0) 0 else (8 * dp).toInt(); marginEnd = (3 * dp).toInt()
                }
            })
            row.addView(TextView(requireContext()).apply {
                text = names[i]; textSize = 11f
                setTextColor(col(R.color.rt_ink2))
            })
        }
        cur.addView(row)
    }

    // ── Экспорт ───────────────────────────────────────────────────────────────

    private fun openExport() {
        ExportSheet(this, books, sessions) { job ->
            pendingJob = job
            try { pickFolder.launch(Prefs.exportFolder(requireContext())) }
            catch (e: Exception) { toast("Не открылось окно выбора папки: ${e.message}") }
        }.show()
    }

    private fun writeExport(folder: Uri, job: ExportJob) {
        val ctx = requireContext().applicationContext
        Prefs.setExportFolder(ctx, folder)
        viewLifecycleOwner.lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val files = Exporter.build(job, books, sessions, readings)
                    Exporter.write(ctx, folder, files)
                }
            }
            r.fold(
                { n -> toast(if (n == 1) "Файл сохранён" else "Сохранено файлов: $n") },
                { toast("Не сохранилось: ${it.message}") }
            )
        }
    }

    private fun toast(t: String) { Toast.makeText(requireContext(), t, Toast.LENGTH_LONG).show() }

    // ── Цвета и форматирование ────────────────────────────────────────────────

    private fun col(id: Int) = RtColors.c(requireContext(), id)
    private fun ink() = col(R.color.rt_ink)

    private fun pill(fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = 18 * dp
        setColor(fill)
        setStroke((1.2f * dp).toInt().coerceAtLeast(1), stroke)
    }

    private fun fmtDays(d: Double): String =
        if (d == d.toInt().toDouble()) "${d.toInt()}" else String.format(Locale("ru"), "%.1f", d)

    override fun onDestroyView() {
        super.onDestroyView()
        sections.clear(); navItems.clear()
        _b = null
    }
}

private val MONTHS_NOM = arrayOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
)

/** Короткие названия разделов для полосы в шапке. */
private val NAV_TITLES = mapOf(
    StatsFragment.SEC_SUMMARY to "сводка",
    StatsFragment.SEC_CALENDAR to "календарь",
    StatsFragment.SEC_DAYS to "по дням",
    StatsFragment.SEC_MONTH to "месяц",
    StatsFragment.SEC_MONTHS to "по месяцам",
    StatsFragment.SEC_YEAR to "год",
    StatsFragment.SEC_WHEN to "когда",
    StatsFragment.SEC_BOOKS to "книги",
    StatsFragment.SEC_ABANDONED to "брошенные",
    StatsFragment.SEC_REREADS to "перечитывания",
    StatsFragment.SEC_PER_BOOK to "на книгу",
    StatsFragment.SEC_RECORDS to "рекорды",
    StatsFragment.SEC_AUTHORS to "авторы",
    StatsFragment.SEC_GENRES to "жанры",
    StatsFragment.SEC_RATINGS to "оценки",
    StatsFragment.SEC_APPS to "читалки"
)
