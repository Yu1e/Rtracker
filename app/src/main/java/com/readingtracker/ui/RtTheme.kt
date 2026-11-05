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

    /** Ступени теплокарты: 0 — нейтральная клетка, 1–4 — во всех темах от светлого к тёмному и сочному. */
    fun heat(c: Context, level: Int): Int {
        if (isGlass(c)) {
            // Стекло: фиолетовый с широким размахом (без розового). В обеих темах чем больше минут,
            // тем темнее и сочнее: от светло-лилового к густому фиолетовому; пустые дни — только контур
            val night = RtColors.isNight(c)
            val steps = if (night) intArrayOf(0x24FFFFFF, 0xFFD9CCFF.toInt(), 0xFFA88DFF.toInt(), 0xFF7B5CFF.toInt(), 0xFF6236F0.toInt())
            else intArrayOf(0xFFE6E0F7.toInt(), 0xFFCDBDFF.toInt(), 0xFFA58EFF.toInt(), 0xFF7B5CFF.toInt(), 0xFF4B2FB0.toInt())
            return steps[level.coerceIn(0, 4)]
        }
        val zero = RtColors.c(c, R.color.rt_heat0)
        if (level <= 0) return zero
        if (isEink(c)) return intArrayOf(0xFFBBBBBB.toInt(), 0xFF888888.toInt(), 0xFF444444.toInt(), 0xFF000000.toInt())[(level - 1).coerceIn(0, 3)]
        val a = accent(c)
        if (RtColors.isNight(c)) {
            // Тёмная тема: тоже чем больше минут, тем темнее и сочнее — от бледного оттенка акцента к густому
            return when ((level - 1).coerceIn(0, 3)) {
                0 -> RtColors.mix(a, android.graphics.Color.WHITE, 0.78f)
                1 -> RtColors.mix(a, android.graphics.Color.WHITE, 0.45f)
                2 -> a
                else -> RtColors.mix(a, android.graphics.Color.BLACK, 0.3f)
            }
        }
        val t = floatArrayOf(0.3f, 0.55f, 0.8f, 1f)[(level - 1).coerceIn(0, 3)]
        return RtColors.mix(zero, a, t)
    }
}
