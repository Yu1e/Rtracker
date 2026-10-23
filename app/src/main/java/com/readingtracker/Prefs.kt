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

    /** Оформление: «mono» (ч/б), «glass» (стекло), «eink» (электронные чернила). */
    fun style(c: Context): String = p(c).getString("style", "glass") ?: "glass"
    fun setStyle(c: Context, v: String) { p(c).edit().putString("style", v).apply() }

    /** Светлая или тёмная: «system», «light», «dark». */
    fun night(c: Context): String = p(c).getString("night", "system") ?: "system"
    fun setNight(c: Context, v: String) { p(c).edit().putString("night", v).apply() }

    /** Кольцо «сегодня за книгой»: «goal» — до цели дня, «hour» — круг за каждый час. */
    fun ringMode(c: Context): String = p(c).getString("ring_mode", "goal") ?: "goal"
    fun setRingMode(c: Context, v: String) { p(c).edit().putString("ring_mode", v).apply() }

    /** Только статистика: без целей и рекордов. */
    fun statsOnly(c: Context): Boolean = p(c).getBoolean("stats_only", false)
    fun setStatsOnly(c: Context, v: Boolean) { p(c).edit().putBoolean("stats_only", v).apply() }

    /** Кольцо считает часы: выбрано «час» или включена «только статистика». */
    fun hourRing(c: Context): Boolean = statsOnly(c) || ringMode(c) == "hour"

    /** Праздничные эффекты за достижения. */
    fun effects(c: Context): Boolean = p(c).getBoolean("effects", true)
    fun setEffects(c: Context, v: Boolean) { p(c).edit().putBoolean("effects", v).apply() }
}
