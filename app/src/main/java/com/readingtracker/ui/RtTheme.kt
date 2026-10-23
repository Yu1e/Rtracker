package com.readingtracker.ui

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import com.readingtracker.Prefs
import com.readingtracker.R
import com.readingtracker.ui.home.Ribbons

/**
 * Оформление приложения: ч/б, стекло или электронные чернила; светлое или тёмное.
 * Цвета лежат в атрибутах темы с теми же именами, что и цвета ч/б (rt_ink и т. д.),
 * так что RtColors.c(context, R.color.rt_ink) отдаёт цвет текущего оформления.
 */
object RtTheme {
    enum class Style(val key: String) { MONO("mono"), GLASS("glass"), EINK("eink") }

    fun style(c: Context): Style = Style.values().firstOrNull { it.key == Prefs.style(c) } ?: Style.GLASS

    fun isGlass(c: Context) = style(c) == Style.GLASS
    fun isEink(c: Context) = style(c) == Style.EINK

    fun themeRes(c: Context): Int = when (style(c)) {
        Style.MONO -> R.style.Theme_ReadingTracker
        Style.GLASS -> R.style.Theme_ReadingTracker_Glass
        Style.EINK -> R.style.Theme_ReadingTracker_Eink
    }

    /** Ставит тему оформления; вызывать до super.onCreate. */
    fun apply(activity: Activity) {
        activity.setTheme(themeRes(activity))
    }

    /** Светлая или тёмная. Электронные чернила всегда светлые. */
    fun applyNight(c: Context) {
        val mode = if (isEink(c)) AppCompatDelegate.MODE_NIGHT_NO else when (Prefs.night(c)) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Ресурс, на который указывает атрибут темы (например, фон кнопки). */
    fun resId(c: Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        return if (c.theme.resolveAttribute(attr, tv, true)) tv.resourceId else 0
    }

    /** Движение разрешено: не чернила и анимации в системе не выключены. */
    fun motion(c: Context): Boolean {
        if (isEink(c)) return false
        val scale = try {
            Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Exception) { 1f }
        return scale > 0f
    }

    /** Акцент графиков, теплокарты и списка книг: в ч/б — из ляссе верхней книги, иначе цвет оформления. */
    fun accent(c: Context): Int =
        if (style(c) == Style.MONO) Ribbons.accent(c) else RtColors.c(c, R.color.rt_accent)

    /** Ступени теплокарты: 0 — нейтральная клетка, 1–4 — всё насыщеннее к цвету акцента. */
    fun heat(c: Context, level: Int): Int {
        val zero = RtColors.c(c, R.color.rt_heat0)
        if (level <= 0) return zero
        if (isEink(c)) return intArrayOf(0xFFBBBBBB.toInt(), 0xFF888888.toInt(), 0xFF444444.toInt(), 0xFF000000.toInt())[(level - 1).coerceIn(0, 3)]
        val t = floatArrayOf(0.3f, 0.55f, 0.8f, 1f)[(level - 1).coerceIn(0, 3)]
        return RtColors.mix(if (isGlass(c)) RtColors.withAlpha(accent(c), 0.15f) else zero, accent(c), t)
    }
}
