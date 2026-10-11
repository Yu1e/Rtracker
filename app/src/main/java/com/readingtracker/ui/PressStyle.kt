package com.readingtracker.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.readingtracker.ui.home.RibbonPalette
import kotlin.random.Random

/**
 * Нажатия раскрашиваются двумя цветами ляссе текущей книги.
 * Варианты чередуются случайно: А «тесьма и закладка», Б «цвета пополам»,
 * В «ромбики», Г «лента через угол».
 */
enum class PressVariant { A, B, C, D;
    companion object {
        private var last: PressVariant? = null
        /** Случайный вариант, не повторяющий предыдущий подряд. */
        fun next(): PressVariant {
            val pool = values().filter { it != last }
            return pool[Random.nextInt(pool.size)].also { last = it }
        }
    }
}

object PressDraw {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Ряд ромбиков по центру строки. */
    fun diamonds(canvas: Canvas, cx: Float, cy: Float, count: Int, size: Float, gap: Float, color: Int) {
        p.color = color; p.style = Paint.Style.FILL
        val step = size * 1.41f + gap
        var x = cx - step * (count - 1) / 2f
        val h = size * 0.707f
        repeat(count) {
            val path = Path().apply { moveTo(x, cy - h); lineTo(x + h, cy); lineTo(x, cy + h); lineTo(x - h, cy); close() }
            canvas.drawPath(path, p)
            x += step
        }
    }

    /** Лента наискосок через правый верхний угол прямоугольника. */
    fun sash(canvas: Canvas, rect: RectF, pal: RibbonPalette) {
        val r = rect.right; val t = rect.top
        p.style = Paint.Style.FILL; p.color = pal.base
        val band = Path().apply { moveTo(r - 60f, t); lineTo(r - 36f, t); lineTo(r, t + 36f); lineTo(r, t + 60f); close() }
        canvas.drawPath(band, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = pal.stripe
        canvas.drawLine(r - 55f, t, r, t + 55f, p)
        canvas.drawLine(r - 41f, t, r, t + 41f, p)
    }

    /** Маленькая закладка с ласточкиным хвостом, свисающая сверху. */
    fun tab(canvas: Canvas, cx: Float, top: Float, pal: RibbonPalette) {
        p.style = Paint.Style.FILL; p.color = pal.base
        val path = Path().apply {
            moveTo(cx - 6f, top); lineTo(cx + 6f, top); lineTo(cx + 6f, top + 18f); lineTo(cx, top + 13.5f); lineTo(cx - 6f, top + 18f); close()
        }
        canvas.drawPath(path, p)
        p.color = pal.stripe
        canvas.drawRect(cx - 4f, top, cx - 2.4f, top + 15.5f, p)
        canvas.drawRect(cx + 2.4f, top, cx + 4f, top + 15.5f, p)
    }
}
