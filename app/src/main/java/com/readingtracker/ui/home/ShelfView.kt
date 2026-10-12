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
 * Читаемые книги — призрачные, слева от готовых. Каждый раз по-новому: все стоят,
 * прислонившись друг к другу домино (первая — к книгам или к стенке), все лежат стопкой,
 * или часть стоит, часть лежит.
 * Рисуется в поле 96×64 dp, прижато к правому краю.
 */
class ShelfView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var bookIds: List<Long> = emptyList()
    private var ghosts = 0
    private var seed = System.nanoTime()

    private enum class Mode { LEAN, LIE, MIX }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.1f
        pathEffect = DashPathEffect(floatArrayOf(2.6f, 1.9f), 0f)
    }

    /** ids готовых книг по порядку дочитывания (первая встанет у стенки); ghosts — сколько читается. */
    fun set(finishedIds: List<Long>, ghosts: Int) {
        this.bookIds = finishedIds
        this.ghosts = ghosts.coerceIn(0, 4)
        contentDescription = "книг за месяц: ${finishedIds.size}" + if (ghosts > 0) ", читается: $ghosts" else ""
        invalidate()
    }

    /** Призраки встают по-новому. */
    fun reshuffle() { seed = System.nanoTime(); invalidate() }

    private fun mode(): Mode {
        val r = Random(seed)
        return if (ghosts >= 2) Mode.values()[r.nextInt(3)] else if (r.nextBoolean()) Mode.LEAN else Mode.LIE
    }

    // Домино — не больше трёх: четвёртый лёг бы почти плашмя, он ложится в стопку
    private fun leaning(): Int = when (mode()) {
        Mode.LEAN -> minOf(ghosts, 3)
        Mode.LIE -> 0
        Mode.MIX -> minOf(1 + Random(seed + 1).nextInt(ghosts - 1), 3)
    }

    /** Сколько места по ширине займут призраки. */
    private fun ghostSpace(small: Boolean): Float {
        if (ghosts == 0) return 0f
        val k = if (small) 0.6f else 1f
        val lean = leaning(); val lie = ghosts - lean
        val leanW = when (lean) { 0 -> 0f; 1 -> 20f; 2 -> 30f; else -> 41f }
        val lieW = if (lie > 0) 38f else 0f
        return (leanW + lieW + if (lean > 0 && lie > 0) 1.5f else 0f) * k
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

    private fun drawGhost(canvas: Canvas, xr: Float, base: Float, w: Float, h: Float, angle: Float = 12f) {
        canvas.save()
        canvas.rotate(angle, xr, base)
        ghostPaint.color = col(R.color.rt_ghost)
        canvas.drawRoundRect(RectF(xr - w + 0.55f, base - h + 0.55f, xr - 0.55f, base - 0.55f), 1.3f, 1.3f, ghostPaint)
        line.color = col(R.color.rt_ghost_stripe); line.strokeWidth = 1.2f
        canvas.drawLine(xr - w + 1.2f, base - h + h * 0.12f, xr - 1.2f, base - h + h * 0.12f, line)
        canvas.drawLine(xr - w + 1.2f, base - h * 0.2f, xr - 1.2f, base - h * 0.2f, line)
        canvas.restore()
    }

    /** Призрак, лежащий плашмя: правый край — xr, низ — base. */
    private fun drawLyingGhost(canvas: Canvas, xr: Float, base: Float, len: Float, th: Float) {
        ghostPaint.color = col(R.color.rt_ghost)
        canvas.drawRoundRect(RectF(xr - len + 0.55f, base - th + 0.55f, xr - 0.55f, base - 0.55f), 1.3f, 1.3f, ghostPaint)
        line.color = col(R.color.rt_ghost_stripe); line.strokeWidth = 1.2f
        canvas.drawLine(xr - len + len * 0.12f, base - th + 1.2f, xr - len + len * 0.12f, base - 1.2f, line)
        canvas.drawLine(xr - len * 0.2f, base - th + 1.2f, xr - len * 0.2f, base - 1.2f, line)
    }

    /** Ставит призраков слева от x (край книг или стенка); возвращает новый левый край. */
    private fun placeGhosts(canvas: Canvas, edge: Float, base: Float, small: Boolean): Float {
        val r = Random(seed + 7)
        val k = if (small) 0.6f else 1f
        val lean = leaning()
        var left = edge
        // Стоят домино: первый прислоняется к книгам, следующий — к предыдущему
        val angles = floatArrayOf(12f, 25f, 38f)
        val heights = floatArrayOf(42f, 39f, 36f)
        val w = (if (small) 9f else 10.5f)
        var prev: Triple<Float, Float, Float>? = null  // ширина, правый нижний угол, наклон
        for (i in 0 until lean) {
            val h = heights[i] * (if (small) 0.58f else 1f)
            val a = angles[i]
            val xr = if (prev == null) {
                edge - 0.6f - h * sin(Math.toRadians(a.toDouble())).toFloat()
            } else {
                val (pw, ppx, pa) = prev
                val r1 = Math.toRadians(pa.toDouble()); val r2 = Math.toRadians(a.toDouble())
                val t = (h * cos(r2) - pw * sin(r1)) / cos(r1)
                (ppx - pw * cos(r1) + t * sin(r1) - h * sin(r2)).toFloat()
            }
            drawGhost(canvas, xr, base, w, h, a)
            left = xr - w * cos(Math.toRadians(a.toDouble())).toFloat()
            prev = Triple(w, xr, a)
        }
        // Лежат стопкой
        val lie = ghosts - lean
        if (lie > 0) {
            val right = (if (lean > 0) left - 1.5f else edge - 0.8f)
            var y = base
            var minLeft = right
            for (i in 0 until lie) {
                val len = (34f + r.nextFloat() * 6f) * k
                val th = (8.5f + r.nextFloat() * 1.5f) * k
                val xr = right - r.nextFloat() * 3f * k
                drawLyingGhost(canvas, xr, y, len, th)
                minLeft = minOf(minLeft, xr - len)
                y -= th + 0.4f
            }
            left = minLeft
        }
        return left
    }

    /** Ставит ряд книг от стенки влево; возвращает левый край ряда. */
    private fun row(canvas: Canvas, wall: Float, base: Float, ids: List<Long>, small: Boolean, withGhost: Boolean): Float {
        var right = wall - 1.3f
        for (id in ids) {
            val s = spineFor(id, small)
            val x = right - s.w
            drawSpine(canvas, x, base, s)
            right = x - 1.2f
        }
        var left = right + 1.2f
        if (withGhost && ghosts > 0) {
            left = placeGhosts(canvas, if (ids.isNotEmpty()) left else wall, base, small)
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
        fun rowWidth(ids: List<Long>, small: Boolean): Float =
            ids.sumOf { (spineFor(it, small).w + 1.2f).toDouble() }.toFloat() + ghostSpace(small)

        val oneRowFits = rowWidth(bookIds, false) <= 86f
        if (oneRowFits) {
            val left = row(canvas, wall, 61f, bookIds, false, true)
            line.color = shelfColor; line.strokeWidth = 2.5f; line.strokeCap = Paint.Cap.BUTT
            canvas.drawLine(minOf(left - 5f, 58f).coerceAtLeast(0f), 62f, 95.3f, 62f, line)
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
                if (acc + w > 70f - ghostSpace(true)) break
                acc += w; bottom += id
            }
            row(canvas, wall, 29f, top, true, false)
            val bottomLeft = row(canvas, wall, 61f, bottom, true, true)
            line.color = shelfColor; line.strokeWidth = 2.2f; line.strokeCap = Paint.Cap.BUTT
            canvas.drawLine(28f, 30f, 95.3f, 30f, line)
            canvas.drawLine(minOf(bottomLeft - 4f, 28f).coerceAtLeast(0f), 62f, 95.3f, 62f, line)
            line.strokeWidth = 2.5f; line.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(94.6f, 62.5f, 94.6f, 1.5f, line)
        }
        canvas.restore()
    }
}
