package com.readingtracker.ui.home

import com.readingtracker.data.Book
import com.readingtracker.data.ReadingSession
import com.readingtracker.stats.StatsCalc
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Всё, что показывает главный экран. */
data class HomeData(
    val today: LocalDate,
    val dayNumber: Int,
    val week: IntArray,
    val todayIndex: Int,
    val book: Book?,
    /** Читаемые книги для стопки: первая — сверху (читалась последней), не больше трёх. */
    val reading: List<Book>,
    val bookMinutes: Long,
    val bricks: List<BookCoverView.Brick>,
    val finishedThisMonth: List<Book>,
    val ghosts: Int,
    val todayMinutes: Long,
    val streak: Int,
    val record: Int
) {
    companion object {
        private val zone: ZoneId get() = ZoneId.systemDefault()
        private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

        fun compute(books: List<Book>, sessions: List<ReadingSession>, now: Long = System.currentTimeMillis()): HomeData {
            val st = StatsCalc.compute(books, sessions, now)
            val today = st.today

            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val week = IntArray(7) { (st.dayMinutes[monday.plusDays(it.toLong())] ?: 0L).toInt() }
            val todayIndex = today.dayOfWeek.value - 1

            // № дня: статистика ведётся непрерывно с первого дня
            val first = listOfNotNull(
                sessions.minOfOrNull { it.startTime }?.toDate(),
                books.minOfOrNull { it.startDate }?.toDate()
            ).minOrNull() ?: today
            val dayNumber = (ChronoUnit.DAYS.between(first, today) + 1).toInt().coerceAtLeast(1)

            // Книги «сейчас читаю»: сверху та, что читалась последней
            val lastRead = HashMap<Long, Long>()
            for (s in sessions) lastRead[s.bookId] = maxOf(lastRead[s.bookId] ?: 0L, s.endTime ?: s.startTime)
            val reading = books.filter { it.status == Book.STATUS_READING }
                .sortedByDescending { lastRead[it.id] ?: it.startDate }
                .take(3)
            val book = reading.firstOrNull()

            var bookMinutes = 0L
            var bricks = emptyList<BookCoverView.Brick>()
            if (book != null) {
                val own = sessions.filter { it.bookId == book.id }
                val fromSessions = own.sumOf { s -> ((s.endTime ?: now) - s.startTime).coerceAtLeast(0) / 60_000 }
                bookMinutes = if (book.totalMinutes > 0) book.totalMinutes else fromSessions
                val readDays = own.map { it.startTime.toDate() }.toSet()
                val start = (listOf(book.startDate.toDate()) + readDays).minOrNull() ?: today
                bricks = buildBricks(start, today, readDays)
            }

            val monthStart = today.withDayOfMonth(1)
            val finished = books.filter {
                it.status == Book.STATUS_FINISHED && it.endDate != null &&
                    !it.endDate.toDate().isBefore(monthStart) && !it.endDate.toDate().isAfter(today)
            }.sortedBy { it.endDate }

            return HomeData(
                today = today,
                dayNumber = dayNumber,
                week = week,
                todayIndex = todayIndex,
                book = book,
                reading = reading,
                bookMinutes = bookMinutes,
                bricks = bricks,
                finishedThisMonth = finished,
                ghosts = reading.size,
                todayMinutes = st.todayMinutes,
                streak = st.currentStreak,
                record = maxOf(st.bestStreak, st.currentStreak)
            )
        }

        /** Кирпичик — день с книгой; у долгих книг кирпичик становится неделей. Не больше 13. */
        fun buildBricks(start: LocalDate, today: LocalDate, readDays: Set<LocalDate>): List<BookCoverView.Brick> {
            val days = (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceAtLeast(1)
            val maxBricks = 13
            val out = mutableListOf<BookCoverView.Brick>()
            if (days <= maxBricks) {
                for (i in 0 until days) {
                    val d = start.plusDays(i.toLong())
                    val read = d in readDays
                    out += when {
                        d == today && read -> BookCoverView.Brick.TODAY_READ
                        d == today -> BookCoverView.Brick.TODAY_EMPTY
                        read -> BookCoverView.Brick.READ
                        else -> BookCoverView.Brick.EMPTY
                    }
                }
            } else {
                // Недели, считая от сегодняшнего дня назад
                val weeks = (days + 6) / 7
                for (w in (weeks - 1) downTo 0) {
                    val end = today.minusDays(w * 7L)
                    val read = (0 until 7).any { end.minusDays(it.toLong()) in readDays }
                    out += when {
                        w == 0 && read -> BookCoverView.Brick.TODAY_READ
                        w == 0 -> BookCoverView.Brick.TODAY_EMPTY
                        read -> BookCoverView.Brick.READ
                        else -> BookCoverView.Brick.EMPTY
                    }
                }
            }
            return out.takeLast(maxBricks)
        }
    }
}
