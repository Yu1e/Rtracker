package com.readingtracker.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.readingtracker.Constants
import com.readingtracker.MainActivity
import com.readingtracker.diag.Diag
import com.readingtracker.service.ReadingTrackerService

/**
 * Ворота для читалок: трекер просыпается, потом открывается читалка.
 * Через них работают ярлыки на рабочем столе, кнопки в трекере, виджет и карточка книги.
 */
class LaunchReaderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(ReaderLauncher.EXTRA_PKG)
        if (pkg == null) { finish(); return }

        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            Toast.makeText(this, "Читалка не установлена", Toast.LENGTH_LONG).show()
            finish(); return
        }

        // Без разрешений трекер не увидит чтение — отправляем в настройку, но книгу всё равно открываем
        val ready = Diag.hasUsageAccess(this) && Settings.canDrawOverlays(this)
        if (ready) {
            try {
                ReadingTrackerService.startForReader(this, pkg)
            } catch (e: Exception) {
                Diag.set(this, Diag.ERROR, "${Diag.time()} запуск службы: $e")
            }
        } else {
            Toast.makeText(this, "Трекер не настроен: откройте его и дайте разрешения", Toast.LENGTH_LONG).show()
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
        finish()
        overridePendingTransition(0, 0)
    }
}

object ReaderLauncher {
    const val EXTRA_PKG = "reader_pkg"

    fun intentFor(context: Context, pkg: String): Intent =
        Intent(context, LaunchReaderActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_PKG, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

    fun open(context: Context, pkg: String) {
        context.startActivity(intentFor(context, pkg))
    }

    private fun iconFor(context: Context, pkg: String): IconCompat? = try {
        val d = context.packageManager.getApplicationIcon(pkg)
        val size = (108 * context.resources.displayMetrics.density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        IconCompat.createWithBitmap(bmp)
    } catch (e: Exception) { null }

    private fun shortcut(context: Context, pkg: String, name: String): ShortcutInfoCompat {
        val b = ShortcutInfoCompat.Builder(context, "reader_$pkg")
            .setShortLabel(name)
            .setLongLabel("$name через трекер")
            .setIntent(intentFor(context, pkg))
        iconFor(context, pkg)?.let { b.setIcon(it) }
        return b.build()
    }

    /** Ярлык на рабочий стол. Android покажет окно подтверждения. */
    fun requestPin(context: Context, pkg: String, name: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut(context, pkg, name), null)
    }

    /** Пункты в меню долгого нажатия на значок трекера. */
    fun updateDynamic(context: Context) {
        try {
            val list = Constants.detectTrackedApps(context).entries
                .distinctBy { it.value }
                .take(4)
                .map { shortcut(context, it.key, it.value) }
            ShortcutManagerCompat.setDynamicShortcuts(context, list)
        } catch (_: Exception) {}
    }

    /** Для экрана, откуда нет кнопки «назад»: открыть трекер на настройке. */
    fun openSetup(context: Context) {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
