package com.readingtracker.rewards

import android.content.Context
import com.readingtracker.Prefs
import com.readingtracker.data.Book
import com.readingtracker.data.Reading
import com.readingtracker.data.ReadingSession
import com.readingtracker.data.Readings
import com.readingtracker.stats.StatsCalc
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Вид праздничного эффекта. */
enum class Effect { RIBBONS, CONFETTI, BUBBLES, FIREWORK, STARS, SPARKLES }

/**
 * Повод для праздника. Подпись — над числом (правило текста): «книг дочитано» и под ней «25».
 * У дочитанной книги вместо числа название.
 */
data class Achievement(
    val key: String,
    val effect: Effect,
    val label: String,
    val big: String,
    val small: String = "",
    val bookId: Long? = null
)

/** Что уже отпраздновано, и что празднуется сейчас. */
object Achievements {

    private val BOOKS = intArrayOf(5, 10, 25, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000)
    private val HOURS = intArrayOf(10, 25, 50, 100, 150, 200, 300, 400, 500, 750, 1000, 1500, 2000)
    private val STREAK = intArrayOf(7, 14, 30, 50, 75, 100, 150, 200, 250, 300, 365, 500, 730, 1000)
    private val DAYS = intArrayOf(30, 50, 100, 150, 200, 300, 365, 500, 730, 1000, 1500, 2000)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("rewards", Context.MODE_PRIVATE)

    private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Все поводы, которые сейчас выполнены; отпразднованные отсеиваются в [pending]. */
    private fun current(c: Context, books: List<Book>, sessions: List<ReadingSession>, readings: List<Reading>): List<Achievement> {
        // Перечитанная книга считается по прочтениям
        val byReading = Readings.expand(books, sessions, readings)
        val st = StatsCalc.compute(byReading, sessions)
        val out = mutableListOf<Achievement>()

        // Дочитанные книги: каждая своя лента; каждое дочитанное перечитывание — тоже
        val rsByBook = readings.groupBy { it.bookId }
        for (b in books) {
            val rs = rsByBook[b.id]?.sortedBy { it.number }
            if (rs.isNullOrEmpty()) {
                if (b.status == Book.STATUS_FINISHED && b.endDate != null)
                    out += Achievement("book_${b.id}", Effect.RIBBONS, "дочитано", b.title.ifBlank { "без названия" }, b.author, b.id)
            } else for (r in rs) {
                if (r.status != Book.STATUS_FINISHED || r.endDate == null) continue
                if (r.number == 1 && !r.reread)
                    out += Achievement("book_${b.id}", Effect.RIBBONS, "дочитано", b.title.ifBlank { "без названия" }, b.author, b.id)
                else
                    out += Achievement("book_${b.id}_r${r.number}", Effect.RIBBONS, "перечитано", b.title.ifBlank { "без названия" }, b.author, b.id)
            }
        }
        val finishedList = byReading.filter { it.status == Book.STATUS_FINISHED && it.endDate != null }
        val finished = finishedList.size
        monthly(finishedList, st.today).let { out += it }
        BOOKS.filter { finished >= it }.forEach { out += Achievement("books_$it", Effect.FIREWORK, "книг дочитано", "$it") }

        val hours = (st.totalMinutes / 60).toInt()
        HOURS.filter { hours >= it }.forEach { out += Achievement("hours_$it", Effect.BUBBLES, "часов с книгой", "$it") }

        val readDays = st.dayMinutes.filterValues { it > 0 }.keys
        DAYS.filter { readDays.size >= it }.forEach { out += Achievement("days_$it", Effect.SPARKLES, "дней с книгой", "$it") }

        // Дни подряд: у каждой серии свои круглые числа
        val streak = st.currentStreak
        if (streak > 0) {
            val end = if (st.today in readDays) st.today else st.today.minusDays(1)
            val runStart = end.minusDays(streak - 1L)
            STREAK.filter { streak >= it }.forEach {
                out += Achievement("streak_${it}_$runStart", Effect.STARS, "дней подряд", "$it")
            }
            run {
                // Новый рекорд: серия обогнала все прежние (если прежние были не совсем короткие)
                var prevBest = 0; var run = 0; var prev: LocalDate? = null
                for (day in readDays.filter { it.isBefore(runStart) }.sorted()) {
                    run = if (prev != null && day == prev.plusDays(1)) run + 1 else 1
                    prevBest = maxOf(prevBest, run); prev = day
                }
                if (prevBest >= 3 && streak > prevBest) {
                    out += Achievement("record_$runStart", Effect.FIREWORK, "новый рекорд, дней подряд", "$streak")
                }
            }
        }

        // Цель дня
        if (!Prefs.statsOnly(c) && Prefs.ringMode(c) == "goal") {
            val goal = Prefs.goalMinutes(c)
            if (st.todayMinutes >= goal) out += Achievement("goal_${st.today}", Effect.CONFETTI, "цель дня взята", "$goal мин")
        }
        return out
    }

    private val MONTH_NOM = arrayOf("январь", "февраль", "март", "апрель", "май", "июнь",
        "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")

    /**
     * Книги за месяц. В текущем месяце — сразу, как только обогнали: прошлый месяц, среднее, рекорд.
     * В начале нового месяца — итог прошлого: «больше, чем в прошлом месяце» и рекорд ещё раз.
     * Из одновременных показывается самая крупная (ключи month_1… month_3 — по возрастанию).
     */
    private fun monthly(finished: List<Book>, today: LocalDate): List<Achievement> {
        val byMonth = finished.groupingBy { it.endDate!!.toDate().withDayOfMonth(1) }.eachCount()
        if (byMonth.isEmpty()) return emptyList()
        val first = byMonth.keys.minOrNull()!!
        fun count(m: LocalDate) = byMonth[m] ?: 0
        val out = mutableListOf<Achievement>()

        fun judge(m: LocalDate, final: Boolean) {
            val cur = count(m)
            if (cur <= 0) return
            val before = generateSequence(first) { it.plusMonths(1) }.takeWhile { it.isBefore(m) }.toList()
            if (before.isEmpty()) return
            val prev = count(m.minusMonths(1))
            val avg = before.sumOf { count(it) }.toDouble() / before.size
            val rec = before.maxOf { count(it) }
            val tag = "${m.year}-${m.monthValue}"
            val name = MONTH_NOM[m.monthValue - 1]
            val head = if (final) "итог месяца, $name: " else ""
            if (prev >= 1 && cur > prev)
                out += Achievement(if (final) "month_1_final_$tag" else "month_1_$tag", if (final) Effect.STARS else Effect.RIBBONS,
                    head + "книг больше, чем в прошлом месяце", "$cur", "прошлый месяц: $prev")
            if (!final && avg > 0 && cur > avg && before.size >= 2)
                out += Achievement("month_2_$tag", Effect.CONFETTI, "книг больше, чем в среднем за месяц", "$cur",
                    "в среднем: " + String.format(java.util.Locale("ru"), "%.1f", avg))
            if (rec >= 1 && cur > rec)
                out += Achievement(if (final) "month_3_final_$tag" else "month_3_$tag", Effect.FIREWORK,
                    head + "рекорд: больше всего книг за месяц", "$cur", "прежний рекорд: $rec")
        }
        val thisMonth = today.withDayOfMonth(1)
        judge(thisMonth, final = false)
        judge(thisMonth.minusMonths(1), final = true)
        return out
    }

    /**
     * Новые поводы, самые важные первыми, не больше двух за раз. Остальные новые отмечаются,
     * чтобы не копились. В первый запуск всё уже выполненное считается отпразднованным.
     */
    fun pending(c: Context, books: List<Book>, sessions: List<ReadingSession>, readings: List<Reading> = emptyList()): List<Achievement> {
        val p = prefs(c)
        val seen = p.getStringSet("seen", emptySet())!!.toMutableSet()
        val now = current(c, books, sessions, readings)
        val fresh = now.filter { it.key !in seen }
        if (fresh.isEmpty()) return emptyList()
        val first = !p.getBoolean("init", false)
        seen += fresh.map { it.key }
        p.edit().putStringSet("seen", seen).putBoolean("init", true).apply()
        if (first || !Prefs.effects(c)) return emptyList()
        val order = listOf("book_", "month_", "record_", "books_", "streak_", "hours_", "days_", "goal_")
        // Из нескольких круглых чисел одного рода показывается самое большое;
        // из наград за месяц — самая крупная (рекорд, потом «больше среднего», потом «больше прошлого месяца»)
        val best = fresh.groupBy { it.key.substringBefore('_') }
            .flatMap { (kind, list) -> if (kind == "book") list else listOf(list.last()) }
        return best.sortedBy { a -> order.indexOfFirst { a.key.startsWith(it) } }.take(4)
    }
}
