package com.readingtracker.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.readingtracker.R
import com.readingtracker.ui.RtColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Полка «книг за месяц». Готовые книги встают от правой стенки и добавляются слева.
 * Начатая книга — призрачная: стоит слева от всех и прислоняется к ним,
 * а на пустой полке прислоняется к стенке.
 * Рисуется в поле 96×64 dp, прижато к правому краю.
 */
class ShelfView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var bookIds: List<Long> = emptyList()
    private var ghost = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.1f
        pathEffect = DashPathEffect(floatArrayOf(2.6f, 1.9f), 0f)
    }

    /** ids готовых книг по порядку дочитывания (первая встанет у стенки). */
    fun set(finishedIds: List<Long>, ghost: Boolean) {
        this.bookIds = finishedIds
        this.ghost = ghost
        contentDescription = "книг за месяц: ${finishedIds.size}" + if (ghost) ", и одна читается" else ""
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((96 * d).toInt(), widthMeasureSpec), resolveSize((64 * d).toInt(), heightMeasureSpec))
    }

    private fun col(id: Int) = RtColors.c(context, id)

    private enum class Pat { TWO, MID, TOP3, LABEL, CAP, BOTTOM2 }
    private data class Spine(val w: Float, val h: Float, val tone: Int, val pat: Pat)

    /** У каждой книги свой корешок: толщина, высота, тон и рисунок выводятся из её номера. */
    private fun spineFor(id: Long, small: Boolean): Spine {
        val r = Random(id * 31 + 7)
        val w = if (small) 7f + r.nextInt(4) else 10f + r.nextInt(4)
        val h = if (small) 20f + r.nextInt(8) else 38f + r.nextInt(12)
        val tone = when (r.nextInt(5)) { 0, 1 -> R.color.rt_book_d; 2, 3 -> R.color.rt_book_m; else -> R.color.rt_book_l }
        return Spine(w, h, tone, Pat.values()[r.nextInt(Pat.values().size)])
    }

    private fun drawSpine(canvas: Canvas, x: Float, base: Float, s: Spine) {
        val y = base - s.h
        fill.color = col(s.tone)
        canvas.drawRoundRect(RectF(x, y, x + s.w, base), 1.3f, 1.3f, fill)
        val bg = col(R.color.rt_tile)
        val k = s.h / 46f
        fun band(yy: Float, hh: Float) { fill.color = bg; canvas.drawRect(x, yy, x + s.w, yy + hh, fill) }
        when (s.pat) {
            Pat.TWO -> { band(y + 5 * k, 1.8f); band(base - 9 * k, 1.8f) }
            Pat.MID -> band(y + s.h * 0.42f, 3.6f)
            Pat.TOP3 -> { band(y + 4 * k, 1.2f); band(y + 7 * k, 1.2f); band(y + 10 * k, 1.2f) }
            Pat.LABEL -> {
                band(y + 5 * k, 1.4f)
                fill.color = bg
                canvas.drawRoundRect(RectF(x + 2.5f, y + s.h * 0.3f, x + s.w - 2.5f, y + s.h * 0.52f), 1f, 1f, fill)
            }
            Pat.CAP -> {
                fill.color = col(R.color.rt_book_cap)
                canvas.drawRoundRect(RectF(x, y, x + s.w, y + s.h * 0.16f), 1.3f, 1.3f, fill)
                band(base - 7 * k, 1.4f)
            }
            Pat.BOTTOM2 -> { band(base - 12 * k, 1.4f); band(base - 8 * k, 1.4f) }
        }
    }

    private fun drawGhost(canvas: Canvas, xr: Float, base: Float, w: Float, h: Float) {
        canvas.save()
        canvas.rotate(12f, xr, base)
        ghostPaint.color = col(R.color.rt_ghost)
        canvas.drawRoundRect(RectF(xr - w + 0.55f, base - h + 0.55f, xr - 0.55f, base - 0.55f), 1.3f, 1.3f, ghostPaint)
        line.color = col(R.color.rt_ghost_stripe); line.strokeWidth = 1.2f
        canvas.drawLine(xr - w + 1.2f, base - h + h * 0.12f, xr - 1.2f, base - h + h * 0.12f, line)
        canvas.drawLine(xr - w + 1.2f, base - h * 0.2f, xr - 1.2f, base - h * 0.2f, line)
        canvas.restore()
    }

    /** Ставит ряд книг от стенки влево; возвращает левый край ряда. */
    private fun row(canvas: Canvas, wall: Float, base: Float, ids: List<Long>, small: Boolean, withGhost: Boolean, ghostW: Float, ghostH: Float): Float {
        var right = wall - 1.3f
        for (id in ids) {
            val s = spineFor(id, small)
            val x = right - s.w
            drawSpine(canvas, x, base, s)
            right = x - 1.2f
        }
        var left = right + 1.2f
        if (withGhost) {
            val target = if (ids.isNotEmpty()) left - 0.6f else wall - 0.6f
            val xr = target - ghostH * sin(Math.toRadians(12.0)).toFloat()
            drawGhost(canvas, xr, base, ghostW, ghostH)
            left = xr - ghostW * cos(Math.toRadians(12.0)).toFloat()
        }
        return left
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(d, d)
        canvas.translate(width / d - 96f, height / d - 64f)
        val shelfColor = col(R.color.rt_shelf)
        val wall = 94f

        // Сколько влезает в один ряд
        fun rowWidth(ids: List<Long>, small: Boolean, g: Boolean): Float =
            ids.sumOf { (spineFor(it, small).w + 1.2f).toDouble() }.toFloat() + if (g) (if (small) 15f else 20f) else 0f

        val oneRowFits = rowWidth(bookIds, false, ghost) <= 82f
        if (oneRowFits) {
            val left = row(canvas, wall, 61f, bookIds, false, ghost, 11f, 42f)
            line.color = shelfColor; line.strokeWidth = 2.5f; line.strokeCap = Paint.Cap.BUTT
            canvas.drawLine(minOf(left - 6f, 58f).coerceAtLeast(0f), 62f, 95.3f, 62f, line)
            line.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(94.6f, 62f, 94.6f, 16f, line)
        } else {
            // Две полки: сначала заполняется верхняя, потом нижняя; призрачная — на нижней
            val top = mutableListOf<Long>()
            var acc = 0f
            var i = 0
            while (i < bookIds.size) {
                val w = spineFor(bookIds[i], true).w + 1.2f
                if (acc + w > 62f) break
                acc += w; top += bookIds[i]; i++
            }
            val rest = bookIds.drop(i)
            val bottom = mutableListOf<Long>()
            acc = 0f
            for (id in rest) {
                val w = spineFor(id, true).w + 1.2f
                if (acc + w > 50f) break
                acc += w; bottom += id
            }
            row(canvas, wall, 29f, top, true, false, 0f, 0f)
            row(canvas, wall, 61f, bottom, true, ghost, 9f, 24f)
            line.color = shelfColor; line.strokeWidth = 2.2f; line.strokeCap = Paint.Cap.BUTT
            canvas.drawLine(28f, 30f, 95.3f, 30f, line)
            canvas.drawLine(28f, 62f, 95.3f, 62f, line)
            line.strokeWidth = 2.5f; line.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(94.6f, 62.5f, 94.6f, 1.5f, line)
        }
        canvas.restore()
    }
}
