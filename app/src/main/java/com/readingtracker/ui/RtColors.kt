package com.readingtracker.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Цвета оформления и немного арифметики контраста. */
object RtColors {
    private val attrOf = HashMap<Int, Int>()

    /**
     * Цвет текущего оформления. Для цветов rt_* берётся одноимённый атрибут темы
     * (стекло и чернила подменяют цвета ч/б); если его нет — сам цвет.
     */
    fun c(context: Context, id: Int): Int {
        val attr = synchronized(attrOf) {
            attrOf.getOrPut(id) {
                val name = try { context.resources.getResourceEntryName(id) } catch (_: Exception) { "" }
                // Пакет ресурсов берём у самого цвета: у отладочной сборки имя приложения другое
                if (name.startsWith("rt_")) context.resources.getIdentifier(name, "attr",
                    context.resources.getResourcePackageName(id)) else 0
            }
        }
        if (attr != 0) {
            val tv = TypedValue()
            if (context.theme.resolveAttribute(attr, tv, true)) {
                return if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
            }
        }
        return ContextCompat.getColor(context, id)
    }

    private fun channel(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(color: Int): Double =
        0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color)) + 0.0722 * channel(Color.blue(color))

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Из кандидатов выбрать самый читаемый на фоне. */
    fun mostReadable(bg: Int, vararg candidates: Int): Int =
        candidates.maxByOrNull { contrast(it, bg) } ?: Color.BLACK

    /** Белый или почти чёрный — что лучше читается на этом цвете. */
    fun onColor(bg: Int): Int = mostReadable(bg, Color.WHITE, 0xFF0F0F0E.toInt())

    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return Color.argb(
            ch(Color.alpha(a), Color.alpha(b)), ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b))
        )
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
}
