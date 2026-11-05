package com.readingtracker.ui.home

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.readingtracker.Constants
import com.readingtracker.Prefs
import com.readingtracker.R
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.databinding.FragmentHomeBinding
import com.readingtracker.launcher.ReaderLauncher
import com.readingtracker.rewards.Achievements
import com.readingtracker.rewards.Celebrations
import com.readingtracker.ui.RtTheme
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import com.readingtracker.service.ManualTimer
import com.readingtracker.ui.book.BookActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Главный экран: неделя, книга с манжеткой и ляссе, полка, «читать», читалки, цель дня, дни подряд. */
class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private var data: HomeData? = null
    private var launching = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Fonts.apply(view)
        // Стопка, изгибы ляссе и призраки на полке — по-новому при каждом открытии
        b.bookCover.reshuffle()
        b.shelf.reshuffle()
        b.streakGlass.reshuffle()
        // Книга со своими ляссе — над плитками: хвост, вышедший за книгу, лежит поверх стекла, а не под ним
        (b.bookCover.parent as View).translationZ = 1f
        b.bookCover.translationZ = 1f
        // «сегодня за книгой» ведёт на статистику
        b.ringTile.setOnClickListener { (activity as? com.readingtracker.MainActivity)?.goToStats(com.readingtracker.ui.stats.StatsFragment.SEC_SUMMARY) }
        // Столбики недели — в раздел «по дням»
        b.weekBars.setOnClickListener { (activity as? com.readingtracker.MainActivity)?.goToStats(com.readingtracker.ui.stats.StatsFragment.SEC_DAYS) }

        b.bookCover.setOnClickListener {
            val book = data?.book
            if (book != null) BookActivity.open(requireContext(), book.id) else BookActivity.create(requireContext())
        }
    }

    // Таймер включили или выключили (и кнопкой в меню) — обновить подпись под «читать»
    private val timerChanged: () -> Unit = { if (_b != null && isResumed) load() }

    override fun onResume() {
        super.onResume()
        ManualTimer.addListener(timerChanged)
        load()
    }

    override fun onPause() {
        ManualTimer.removeListener(timerChanged)
        super.onPause()
    }

    private fun load() {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val (d, rewards) = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(ctx)
                val books = db.bookDao().getAllBooksSync()
                val sessions = db.sessionDao().getAllSessions()
                val readings = db.readingDao().getAll()
                HomeData.compute(books, sessions, readings) to Achievements.pending(ctx, books, sessions, readings)
            }
            if (_b == null) return@launch
            data = d
            render(d)
            Celebrations.play(requireActivity(), rewards)
        }
    }

    private fun col(id: Int) = RtColors.c(requireContext(), id)

    private fun render(d: HomeData) {
        val ctx = requireContext()

        val statsOnly = Prefs.statsOnly(ctx)

        // Неделя
        b.weekBars.crownOn = !statsOnly
        b.weekBars.set(d.week, d.todayIndex, d.weekLabels)

        // Книги стопкой
        val book = d.book
        if (book != null) {
            val pals = Ribbons.assign(ctx, d.reading.sortedBy { it.startDate }.map { it.id })
            val items = d.reading.map {
                BookCoverView.Item(
                    id = it.id,
                    author = it.author,
                    title = it.title.ifBlank { "без названия" },
                    palette = pals[it.id] ?: Ribbons.fallback,
                    pattern = Ribbons.patternFor(it.id)
                )
            }
            Ribbons.setCurrent(ctx, items.first().palette)
            val progress = if (book.pagesTotal > 0) book.pagesRead / book.pagesTotal.toFloat() else null
            b.bookCover.setBooks(items, formatHM(d.bookMinutes), d.bricks, progress)
        } else {
            Ribbons.setCurrent(ctx, Ribbons.fallback)
            b.bookCover.setEmpty("здесь появится книга, которую ты читаешь")
        }

        // Полка
        b.tvShelfTitle.text = "книг за ${MONTHS_NOM[d.today.monthValue - 1]}"
        val count = SpannableStringBuilder(d.finishedThisMonth.size.toString())
        if (d.pending > 0) {
            val start = count.length
            count.append("+${d.pending}")
            // Крупно (0,8 от числа) и чуть приподнято: обычный верхний индекс при таком размере обрезался бы сверху
            count.setSpan(RaiseSpan(0.8f, 0.24f), start, count.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            // На тёмном фоне цифра после плюса светлая, иначе тёмное на тёмном
            val plusColor = when {
                !RtColors.isNight(ctx) -> col(R.color.rt_ink2)
                RtTheme.isGlass(ctx) -> 0xFFC9B6FF.toInt()
                else -> col(R.color.rt_ink2)
            }
            count.setSpan(ForegroundColorSpan(plusColor), start, count.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        b.tvMonthCount.text = count
        b.shelf.set(d.finishedThisMonth.map { it.id }, d.ghosts)

        // Читалки
        val readers = Constants.detectTrackedApps(ctx).entries.distinctBy { it.value }.take(4)
        val readPkg = book?.appPackage?.takeIf { pkg -> readers.any { it.key == pkg } } ?: readers.firstOrNull()?.key
        val readName = readers.firstOrNull { it.key == readPkg }?.value
            ?: book?.appName?.takeIf { it.isNotBlank() } ?: "читалка не найдена"
        // Бумажная книга (онлайн, другое) сверху стопки: «читать» запускает таймер, читалка не открывается
        // Книга «В интернете» с адресом: «читать» открывает её в браузере, трекер следит за адресом
        val web = book != null && book.appPackage == Book.PKG_ONLINE && book.url.isNotBlank()
        val offPhone = book != null && book.appPackage in OFF_PHONE && !web
        fun timerLabel() = if (book != null && ManualTimer.isRunningFor(book.id)) "остановить таймер" else "запуск таймера"
        b.btnRead.setReader(when {
            web -> com.readingtracker.service.WebReading.browserName(ctx)
            offPhone -> timerLabel()
            else -> readName
        })
        b.btnRead.setOnClickListener {
            if (web && book != null) {
                if (launching) return@setOnClickListener
                launching = true
                b.btnRead.postDelayed({
                    launching = false
                    com.readingtracker.service.WebReading.open(ctx, book.id, book.url)
                }, if (RtTheme.motion(ctx)) READ_DELAY_MS else 0L)
            } else if (offPhone && book != null) {
                if (launching) return@setOnClickListener
                launching = true
                b.btnRead.postDelayed({
                    launching = false
                    if (ManualTimer.isRunningFor(book.id)) ManualTimer.stop(ctx)
                    else {
                        ManualTimer.start(ctx, book.id)
                        Toast.makeText(ctx, ManualTimer.hint(), Toast.LENGTH_LONG).show()
                    }
                    _b?.btnRead?.postDelayed({ _b?.btnRead?.setReader(timerLabel()) }, 400)
                }, if (RtTheme.motion(ctx)) READ_DELAY_MS else 0L)
            } else if (readPkg == null) {
                Toast.makeText(ctx, "Читалки не найдены", Toast.LENGTH_SHORT).show()
            } else if (!launching) {
                // Эффект успевает доиграть: читалка открывается через 0,8 с
                launching = true
                val delay = if (RtTheme.motion(ctx)) READ_DELAY_MS else 0L
                b.btnRead.postDelayed({
                    launching = false
                    if (book != null && book.appPackage == readPkg) Prefs.setPendingBook(ctx, readPkg, book.id)
                    ReaderLauncher.open(ctx, readPkg)
                }, delay)
            }
        }
        // В ряду «Свет» первым, остальные как были
        buildReaders(readers.sortedBy { if (it.value == "Свет") 0 else 1 }.map { it.key to it.value })

        // Сегодня за книгой
        val goal = Prefs.goalMinutes(ctx)
        val hourRing = Prefs.hourRing(ctx)
        val m = d.todayMinutes
        b.ring.set(m, goal, hourRing)
        b.tvRingCaption.text = when {
            statsOnly -> if (m >= 60) "полных часов: ${m / 60}" else ""
            m == 0L -> ZERO_PHRASES[Random.nextInt(ZERO_PHRASES.size)]
            hourRing -> if (m >= 60) "полных часов: ${m / 60}" else "до часа: ${60 - m} мин"
            m > goal -> "цель взята, второй круг"
            m == goal.toLong() -> "цель взята"
            else -> "цель: $goal мин"
        }

        // Дни подряд: витраж, после 30 дней — розетка у заголовка
        b.tvStreak.text = d.streak.toString()
        b.streakGlass.set(d.streak)
        b.rosette.visibility = if (d.streak >= StainedGlassView.CELLS) View.VISIBLE else View.GONE
    }

    /** Ряд под книгой: кнопки читалок. Таймер — в середине нижнего меню. */
    private fun buildReaders(readers: List<Pair<String, String>>) {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        b.readersRow.removeAllViews()
        val has = readers.isNotEmpty()
        b.readersRow.visibility = if (has) View.VISIBLE else View.INVISIBLE
        b.tvReadersLabel.visibility = if (has) View.VISIBLE else View.INVISIBLE
        b.tvReadersLabel.text = "открыть читалку"
        val btnBg = RtTheme.resId(ctx, R.attr.rtReaderBtn)
        readers.forEachIndexed { i, (pkg, name) ->
            val tv = TextView(ctx).apply {
                // «Яндекс Книги» в две строки, иначе не помещается
                text = if (name == "Яндекс Книги") "Яндекс\nКниги" else name
                gravity = Gravity.CENTER
                setLineSpacing(0f, 0.9f)
                setTextColor(col(R.color.rt_ink))
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
                typeface = Fonts.regular(ctx)
                maxLines = 2
                if (btnBg != 0) setBackgroundResource(btnBg)
                isClickable = true
                isFocusable = true
                setOnClickListener { ReaderLauncher.open(ctx, pkg) }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            if (i > 0) lp.marginStart = (6 * dp).toInt()
            b.readersRow.addView(tv, lp)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    /** Часть числа меньшим кеглем (size) и приподнятая на rise от основного кегля. */
    private class RaiseSpan(private val size: Float, private val rise: Float) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) = shift(tp)
        override fun updateMeasureState(tp: TextPaint) = shift(tp)
        private fun shift(tp: TextPaint) {
            val base = tp.textSize
            tp.textSize = base * size
            tp.baselineShift -= (base * rise).toInt()
        }
    }

    companion object {
        private const val READ_DELAY_MS = 800L
        /** Книги не в телефонной читалке: «читать» для них — таймер. */
        val OFF_PHONE = setOf(Book.PKG_PAPER, Book.PKG_ONLINE, Book.PKG_OTHER)
        private val MONTHS_GEN = arrayOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
        )
        private val MONTHS_NOM = arrayOf(
            "январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
        )
        private val ZERO_PHRASES = arrayOf(
            "одну главу?", "страница, другая…", "пару страничек?", "полглавы?", "десяток страниц?",
            "пока закипает чайник", "до нужной остановки", "перед сном 10 мин", "закладка скучает",
            "книга ждёт, но не вечно"
        )

        /** «5 ч 12 мин», «52 мин», «6 ч». */
        fun formatHM(minutes: Long): String {
            val h = minutes / 60; val m = minutes % 60
            return when {
                h == 0L -> "$m мин"
                m == 0L -> "$h ч"
                else -> "$h ч $m мин"
            }
        }
    }
}
