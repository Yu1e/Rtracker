package com.readingtracker.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.readingtracker.Constants
import com.readingtracker.Prefs
import com.readingtracker.data.AppDatabase
import com.readingtracker.databinding.FragmentHomeEinkBinding
import com.readingtracker.launcher.ReaderLauncher
import com.readingtracker.rewards.Achievements
import com.readingtracker.rewards.Celebrations
import com.readingtracker.service.ManualTimer
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.book.BookActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Главный экран для электронных чернил (6–12 дюймов): книга словами, «читать» и таймер,
 * сегодня, дни подряд, неделя, книги за месяц. Крупно, чёрным по белому, без движения.
 */
class HomeEinkFragment : Fragment() {

    private var _b: FragmentHomeEinkBinding? = null
    private val b get() = _b!!
    private var data: HomeData? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeEinkBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Fonts.apply(view)
        b.fit.designWidthDp = 360f
        b.fit.maxScale = 3f
        b.bookTile.setOnClickListener {
            val book = data?.book
            if (book != null) BookActivity.open(requireContext(), book.id) else BookActivity.create(requireContext())
        }
        b.btnTimer.setOnClickListener { toggleTimer() }
        // «сегодня за книгой» ведёт на статистику
        (b.ring.parent as? View)?.setOnClickListener { (activity as? com.readingtracker.MainActivity)?.goTo(2) }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onStart() {
        super.onStart()
        ManualTimer.addListener(timerChanged)
    }

    override fun onStop() {
        ManualTimer.removeListener(timerChanged)
        super.onStop()
    }

    private val timerChanged: () -> Unit = { if (_b != null) load() }

    private fun load() {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val (d, rewards) = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(ctx)
                val books = db.bookDao().getAllBooksSync()
                val sessions = db.sessionDao().getAllSessions()
                HomeData.compute(books, sessions) to Achievements.pending(ctx, books, sessions)
            }
            if (_b == null) return@launch
            data = d
            render(d)
            Celebrations.play(requireActivity(), rewards)
        }
    }

    private fun render(d: HomeData) {
        val ctx = requireContext()
        val statsOnly = Prefs.statsOnly(ctx)
        val book = d.book

        if (book != null) {
            b.tvTitle.text = book.title.ifBlank { "без названия" }
            b.tvAuthor.text = book.author
            b.tvAuthor.visibility = if (book.author.isBlank()) View.GONE else View.VISIBLE
            val more = if (d.reading.size > 1) "  ·  ещё читается: ${d.reading.size - 1}" else ""
            b.tvBookTime.text = "за книгой ${HomeFragment.formatHM(d.bookMinutes)}$more"
            b.tvBookTime.visibility = View.VISIBLE
        } else {
            b.tvTitle.text = "здесь появится книга, которую вы читаете"
            b.tvAuthor.visibility = View.GONE
            b.tvBookTime.visibility = View.GONE
        }

        // Читать
        val readers = Constants.detectTrackedApps(ctx).entries.distinctBy { it.value }
        val readPkg = book?.appPackage?.takeIf { pkg -> readers.any { it.key == pkg } } ?: readers.firstOrNull()?.key
        val readName = readers.firstOrNull { it.key == readPkg }?.value
            ?: book?.appName?.takeIf { it.isNotBlank() } ?: "читалка не найдена"
        b.btnRead.setReader(readName)
        b.btnRead.setOnClickListener {
            if (readPkg == null) {
                Toast.makeText(ctx, "Читалки не найдены", Toast.LENGTH_SHORT).show()
            } else {
                if (book != null && book.appPackage == readPkg) Prefs.setPendingBook(ctx, readPkg, book.id)
                ReaderLauncher.open(ctx, readPkg)
            }
        }
        b.btnTimer.text = if (ManualTimer.bookId == null) "▶ таймер" else {
            val min = ((System.currentTimeMillis() - ManualTimer.since) / 60_000).coerceAtLeast(0)
            "■ стоп\n$min мин"
        }

        // Сегодня
        val goal = Prefs.goalMinutes(ctx)
        val hourRing = Prefs.hourRing(ctx)
        val m = d.todayMinutes
        b.ring.set(m, goal, hourRing)
        b.tvRingCaption.text = when {
            m >= 60 && (statsOnly || hourRing) -> "полных часов: ${m / 60}"
            statsOnly || m == 0L -> ""
            hourRing -> "до часа: ${60 - m} мин"
            m >= goal -> "цель взята"
            else -> "цель: $goal мин"
        }

        // Дни подряд
        b.tvStreak.text = d.streak.toString()
        b.tvStreakCaption.text = when {
            statsOnly -> ""
            d.streak == 0 && d.record > 0 -> "рекорд: ${d.record}"
            d.streak == 0 -> "начать можно сегодня"
            d.streak >= d.record -> "новый рекорд"
            else -> "до рекорда: ${d.record - d.streak}"
        }

        b.weekBars.crownOn = !statsOnly
        b.weekBars.set(d.week, d.todayIndex, d.weekLabels)

        val month = MONTHS_NOM[d.today.monthValue - 1]
        b.tvMonth.text = "книг за $month: ${d.finishedThisMonth.size}" +
            if (d.pending > 0) "  ·  читается и отложено: ${d.pending}" else ""
    }

    private fun toggleTimer() {
        val ctx = requireContext()
        val book = data?.book
        when {
            ManualTimer.bookId != null -> ManualTimer.stop(ctx)
            book == null -> BookActivity.create(ctx)
            else -> {
                ManualTimer.start(ctx, book.id)
                Toast.makeText(ctx, ManualTimer.hint(), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private val MONTHS_NOM = arrayOf(
            "январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
        )
    }
}
