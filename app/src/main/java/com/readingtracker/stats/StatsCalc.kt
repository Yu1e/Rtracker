package com.readingtracker.stats

import com.readingtracker.data.Book
import com.readingtracker.data.BookActions
import com.readingtracker.data.ReadingSession
import java.time.*
import java.time.temporal.TemporalAdjusters

/** Все цифры статистики, посчитанные из книг и сессий. */
data class Stats(
    val today: LocalDate,
    val dayMinutes: Map<LocalDate, Long>,
    val todayMinutes: Long,
    val weekMinutes: Long,
    val monthMinutes: Long,
    val prevMonthMinutes: Long,
    val monthReadingDays: Int,
    val monthStarted: Int,
    val monthFinished: Int,
    val prevMonthFinished: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val readToday: Boolean,
    val hourMinutes: LongArray,          // 0..23
    val weekdayMinutes: LongArray,       // 0 = пн
    val monthsLabels: List<String>,      // последние 12 месяцев
    val monthsMinutes: List<Long>,
    val monthsFinished: List<Int>,
    val finishedTotal: Int,
    val abandonedTotal: Int,
    val pausedTotal: Int,
    val readingTotal: Int,
    val totalMinutes: Long,
    val meanMinutesAll: Long, val medianMinutesAll: Long,
    val meanMinutesMonth: Long?, val medianMinutesMonth: Long?,
    val meanDaysAll: Double?, val medianDaysAll: Double?,
    val meanDaysMonth: Double?, val medianDaysMonth: Double?,
    val fastestByDays: Pair<Book, Int>?, val slowestByDays: Pair<Book, Int>?,
    val mostTime: Book?, val leastTime: Book?,
    val topAuthorsByBooks: List<Pair<String, Int>>,
    val topAuthorsByTime: List<Pair<String, Long>>,
    val authorsThisYear: Int,
    val apps: List<Triple<String, Int, Long>>,   // название, книг, минут
    val avgRating: Double?, val ratedCount: Int,
    val topRated: List<Book>,
    val yearFinished: Int, val prevYearFinished: Int,
    val yearMinutes: Long, val prevYearMinutes: Long,
    val currentBook: Book?,
    val booksById: Map<Long, Book>
) {
    fun chronotype(): String {
        val morning = (5..11).sumOf { hourMinutes[it] }
        val day = (12..16).sumOf { hourMinutes[it] }
        val evening = (17..22).sumOf { hourMinutes[it] }
        val night = listOf(23, 0, 1, 2, 3, 4).sumOf { hourMinutes[it] }
        val max = maxOf(morning, day, evening, night)
        if (max == 0L) return "—"
        return when (max) {
            morning -> "утренний читатель"
            day -> "дневной читатель"
            evening -> "вечерний читатель"
            else -> "ночной читатель"
        }
    }
}

object StatsCalc {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
    private fun LocalDate.startMs(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }

    private fun medianD(values: List<Int>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2].toDouble() else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    fun compute(books: List<Book>, sessions: List<ReadingSession>, now: Long = System.currentTimeMillis()): Stats {
        val today = now.toDate()
        val booksById = books.associateBy { it.id }

        // ── Минуты по дням и часам (сессии делятся по границам часов) ────────
        val acc = HashMap<LocalDate, Double>()
        val accHour = DoubleArray(24)
        val accWeekday = DoubleArray(7)
        for (s in sessions) {
            val end = s.endTime ?: now
            if (end <= s.startTime) continue
            var t = s.startTime
            while (t < end) {
                val zt = Instant.ofEpochMilli(t).atZone(zone)
                val nextHour = zt.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                val segEnd = minOf(end, nextHour)
                val mins = (segEnd - t) / 60_000.0
                val d = zt.toLocalDate()
                acc[d] = (acc[d] ?: 0.0) + mins
                accHour[zt.hour] += mins
                accWeekday[zt.dayOfWeek.value - 1] += mins
                t = segEnd
            }
        }
        val dayMin = HashMap<LocalDate, Long>()
        for ((d, m) in acc) dayMin[d] = Math.round(m)
        val hourMin = LongArray(24) { Math.round(accHour[it]) }
        val weekdayMin = LongArray(7) { Math.round(accWeekday[it]) }

        // Время, внесённое вручную, раскладывается поровну на дни книги (с начала до конца).
        // Оно входит в суммы за неделю, месяц и год, но не в дни: теплокарту, стрик, «сегодня».
        val periodAcc = HashMap<LocalDate, Double>()
        for ((d0, m) in acc) periodAcc[d0] = m
        for (b in books) {
            if (b.manualMinutes <= 0) continue
            val from = b.startDate.toDate()
            var to = (b.endDate ?: b.startDate).toDate()
            if (to.isBefore(from)) to = from
            val days = (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1).coerceIn(1, 3660)
            val per = b.manualMinutes.toDouble() / days
            for (i in 0 until days) {
                val dd = from.plusDays(i)
                periodAcc[dd] = (periodAcc[dd] ?: 0.0) + per
            }
        }

        fun minutesBetween(from: LocalDate, toExclusive: LocalDate): Long =
            Math.round(periodAcc.filterKeys { !it.isBefore(from) && it.isBefore(toExclusive) }.values.sum())

        val monthStart = today.withDayOfMonth(1)
        val nextMonth = monthStart.plusMonths(1)
        val prevMonthStart = monthStart.minusMonths(1)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val yearStart = today.withDayOfYear(1)
        val prevYearStart = yearStart.minusYears(1)

        // ── Стрики ────────────────────────────────────────────────────────────
        val readDays = dayMin.filterValues { it > 0 }.keys
        var current = 0
        var d = if (today in readDays) today else today.minusDays(1)
        while (d in readDays) { current++; d = d.minusDays(1) }
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (day in readDays.sorted()) {
            run = if (prev != null && day == prev.plusDays(1)) run + 1 else 1
            if (run > best) best = run
            prev = day
        }

        // ── Книги ─────────────────────────────────────────────────────────────
        fun inRange(ms: Long?, from: LocalDate, to: LocalDate) =
            ms != null && !ms.toDate().isBefore(from) && ms.toDate().isBefore(to)

        val finished = books.filter { it.status == Book.STATUS_FINISHED }
        val finishedMonth = finished.filter { inRange(it.endDate, monthStart, nextMonth) }
        val finishedPrevMonth = finished.filter { inRange(it.endDate, prevMonthStart, monthStart) }

        val months = (11 downTo 0).map { monthStart.minusMonths(it.toLong()) }
        val monthNames = arrayOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
        val monthsLabels = months.map { monthNames[it.monthValue - 1] }
        val monthsMinutes = months.map { minutesBetween(it, it.plusMonths(1)) }
        val monthsFinished = months.map { m -> finished.count { inRange(it.endDate, m, m.plusMonths(1)) } }

        // Время и дни на дочитанные книги
        val timed = finished.filter { it.totalMinutes > 0 }
        val timedMonth = finishedMonth.filter { it.totalMinutes > 0 }
        val daysAll = finished.map { BookActions.daysOnBook(it) }
        val daysMonth = finishedMonth.map { BookActions.daysOnBook(it) }

        val fastest = finished.minByOrNull { BookActions.daysOnBook(it) }?.let { it to BookActions.daysOnBook(it) }
        val slowest = finished.maxByOrNull { BookActions.daysOnBook(it) }?.let { it to BookActions.daysOnBook(it) }

        // ── Авторы ────────────────────────────────────────────────────────────
        val withAuthor = books.filter { it.author.isNotBlank() }
        val topByBooks = withAuthor.groupingBy { it.author }.eachCount()
            .entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
        val topByTime = withAuthor.groupBy { it.author }
            .mapValues { (_, l) -> l.sumOf { it.totalMinutes } }
            .filterValues { it > 0 }
            .entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
        val authorsYear = withAuthor.filter { b ->
            inRange(b.startDate, yearStart, yearStart.plusYears(1)) ||
                inRange(b.endDate, yearStart, yearStart.plusYears(1)) ||
                sessions.any { it.bookId == b.id && inRange(it.startTime, yearStart, yearStart.plusYears(1)) }
        }.map { it.author }.toSet().size

        // ── Приложения ────────────────────────────────────────────────────────
        val apps = books.groupBy { it.appName }.map { (name, l) ->
            Triple(name, l.size, l.sumOf { it.totalMinutes })
        }.sortedByDescending { it.third }

        // ── Оценки ────────────────────────────────────────────────────────────
        val rated = books.filter { it.rating != null }
        val topRated = rated.sortedByDescending { it.rating }.take(3)

        // ── Текущая книга ─────────────────────────────────────────────────────
        val lastSession = sessions.maxByOrNull { it.startTime }
        val currentBook = lastSession?.let { booksById[it.bookId] }
            ?: books.filter { it.status == Book.STATUS_READING }.maxByOrNull { it.startDate }

        return Stats(
            today = today,
            dayMinutes = dayMin,
            todayMinutes = dayMin[today] ?: 0,
            weekMinutes = minutesBetween(weekStart, today.plusDays(1)),
            monthMinutes = minutesBetween(monthStart, nextMonth),
            prevMonthMinutes = minutesBetween(prevMonthStart, monthStart),
            monthReadingDays = readDays.count { !it.isBefore(monthStart) && it.isBefore(nextMonth) },
            monthStarted = books.count { inRange(it.startDate, monthStart, nextMonth) },
            monthFinished = finishedMonth.size,
            prevMonthFinished = finishedPrevMonth.size,
            currentStreak = current,
            bestStreak = best,
            readToday = today in readDays,
            hourMinutes = hourMin,
            weekdayMinutes = weekdayMin,
            monthsLabels = monthsLabels,
            monthsMinutes = monthsMinutes,
            monthsFinished = monthsFinished,
            finishedTotal = finished.size,
            abandonedTotal = books.count { it.status == Book.STATUS_ABANDONED },
            pausedTotal = books.count { it.status == Book.STATUS_PAUSED },
            readingTotal = books.count { it.status == Book.STATUS_READING },
            totalMinutes = books.sumOf { it.totalMinutes },
            meanMinutesAll = if (timed.isEmpty()) 0 else timed.sumOf { it.totalMinutes } / timed.size,
            medianMinutesAll = median(timed.map { it.totalMinutes }) ?: 0,
            meanMinutesMonth = if (timedMonth.isEmpty()) null else timedMonth.sumOf { it.totalMinutes } / timedMonth.size,
            medianMinutesMonth = median(timedMonth.map { it.totalMinutes }),
            meanDaysAll = if (daysAll.isEmpty()) null else daysAll.average(),
            medianDaysAll = medianD(daysAll),
            meanDaysMonth = if (daysMonth.isEmpty()) null else daysMonth.average(),
            medianDaysMonth = medianD(daysMonth),
            fastestByDays = fastest,
            slowestByDays = slowest,
            mostTime = timed.maxByOrNull { it.totalMinutes },
            leastTime = timed.minByOrNull { it.totalMinutes },
            topAuthorsByBooks = topByBooks,
            topAuthorsByTime = topByTime,
            authorsThisYear = authorsYear,
            apps = apps,
            avgRating = if (rated.isEmpty()) null else rated.mapNotNull { it.rating }.average(),
            ratedCount = rated.size,
            topRated = topRated,
            yearFinished = finished.count { inRange(it.endDate, yearStart, yearStart.plusYears(1)) },
            prevYearFinished = finished.count { inRange(it.endDate, prevYearStart, yearStart) },
            yearMinutes = minutesBetween(yearStart, yearStart.plusYears(1)),
            prevYearMinutes = minutesBetween(prevYearStart, yearStart),
            currentBook = currentBook,
            booksById = booksById
        )
    }
}
