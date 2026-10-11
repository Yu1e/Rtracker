package com.readingtracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.readingtracker.MainActivity
import com.readingtracker.R
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.stats.Stats
import com.readingtracker.stats.StatsCalc
import com.readingtracker.ui.book.BookActivity
import com.readingtracker.ui.book.formatMinutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

/** Виджет рабочего стола: стрик, сегодня, неделя, текущая книга, месяц, год. */
class ReadingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.update(context)
    }
}

object WidgetUpdater {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dayIds = intArrayOf(R.id.wDay0, R.id.wDay1, R.id.wDay2, R.id.wDay3, R.id.wDay4, R.id.wDay5, R.id.wDay6)
    private val cellBg = intArrayOf(
        R.drawable.widget_cell_0, R.drawable.widget_cell_1, R.drawable.widget_cell_2,
        R.drawable.widget_cell_3, R.drawable.widget_cell_4
    )

    fun update(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, ReadingWidgetProvider::class.java))
        if (ids.isEmpty()) return
        scope.launch {
            try {
                val db = AppDatabase.getInstance(app)
                val stats = StatsCalc.compute(db.bookDao().getAllBooksSync(), db.sessionDao().getAllSessions())
                val views = build(app, stats)
                manager.updateAppWidget(ids, views)
            } catch (_: Exception) {}
        }
    }

    private fun level(m: Long) = when {
        m <= 0 -> 0; m < 15 -> 1; m < 30 -> 2; m < 60 -> 3; else -> 4
    }

    private fun daysWord(n: Int): String {
        val n100 = n % 100; val n10 = n100 % 10
        return when {
            n100 in 11..14 -> "дней"; n10 == 1 -> "день"; n10 in 2..4 -> "дня"; else -> "дней"
        }
    }

    private fun hours(m: Long) = if (m < 60) "$m мин" else String.format(Locale("ru"), "%.1f ч", m / 60.0)

    private fun build(c: Context, s: Stats): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_reading)

        v.setTextViewText(R.id.wStreak, "${s.currentStreak}")
        v.setTextViewText(R.id.wStreakLabel, "${daysWord(s.currentStreak)} подряд")
        v.setTextViewText(R.id.wToday, formatMinutes(s.todayMinutes))

        val record = when {
            s.currentStreak == 0 && s.bestStreak == 0 -> "Открой книгу — начнётся стрик"
            s.currentStreak == 0 -> "Рекорд ${s.bestStreak} ${daysWord(s.bestStreak)}. Начни новый стрик сегодня"
            !s.readToday -> "Сегодня ещё не читала — стрик ждёт. Рекорд: ${s.bestStreak}"
            s.currentStreak >= s.bestStreak -> "Это рекордный стрик!"
            else -> "До рекорда (${s.bestStreak}) осталось ${s.bestStreak - s.currentStreak} ${daysWord(s.bestStreak - s.currentStreak)}"
        }
        v.setTextViewText(R.id.wRecord, record)

        // Неделя: последние 7 дней, сегодня справа
        for (i in 0 until 7) {
            val day = s.today.minusDays((6 - i).toLong())
            val m = s.dayMinutes[day] ?: 0
            val label = if (i == 6) "сег" else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
            v.setTextViewText(dayIds[i], label)
            v.setInt(dayIds[i], "setBackgroundResource", cellBg[level(m)])
            v.setTextColor(dayIds[i], if (i == 6) 0xFFF2F2F0.toInt() else 0xFFB5B5B0.toInt())
        }

        val book = s.currentBook
        if (book != null) {
            v.setTextViewText(R.id.wBook, Book.displayName(book))
            v.setTextViewText(R.id.wBookMeta,
                "${book.appName} · ${formatMinutes(book.totalMinutes)} · ${com.readingtracker.data.BookActions.daysOnBook(book)}-й день")
            val launch = c.packageManager.getLaunchIntentForPackage(book.appPackage)
                ?: Intent(c, BookActivity::class.java).putExtra(BookActivity.EXTRA_BOOK_ID, book.id)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(R.id.wBook, PendingIntent.getActivity(
                c, 1, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            v.setTextViewText(R.id.wBook, "Книга пока не выбрана")
            v.setTextViewText(R.id.wBookMeta, "Открой читалку — трекер спросит, что читаешь")
        }

        val monthName = s.today.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
            .replaceFirstChar { it.uppercase() }
        v.setTextViewText(R.id.wMonth,
            "$monthName: ${s.monthFinished} кн. дочитано · ${hours(s.monthMinutes)} · ${s.monthReadingDays} ${daysWord(s.monthReadingDays)} с чтением")
        v.setTextViewText(R.id.wYear,
            "В этом году: ${s.yearFinished} кн. · ${hours(s.yearMinutes)}")

        val open = PendingIntent.getActivity(c, 0,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        v.setOnClickPendingIntent(R.id.wRoot, open)
        return v
    }
}
