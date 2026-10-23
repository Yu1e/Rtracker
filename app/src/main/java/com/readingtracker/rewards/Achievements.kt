package com.readingtracker.rewards

import android.content.Context
import com.readingtracker.Prefs
import com.readingtracker.data.Book
import com.readingtracker.data.ReadingSession
import com.readingtracker.stats.StatsCalc
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Вид праздничного эффекта. */
enum class Effect { RIBBONS, CONFETTI, BUBBLES, FIREWORK, LEAVES, SPARKLES }

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
    private fun current(c: Context, books: List<Book>, sessions: List<ReadingSession>): List<Achievement> {
        val st = StatsCalc.compute(books, sessions)
        val out = mutableListOf<Achievement>()
        val statsOnly = Prefs.statsOnly(c)

        // Дочитанные книги: каждая своя лента
        books.filter { it.status == Book.STATUS_FINISHED && it.endDate != null }.forEach { b ->
            out += Achievement("book_${b.id}", Effect.RIBBONS, "дочитано",
                b.title.ifBlank { "без названия" }, b.author, b.id)
        }
        val finished = books.count { it.status == Book.STATUS_FINISHED }
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
                out += Achievement("streak_${it}_$runStart", Effect.LEAVES, "дней подряд", "$it")
            }
            if (!statsOnly) {
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
        if (!statsOnly && Prefs.ringMode(c) == "goal") {
            val goal = Prefs.goalMinutes(c)
            if (st.todayMinutes >= goal) out += Achievement("goal_${st.today}", Effect.CONFETTI, "цель дня взята", "$goal мин")
        }
        return out
    }

    /**
     * Новые поводы, самые важные первыми, не больше двух за раз. Остальные новые отмечаются,
     * чтобы не копились. В первый запуск всё уже выполненное считается отпразднованным.
     */
    fun pending(c: Context, books: List<Book>, sessions: List<ReadingSession>): List<Achievement> {
        val p = prefs(c)
        val seen = p.getStringSet("seen", emptySet())!!.toMutableSet()
        val now = current(c, books, sessions)
        val fresh = now.filter { it.key !in seen }
        if (fresh.isEmpty()) return emptyList()
        val first = !p.getBoolean("init", false)
        seen += fresh.map { it.key }
        p.edit().putStringSet("seen", seen).putBoolean("init", true).apply()
        if (first || !Prefs.effects(c)) return emptyList()
        val order = listOf("book_", "record_", "books_", "streak_", "hours_", "days_", "goal_")
        // Из нескольких круглых чисел одного рода показывается самое большое
        val best = fresh.groupBy { it.key.substringBefore('_') }
            .flatMap { (kind, list) -> if (kind == "book") list else listOf(list.last()) }
        return best.sortedBy { a -> order.indexOfFirst { a.key.startsWith(it) } }.take(2)
    }
}
