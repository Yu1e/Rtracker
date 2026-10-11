package com.readingtracker

import android.content.Context
import android.net.Uri

/** Небольшие настройки приложения. */
object Prefs {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Книга, открытая через трекер-посредник: следующий вход в читалку не требует пузырька
    fun setPendingBook(c: Context, pkg: String, bookId: Long) {
        p(c).edit().putLong("pending_$pkg", bookId)
            .putLong("pending_time_$pkg", System.currentTimeMillis()).apply()
    }

    /** Возвращает и стирает книгу-ожидание, если она назначена не раньше 3 минут назад. */
    fun takePendingBook(c: Context, pkg: String): Long? {
        val prefs = p(c)
        val id = prefs.getLong("pending_$pkg", -1)
        val time = prefs.getLong("pending_time_$pkg", 0)
        prefs.edit().remove("pending_$pkg").remove("pending_time_$pkg").apply()
        return if (id > 0 && System.currentTimeMillis() - time < 3 * 60_000) id else null
    }

    fun backupFolder(c: Context): Uri? =
        p(c).getString("backup_folder", null)?.let { Uri.parse(it) }

    fun setBackupFolder(c: Context, uri: Uri?) {
        p(c).edit().putString("backup_folder", uri?.toString()).apply()
    }

    fun lastBackupDay(c: Context): String? = p(c).getString("last_backup_day", null)
    fun setLastBackup(c: Context, day: String, info: String) {
        p(c).edit().putString("last_backup_day", day).putString("last_backup_info", info).apply()
    }
    fun lastBackupInfo(c: Context): String? = p(c).getString("last_backup_info", null)

    /** Цель дня в минутах: кольцо «сегодня за книгой». */
    fun goalMinutes(c: Context): Int = p(c).getInt("goal_minutes", 60)
    fun setGoalMinutes(c: Context, m: Int) { p(c).edit().putInt("goal_minutes", m).apply() }

    fun iconVariant(c: Context): Int = p(c).getInt("icon_variant", 0)
    fun setIconVariant(c: Context, v: Int) { p(c).edit().putInt("icon_variant", v).apply() }
}
