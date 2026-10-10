package com.readingtracker.ui.stats

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.readingtracker.databinding.FragmentStatsBinding

class StatsFragment : Fragment() {

    private var _b: FragmentStatsBinding? = null
    private val b get() = _b!!
    private val vm: StatsViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentStatsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.totalFinished.observe(viewLifecycleOwner) {
            b.tvTotalBooks.text = "Книг прочитано всего: ${it ?: 0}"
        }

        vm.totalMinutes.observe(viewLifecycleOwner) { mins ->
            val m = mins ?: 0
            b.tvTotalTime.text = "Всего за чтением: ${formatTime(m)}"
        }

        vm.monthStats.observe(viewLifecycleOwner) { s ->
            b.tvMonthDays.text = "Дней чтения в этом месяце: ${s.readingDays}"
            b.tvMonthTime.text = "Время в этом месяце: ${formatTime(s.totalMinutes)}"
            b.tvMonthBooks.text = "Книг закончено в этом месяце: ${s.finishedBooks}"
        }

        vm.streak.observe(viewLifecycleOwner) { s ->
            b.tvStreakCurrent.text = "Текущий стрик: ${s.current} дн. подряд"
            b.tvStreakBest.text = "Лучший стрик: ${s.best} дн."
        }

        vm.avgStats.observe(viewLifecycleOwner) { a ->
            b.tvAvgMean.text = "Среднее время на книгу: ${formatTime(a.allTimeMeanMins)}"
            b.tvAvgMedian.text = "Медиана времени на книгу: ${formatTime(a.allTimeMedianMins)}"
            if (a.thisMonthBooks > 0) {
                b.tvMonthMedian.text = "Медиана этого месяца: ${formatTime(a.thisMonthMedianMins)}"
                b.tvMonthMedian.visibility = View.VISIBLE
            } else {
                b.tvMonthMedian.visibility = View.GONE
            }
        }

        vm.topAuthors.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                b.tvTopAuthors.text = "Авторов пока нет"
            } else {
                val text = list.take(5).mapIndexed { i, a ->
                    "${i + 1}. ${a.author} — ${a.bookCount} кн."
                }.joinToString("\n")
                b.tvTopAuthors.text = "Топ авторов:\n$text"
            }
        }

        vm.statsByApp.observe(viewLifecycleOwner) { list ->
            if (list.isNotEmpty()) {
                val text = list.joinToString("\n") { a ->
                    "${a.appName}: ${a.bookCount} кн., ${formatTime(a.totalMins)}"
                }
                b.tvStatsByApp.text = "По приложениям:\n$text"
            }
        }

        b.btnRefresh.setOnClickListener { vm.refresh() }
    }

    private fun formatTime(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 -> "${h} ч ${m} мин"
            m > 0 -> "${m} мин"
            else -> "—"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
