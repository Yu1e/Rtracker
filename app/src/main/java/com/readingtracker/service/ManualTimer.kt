package com.readingtracker.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Таймер, запущенный вручную: для книги, которую трекер не поймал (другая читалка, pdf, бумага).
 * Дальше чтение ведут перелистывания: 3 минуты тишины — пауза, долгая тишина — таймер останавливается.
 */
object ManualTimer {
    /** Книга, для которой идёт таймер; null — таймер не идёт. */
    @Volatile var bookId: Long? = null
        internal set

    /** Когда запущен. */
    @Volatile var since: Long = 0L
        internal set

    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    internal fun changed() { main.post { listeners.forEach { it() } } }

    fun isRunningFor(id: Long) = bookId == id

    fun start(context: Context, bookId: Long) {
        context.startForegroundService(Intent(context, ReadingTrackerService::class.java)
            .putExtra(ReadingTrackerService.EXTRA_MANUAL_BOOK, bookId))
    }

    /** Подсказка после запуска. */
    fun hint(): String = if (ActivitySignal.accessibilityConnected)
        "Таймер идёт. Если 3 мин нет перелистываний, встанет на паузу"
    else "Таймер идёт. Остановить — кнопкой «стоп» здесь или в уведомлении"

    fun stop(context: Context) {
        if (bookId == null) return
        context.startService(Intent(context, ReadingTrackerService::class.java)
            .setAction(ReadingTrackerService.ACTION_STOP_MANUAL))
    }
}
