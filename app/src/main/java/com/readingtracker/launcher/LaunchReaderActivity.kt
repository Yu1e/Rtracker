package com.readingtracker.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.readingtracker.Prefs
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.diag.Diag
import com.readingtracker.service.ReadingTrackerService
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme
import kotlinx.coroutines.runBlocking

/**
 * Ворота для читалок: трекер просыпается, потом открывается читалка.
 * Через них работают ярлыки на рабочем столе, кнопки в трекере, виджет и карточка книги.
 * Если у читалки нет открытой книги — сначала окно: выбирать без счёта или считать сразу.
 */
class LaunchReaderActivity : Activity() {

    private var pkg: String? = null
    private var launch: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(ReaderLauncher.EXTRA_PKG)
        if (pkg == null) { finish(); return }
        this.pkg = pkg

        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            Toast.makeText(this, "Читалка не установлена", Toast.LENGTH_LONG).show()
            finish(); return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        this.launch = launch

        // Без разрешений трекер не увидит чтение — отправляем в настройку, но книгу всё равно открываем
        val ready = Diag.hasUsageAccess(this) && Settings.canDrawOverlays(this)
        if (!ready) {
            Toast.makeText(this, "Трекер не настроен: открой его и дай разрешения", Toast.LENGTH_LONG).show()
            open(); return
        }
        if (Prefs.hasPendingBook(this, pkg)) { start(choosing = false); return }

        // Короткий запрос к базе: есть ли у читалки книга, которая читается или отложена
        val hasOpenBook = try {
            runBlocking {
                val db = AppDatabase.getInstance(this@LaunchReaderActivity)
                val last = db.sessionDao().getLastBookIdForApp(pkg)?.let { db.bookDao().getBookById(it) }
                last != null && Book.isOpen(last.status)
            }
        } catch (e: Exception) { true }

        if (hasOpenBook) start(choosing = false) else askNoBook()
    }

    /** Здесь нет открытой книги: текст и кнопки — как договорились, слово в слово. */
    private fun askNoBook() {
        // Окно светлое или тёмное, как выбрано в трекере (это окно живёт вне его темы)
        val night = !RtTheme.isEink(this) && when (Prefs.night(this)) {
            "dark" -> true
            "light" -> false
            else -> RtColors.isNight(this)
        }
        val theme = if (night)
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        AlertDialog.Builder(this, theme)
            .setMessage(
                "Здесь у тебя нет открытой книги. Если ещё не знаешь и будешь выбирать — нажми «Остановить трекер».\n\n" +
                    "Когда выберешь книгу в этой читалке — закрой читалку, снова запусти трекер и в этом окне нажми «Время чтения пошло»."
            )
            .setNegativeButton("Остановить трекер") { _, _ -> start(choosing = true) }
            .setPositiveButton("Время чтения пошло") { _, _ -> start(choosing = false) }
            .setOnCancelListener { finish() }
            .show()
    }

    /** choosing — трекер на паузе: читалка открывается, время не идёт, каждые 5 минут вопрос. */
    private fun start(choosing: Boolean) {
        try {
            ReadingTrackerService.startForReader(this, pkg!!, choosing)
        } catch (e: Exception) {
            Diag.set(this, Diag.ERROR, "${Diag.time()} запуск службы: $e")
        }
        open()
    }

    private fun open() {
        launch?.let { startActivity(it) }
        finish()
        overridePendingTransition(0, 0)
    }
}
