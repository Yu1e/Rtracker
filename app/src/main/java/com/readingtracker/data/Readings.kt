package com.readingtracker.data

/**
 * Прочтения книги и их время. Время прочтения — сеансы от его начала до начала следующего
 * (у первого — все сеансы до второго и время, внесённое вручную), так что сумма прочтений
 * равна общему времени книги.
 */
object Readings {

    class Part(val reading: Reading, val minutes: Long)

    fun sessionMinutes(s: ReadingSession, now: Long): Long =
        ((s.endTime ?: now) - s.startTime).coerceAtLeast(0) / 60_000

    /** Прочтения книги со временем; пусто, если книга не перечитывалась. */
    fun parts(book: Book, readings: List<Reading>, sessions: List<ReadingSession>, now: Long = System.currentTimeMillis()): List<Part> {
        val rs = readings.filter { it.bookId == book.id }.sortedBy { it.number }
        if (rs.isEmpty()) return emptyList()
        val own = sessions.filter { it.bookId == book.id }
        return rs.mapIndexed { i, r ->
            val lo = if (i == 0) Long.MIN_VALUE else r.startDate
            val hi = rs.getOrNull(i + 1)?.startDate ?: Long.MAX_VALUE
            val m = own.filter { it.startTime >= lo && it.startTime < hi }.sumOf { sessionMinutes(it, now) } +
                if (i == 0) book.manualMinutes else 0L
            Part(r, m)
        }
    }

    /**
     * Книги по прочтениям для статистики: перечитанная книга считается столько раз, сколько её читали,
     * у каждого раза свои даты, статус и время. Книга без перечитываний — как есть.
     */
    fun expand(books: List<Book>, sessions: List<ReadingSession>, readings: List<Reading>, now: Long = System.currentTimeMillis()): List<Book> {
        if (readings.isEmpty()) return books
        val byBook = readings.groupBy { it.bookId }
        val ses = sessions.groupBy { it.bookId }
        return books.flatMap { b ->
            val rs = byBook[b.id] ?: return@flatMap listOf(b)
            parts(b, rs, ses[b.id] ?: emptyList(), now).mapIndexed { i, p ->
                b.copy(
                    startDate = p.reading.startDate, endDate = p.reading.endDate, status = p.reading.status,
                    totalMinutes = p.minutes, manualMinutes = if (i == 0) b.manualMinutes else 0L
                )
            }
        }
    }

    /** Сеансы тех прочтений (или книг без перечитываний), чей статус отмечен. */
    fun sessionsWithStatus(books: List<Book>, sessions: List<ReadingSession>, readings: List<Reading>, statuses: Set<String>): List<ReadingSession> {
        val bookById = books.associateBy { it.id }
        val byBook = readings.groupBy { it.bookId }.mapValues { (_, l) -> l.sortedBy { it.number } }
        return sessions.filter { s ->
            val rs = byBook[s.bookId]
            if (rs.isNullOrEmpty()) bookById[s.bookId]?.status in statuses
            else {
                val i = rs.indexOfLast { it.startDate <= s.startTime }.coerceAtLeast(0)
                rs[i].status in statuses
            }
        }
    }

    /** «Первое», «Второе»… */
    fun ordinal(n: Int): String = ORD.getOrNull(n - 1) ?: "$n-е"

    private val ORD = listOf("Первое", "Второе", "Третье", "Четвёртое", "Пятое",
        "Шестое", "Седьмое", "Восьмое", "Девятое", "Десятое")
}
