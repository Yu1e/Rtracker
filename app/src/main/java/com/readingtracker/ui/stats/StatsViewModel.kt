package com.readingtracker.ui.stats

import android.app.Application
import androidx.lifecycle.*
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.AuthorStat
import com.readingtracker.data.AppStat
import com.readingtracker.data.Book
import kotlinx.coroutines.launch
import java.util.*

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

    val totalFinished: LiveData<Int> = db.bookDao().getFinishedBooksCount()
    val totalMinutes: LiveData<Long?> = db.bookDao().getTotalReadingMinutes()
    val topAuthors: LiveData<List<AuthorStat>> = db.bookDao().getTopAuthors()
    val statsByApp: LiveData<List<AppStat>> = db.bookDao().getStatsByApp()

    private val _monthStats = MutableLiveData<MonthStats>()
    val monthStats: LiveData<MonthStats> = _monthStats

    private val _streak = MutableLiveData<StreakInfo>()
    val streak: LiveData<StreakInfo> = _streak

    private val _avgStats = MutableLiveData<AvgStats>()
    val avgStats: LiveData<AvgStats> = _avgStats

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loadMonthStats()
            loadStreak()
            loadAvgStats()
        }
    }

    // ── Month stats ────────────────────────────────────────────────────────────

    private suspend fun loadMonthStats() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis

        val dailyRows = db.sessionDao().getDailyMinutes(start, System.currentTimeMillis())
        val readingDays = dailyRows.count { it.minutes > 0 }
        val totalMins = dailyRows.sumOf { it.minutes }
        val booksThisMonth = db.bookDao().getFinishedBooksInMonth(start, end)

        _monthStats.postValue(MonthStats(readingDays, totalMins, booksThisMonth))
    }

    // ── Streak ─────────────────────────────────────────────────────────────────

    private suspend fun loadStreak() {
        val since = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        val rows = db.sessionDao().getDailyMinutes(since, System.currentTimeMillis())
        val readSet = rows.filter { it.minutes > 0 }.map { it.day }.toSet()

        fun dayKey(c: Calendar) = String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )

        // Текущий стрик: считаем назад от сегодня; если сегодня ещё не читали, начинаем со вчера
        val cal = Calendar.getInstance()
        if (dayKey(cal) !in readSet) cal.add(Calendar.DAY_OF_MONTH, -1)
        var current = 0
        while (dayKey(cal) in readSet) {
            current++
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }

        // Лучший стрик за последний год
        var best = 0
        var run = 0
        val walk = Calendar.getInstance()
        walk.add(Calendar.DAY_OF_MONTH, -365)
        repeat(366) {
            if (dayKey(walk) in readSet) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
            walk.add(Calendar.DAY_OF_MONTH, 1)
        }

        _streak.postValue(StreakInfo(current, best))
    }

    // ── Average / median ───────────────────────────────────────────────────────

    private suspend fun loadAvgStats() {
        val finished = db.bookDao().getFinishedBooksSortedByTime()
            .filter { it.totalMinutes > 0 }

        if (finished.isEmpty()) {
            _avgStats.postValue(AvgStats(0, 0, 0, 0))
            return
        }

        val allMins = finished.map { it.totalMinutes }.sorted()
        val allMean = allMins.average().toLong()
        val allMedian = median(allMins)

        // This month's finished books
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        val startMonth = cal.timeInMillis
        val monthMins = finished
            .filter { (it.endDate ?: 0) >= startMonth }
            .map { it.totalMinutes }.sorted()
        val monthMedian = if (monthMins.isEmpty()) 0L else median(monthMins)

        _avgStats.postValue(AvgStats(allMean, allMedian, monthMins.size.toLong(), monthMedian))
    }

    private fun median(sorted: List<Long>): Long {
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2]
        else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
    }
}

data class MonthStats(val readingDays: Int, val totalMinutes: Long, val finishedBooks: Int)
data class StreakInfo(val current: Int, val best: Int)
data class AvgStats(
    val allTimeMeanMins: Long,
    val allTimeMedianMins: Long,
    val thisMonthBooks: Long,
    val thisMonthMedianMins: Long
)
