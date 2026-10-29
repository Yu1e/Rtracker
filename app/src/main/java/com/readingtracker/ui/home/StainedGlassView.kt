package com.readingtracker.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.readingtracker.R
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * «дней подряд» витражом: окно из 30 осколков цветного стекла в тёмных переплётах.
 * Прочитанный день — осколок загорается; заполнение с правого верхнего осколка,
 * справа налево, ряд за рядом. Узор осколков и их цвета при каждом открытии новые.
 * Каждые 30 дней окно начинается заново, о пройденных тридцати говорит розетка у заголовка.
 */
class StainedGlassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val style = RtTheme.style(context)
    private val night = RtColors.isNight(context)
    private var streak = 0
    private var seed = System.nanoTime()

    /** Осколки в порядке заполнения, в dp. */
    private var cells: List<Cell> = emptyList()
    private var builtFor = Triple(0, 0, 0L)

    private class Cell(val path: Path, val bounds: RectF, val colors: Pair<Int, Int>)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }

    fun set(streak: Int) {
        this.streak = streak.coerceAtLeast(0)
        contentDescription = "дней подряд: $streak"
        invalidate()
    }

    /** Новый узор осколков. */
    fun reshuffle() { seed = System.nanoTime(); cells = emptyList(); invalidate() }

    /** Сколько осколков горит: остаток от тридцати; ровно 30, 60… — окно полное. */
    private fun lit(): Int = when {
        streak <= 0 -> 0
        streak % CELLS == 0 -> CELLS
        else -> streak % CELLS
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((100 * d).toInt(), heightMeasureSpec))
    }

    private fun build(w: Float, h: Float) {
        val rnd = Random(seed)
        val cw = w / COLS; val rh = h / ROWS
        val seeds = ArrayList<FloatArray>()
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            seeds += floatArrayOf(
                (c + 0.5f + (rnd.nextFloat() * 2 - 1) * 0.32f) * cw,
                (r + 0.5f + (rnd.nextFloat() * 2 - 1) * 0.30f) * rh,
                r.toFloat()
            )
        }
        // Справа сверху: по рядам сверху вниз, в ряду справа налево
        val ordered = seeds.sortedWith(compareBy<FloatArray>({ it[2] }, { -it[0] }))
        val palette = HOLO.shuffled(rnd)
        cells = ordered.mapIndexed { i, s ->
            var poly = listOf(floatArrayOf(0f, 0f), floatArrayOf(w, 0f), floatArrayOf(w, h), floatArrayOf(0f, h))
            for (o in seeds) {
                if (o === s) continue
                val a = o[0] - s[0]; val b = o[1] - s[1]
                val c = (o[0] * o[0] + o[1] * o[1] - s[0] * s[0] - s[1] * s[1]) / 2
                poly = clip(poly, a, b, c)
            }
            val path = Path()
            val bounds = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            poly.forEachIndexed { k, p ->
                if (k == 0) path.moveTo(p[0], p[1]) else path.lineTo(p[0], p[1])
                bounds.union(p[0], p[1])
            }
            path.close()
            Cell(path, bounds, palette[i % palette.size])
        }
    }

    /** Часть многоугольника, где a·x + b·y ≤ c. */
    private fun clip(poly: List<FloatArray>, a: Float, b: Float, c: Float): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (i in poly.indices) {
            val p = poly[i]; val q = poly[(i + 1) % poly.size]
            val fp = a * p[0] + b * p[1] - c; val fq = a * q[0] + b * q[1] - c
            if (fp <= 0) out += p
            if (fp * fq < 0) {
                val t = fp / (fp - fq)
                out += floatArrayOf(p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1]))
            }
        }
        return out
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width / d; val h = height / d
        if (w <= 4 || h <= 4) return
        val inset = 1.2f
        val iw = w - 2 * inset; val ih = h - 2 * inset
        val key = Triple(iw.roundToInt(), ih.roundToInt(), seed)
        if (cells.isEmpty() || key != builtFor) { build(iw, ih); builtFor = key }

        canvas.save(); canvas.scale(d, d); canvas.translate(inset, inset)
        val frame = RectF(0f, 0f, iw, ih)
        val radius = 12f
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(frame, radius, radius, Path.Direction.CW) })

        val lit = lit()
        val ink = RtColors.c(context, R.color.rt_ink)
        for ((i, cell) in cells.withIndex()) {
            val on = i < lit
            val b = cell.bounds
            if (on) {
                val (a, bright) = colorsFor(cell, i)
                fill.shader = RadialGradient(
                    b.left + b.width() * 0.4f, b.top + b.height() * 0.35f, max(b.width(), b.height()) * 0.8f,
                    intArrayOf(Color.argb(242, 255, 255, 255), bright, a), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP
                )
            } else {
                fill.shader = null
                fill.color = when (style) {
                    RtTheme.Style.GLASS -> if (night) 0x1FFFFFFF else 0x59FFFFFF
                    else -> RtColors.c(context, R.color.rt_tile)
                }
            }
            canvas.drawPath(cell.path, fill)
        }
        fill.shader = null

        val leadColor = when (style) {
            RtTheme.Style.GLASS -> if (night) 0xFF0E0B1E.toInt() else 0xFF2A2350.toInt()
            else -> ink
        }
        lead.color = leadColor; lead.strokeWidth = 1.5f
        for (cell in cells) canvas.drawPath(cell.path, lead)
        canvas.restore()
        lead.strokeWidth = 2.2f
        canvas.drawRoundRect(frame, radius, radius, lead)
        canvas.restore()
    }

    /** Цвета осколка: тёмный край и светлая середина. В ч/б — оттенки серого. */
    private fun colorsFor(cell: Cell, i: Int): Pair<Int, Int> = when (style) {
        RtTheme.Style.GLASS -> cell.colors
        else -> {
            val tones = intArrayOf(R.color.rt_book_d, R.color.rt_book_m, R.color.rt_book_l)
            val a = RtColors.c(context, tones[i % 3])
            a to RtColors.mix(a, Color.WHITE, 0.45f)
        }
    }

    companion object {
        const val CELLS = 30
        private const val COLS = 6
        private const val ROWS = 5

        private fun c(v: Long) = v.toInt()

        /** Перелив: у каждого осколка свой оттенок (тёмный край, светлая середина). */
        private val HOLO = listOf(
            c(0xFF7B5CFF) to c(0xFF2EC4E6), c(0xFFFF5CA8) to c(0xFFFFC94A), c(0xFF2EC4E6) to c(0xFF6BE3C8),
            c(0xFFB04BE0) to c(0xFFFF8AC2), c(0xFF5B8CFF) to c(0xFFC9A8FF), c(0xFFFF7A59) to c(0xFFFFD36B),
            c(0xFF29C9A8) to c(0xFF7B5CFF), c(0xFFFF5C8A) to c(0xFF9C84FF)
        )
    }
}

/** Круглая розетка-витраж у заголовка «дней подряд»: значит «30+», одна, сколько бы ни было. */
class RosetteView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val glass = RtTheme.isGlass(context)
    private val night = RtColors.isNight(context)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lead = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }

    init { contentDescription = "больше тридцати дней подряд" }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = (26 * d).toInt()
        setMeasuredDimension(resolveSize(s, widthMeasureSpec), resolveSize(s, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - 1.4f * d
        val petals = 8
        val ink = RtColors.c(context, R.color.rt_ink)
        val colors = if (glass) intArrayOf(
            0xFF7B5CFF.toInt(), 0xFFFF5CA8.toInt(), 0xFF2EC4E6.toInt(), 0xFFFFC94A.toInt(),
            0xFFB04BE0.toInt(), 0xFF29C9A8.toInt(), 0xFFFF7A59.toInt(), 0xFF5B8CFF.toInt()
        ) else IntArray(petals) { RtColors.withAlpha(ink, if (it % 2 == 0) 0.55f else 0.3f) }
        // Лепестки — сектора кольца
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        for (i in 0 until petals) {
            fill.shader = RadialGradient(cx, cy, r, intArrayOf(Color.WHITE, colors[i]), floatArrayOf(0.25f, 1f), Shader.TileMode.CLAMP)
            canvas.drawArc(oval, i * 45f - 90f, 45f, true, fill)
        }
        fill.shader = null
        // Серединка
        fill.color = if (glass) 0xFFFFD36B.toInt() else ink
        canvas.drawCircle(cx, cy, r * 0.3f, fill)
        lead.color = if (glass) (if (night) 0xFF0E0B1E.toInt() else 0xFF2A2350.toInt()) else ink
        lead.strokeWidth = 1.1f * d
        for (i in 0 until petals) {
            val a = Math.toRadians(i * 45.0 - 90.0)
            canvas.drawLine(cx + r * 0.3f * cos(a).toFloat(), cy + r * 0.3f * sin(a).toFloat(),
                cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), lead)
        }
        canvas.drawCircle(cx, cy, r * 0.3f, lead)
        canvas.drawCircle(cx, cy, r * 0.66f, lead)
        lead.strokeWidth = 1.6f * d
        canvas.drawCircle(cx, cy, r, lead)
    }
}
