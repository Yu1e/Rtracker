package com.readingtracker.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import com.readingtracker.R
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import kotlin.random.Random

/**
 * Книги «сейчас читаю» стопкой (до трёх). Сверху — та, что читалась последней:
 * на ней автор, название и манжетка с общим временем и шкалой дней.
 * У каждой книги своё цветное ляссе; раскладка стопки и изгибы лент меняются при каждом открытии.
 * Всё рисуется в dp; одна книга — обложка 150×225 в точке (OX, OY).
 */
class BookCoverView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Brick { READ, EMPTY, TODAY_READ, TODAY_EMPTY }

    data class Item(
        val id: Long,
        val author: String,
        val title: String,
        val palette: RibbonPalette,
        val pattern: RibbonPattern
    )

    private class Placed(
        val item: Item, val x: Float, val y: Float, val rot: Float, val s: Float,
        val coverRes: Int, val route: RibbonRoute
    )

    private val d = resources.displayMetrics.density

    private var items: List<Item> = emptyList()   // сверху стопки — первая
    private var emptyText: String = ""
    private var timeText: String = ""
    private var bricks: List<Brick> = emptyList()
    /** Доля прочитанных страниц (0..1) или null, если страниц трекер не знает. */
    private var progress: Float? = null
    private var shuffleSeed: Long = System.nanoTime()
    private var placed: List<Placed> = emptyList()  // снизу вверх
    private var titleLayout: StaticLayout? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Fonts.regular(context) }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 19f; typeface = Fonts.semibold(context) }
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Fonts.semibold(context) }

    private fun col(id: Int) = RtColors.c(context, id)

    fun setBooks(items: List<Item>, timeText: String, bricks: List<Brick>, progress: Float? = null) {
        this.items = items.take(3)
        this.timeText = timeText
        this.bricks = bricks
        this.progress = progress?.coerceIn(0f, 1f)
        titleLayout = null
        arrange()
        val top = this.items.firstOrNull()
        contentDescription = buildString {
            if (top != null) append(listOf(top.author, top.title, timeText).filter { it.isNotBlank() }.joinToString(", "))
            this@BookCoverView.progress?.let { append(", прочитано: ${(it * 100).toInt()} %") }
            if (this@BookCoverView.items.size > 1) append(". Ещё читается: ${this@BookCoverView.items.size - 1}")
        }
        invalidate()
    }

    fun setEmpty(text: String) {
        items = emptyList()
        emptyText = text
        timeText = ""
        bricks = emptyList()
        progress = null
        titleLayout = null
        placed = emptyList()
        contentDescription = text
        invalidate()
    }

    /** Новая раскладка стопки и новые изгибы лент. */
    fun reshuffle() {
        shuffleSeed = System.nanoTime()
        arrange()
        invalidate()
    }

    // ── Раскладка ─────────────────────────────────────────────────────────────

    private class Slot(val x: Float, val y: Float, val rot: Float, val s: Float, val kinds: List<TailKind>)
    private class Template(val under: List<Slot>, val top: Slot)

    private val templates = listOf(
        // Уступом: нижние выглядывают сверху справа и снизу слева
        Template(
            listOf(Slot(30f, 0f, 4f, 0.84f, listOf(TailKind.UP)), Slot(0f, 40f, -3f, 0.80f, listOf(TailKind.DOWN))),
            Slot(14f, 22f, 0f, 0.82f, listOf(TailKind.DOWN, TailKind.UP, TailKind.SIDE))
        ),
        // Башня: нижние выглядывают сверху
        Template(
            listOf(Slot(4f, 0f, -2f, 0.83f, listOf(TailKind.UP)), Slot(20f, 12f, 3f, 0.80f, listOf(TailKind.UP, TailKind.SIDE))),
            Slot(8f, 28f, 0f, 0.82f, listOf(TailKind.DOWN, TailKind.SIDE))
        ),
        // Развал: нижние разъехались в стороны
        Template(
            listOf(Slot(40f, 26f, 8f, 0.80f, listOf(TailKind.DOWN)), Slot(-4f, 10f, -5f, 0.84f, listOf(TailKind.UP))),
            Slot(12f, 18f, 1f, 0.82f, listOf(TailKind.DOWN, TailKind.UP))
        )
    )

    private val underCovers = listOf(R.color.rt_cover2, R.color.rt_cover3, R.color.rt_cover4)

    private fun arrange() {
        val rnd = Random(shuffleSeed)
        val n = items.size
        if (n == 0) { placed = emptyList(); return }
        if (n == 1) {
            placed = listOf(Placed(items[0], 0f, 0f, 0f, 1f, R.color.rt_cover, RibbonRoute.random(rnd, TailKind.DOWN)))
            return
        }
        val t = templates[rnd.nextInt(templates.size)]
        val under = if (n == 3) t.under else listOf(t.under[rnd.nextInt(t.under.size)])
        val slots = under + t.top
        fun j(a: Float) = (rnd.nextFloat() * 2 - 1) * a
        // Хвосты: у каждой книги свой вид из доступных; все одинаковые — только если иначе нельзя
        val kinds = slots.map { it.kinds[rnd.nextInt(it.kinds.size)] }.toMutableList()
        if (kinds.distinct().size == 1) {
            val i = slots.indexOfFirst { s -> s.kinds.any { it != kinds[0] } }
            if (i >= 0) kinds[i] = slots[i].kinds.first { it != kinds[0] }
        }
        val covers = underCovers.shuffled(rnd)
        // items: первая — сверху; slots: снизу вверх
        val order = items.reversed()
        placed = slots.mapIndexed { i, s ->
            val isTop = i == slots.size - 1
            Placed(
                order[i], s.x + j(3f), s.y + j(3f), s.rot + (if (isTop) j(0.8f) else j(1.5f)), s.s,
                if (isTop) R.color.rt_cover else covers[i % covers.size],
                RibbonRoute.random(rnd, kinds[i])
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((W * d).toInt(), widthMeasureSpec), resolveSize((H * d).toInt(), heightMeasureSpec))
    }

    private fun buildTitle(text: String, hasBook: Boolean): StaticLayout {
        titlePaint.color = col(if (hasBook) R.color.rt_ink else R.color.rt_ink2)
        titlePaint.typeface = if (hasBook) Fonts.semibold(context) else Fonts.regular(context)
        val width = (150f - 20f - 12f).toInt()
        // Длинное слово не рвём посередине: уменьшаем кегль, пока самое длинное слово не влезет
        titlePaint.textSize = 19f
        val longest = text.split(Regex("\\s+")).maxByOrNull { titlePaint.measureText(it) } ?: ""
        while (titlePaint.textSize > 13f && titlePaint.measureText(longest) > width) titlePaint.textSize -= 1f
        return StaticLayout.Builder.obtain(text, 0, text.length, titlePaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setMaxLines(if (hasBook) 4 else 5)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(d, d)
        canvas.translate(OX, OY)
        if (placed.isEmpty()) {
            drawBook(canvas, null, R.color.rt_cover, null, true)
        } else {
            for ((i, p) in placed.withIndex()) {
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.rot)
                canvas.scale(p.s, p.s)
                drawBook(canvas, p.item, p.coverRes, p.route, i == placed.size - 1)
                canvas.restore()
            }
        }
        canvas.restore()
    }

    /** Одна книга в своих координатах: обложка 150×225 в точке (0, 0). */
    private fun drawBook(canvas: Canvas, item: Item?, coverRes: Int, route: RibbonRoute?, isTop: Boolean) {
        // Задняя крышка
        fill.color = col(R.color.rt_board)
        val back = RectF(9f, 8.1f, 159f, 233.1f)
        canvas.drawRoundRect(back, 1.5f, 1.5f, fill)
        stroke.color = col(R.color.rt_cover_stroke); stroke.strokeWidth = 1.3f
        canvas.drawRoundRect(back, 1.5f, 1.5f, stroke)

        // Блок страниц: справа и снизу, углы прямые
        fill.color = col(R.color.rt_pages)
        val pages = Path().apply {
            moveTo(148f, 2.4f); lineTo(156f, 9.6f); lineTo(156f, 230.6f)
            lineTo(8.5f, 230.6f); lineTo(0.5f, 223.4f); lineTo(148f, 223.4f); close()
        }
        canvas.drawPath(pages, fill)
        val ts = floatArrayOf(0.6f, 1.8f, 3.0f, 4.2f, 5.4f, 6.6f, 7.8f)
        for ((i, t) in ts.withIndex()) {
            val last = i == ts.size - 1
            stroke.color = col(if (last) R.color.rt_cover_stroke else R.color.rt_page_line)
            stroke.strokeWidth = if (last) 0.9f else 0.6f
            val p = Path().apply {
                moveTo(148f + t, 2f + 0.9f * t); lineTo(148f + t, 223f + 0.9f * t); lineTo(0.5f + t, 223f + 0.9f * t)
            }
            canvas.drawPath(p, stroke)
        }

        // Торец корешка снизу слева: скрепляет обе крышки
        val spine = Path().apply {
            moveTo(0.8f, 222.5f)
            cubicTo(-1.6f, 226.5f, 1.2f, 231.6f, 9.5f, 233.1f)
            lineTo(11.5f, 230.4f)
            cubicTo(6f, 229.4f, 3.4f, 227.4f, 3.2f, 222.5f)
            close()
        }
        fill.color = col(R.color.rt_spine); canvas.drawPath(spine, fill)
        stroke.color = col(R.color.rt_cover_stroke); stroke.strokeWidth = 1.1f; stroke.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(spine, stroke)

        // Ляссе: под обложкой сверху, между страницами снизу, сверху или сбоку
        if (item != null && route != null) RibbonPainter(item.palette, item.pattern).draw(canvas, route)

        // Обложка
        val cover = RectF(0.75f, 0.75f, 149.25f, 224.25f)
        val coverColor = col(coverRes)
        fill.color = coverColor; canvas.drawRoundRect(cover, 3f, 3f, fill)
        stroke.color = col(R.color.rt_cover_stroke); stroke.strokeWidth = 1.5f
        canvas.drawRoundRect(cover, 3f, 3f, stroke)
        fill.color = if (coverRes == R.color.rt_cover) col(R.color.rt_cover_hinge)
            else RtColors.withAlpha(if (RtColors.luminance(coverColor) < 0.25) Color.WHITE else Color.BLACK, 0.16f)
        canvas.drawRect(10f, 1.5f, 11f, 223.5f, fill)

        if (!isTop) return

        // Автор и название
        var y = 14f
        val author = item?.author ?: ""
        if (author.isNotBlank()) {
            authorPaint.color = col(R.color.rt_ink2)
            val a = TextUtils.ellipsize(author, authorPaint, 118f, TextUtils.TruncateAt.END).toString()
            canvas.drawText(a, 20f, y - authorPaint.ascent(), authorPaint)
            y += authorPaint.descent() - authorPaint.ascent() + 6f
        }
        val tl = titleLayout ?: buildTitle(item?.title ?: emptyText, item != null).also { titleLayout = it }
        canvas.save(); canvas.translate(20f, y); tl.draw(canvas); canvas.restore()

        if (item != null) drawBand(canvas)
    }

    /** Манжетка: узкая бумажная полоса, края загнуты под обложку, слева чуть отстаёт. */
    private fun drawBand(canvas: Canvas) {
        val top = 138f; val bottom = 202f
        fill.color = col(R.color.rt_band_shadow)
        canvas.drawRect(-2.5f, bottom, 150.5f, bottom + 1.5f, fill)
        fill.color = col(R.color.rt_band)
        canvas.drawRect(-2.5f, top, 150.5f, bottom, fill)
        stroke.color = col(R.color.rt_band_stroke); stroke.strokeWidth = 1f; stroke.strokeJoin = Paint.Join.MITER
        canvas.drawRect(-2f, top + 0.5f, 150.5f, bottom - 0.5f, stroke)
        // Загиб справа
        val fold = Path().apply {
            moveTo(150.5f, top); lineTo(152.4f, top + 1.7f); lineTo(152.4f, bottom + 1.7f); lineTo(150.5f, bottom); close()
        }
        fill.color = col(R.color.rt_band_fold); canvas.drawPath(fold, fill)
        stroke.strokeWidth = 0.8f; canvas.drawPath(fold, stroke)

        // Линейки, время, шкала дней
        val left = 20f; val right = 140f
        fill.color = col(R.color.rt_band_rule)
        canvas.drawRect(left, top + 7.5f, right, top + 8.5f, fill)
        canvas.drawRect(left, bottom - 8.5f, right, bottom - 7.5f, fill)
        timePaint.color = col(R.color.rt_band_text)
        val timeTop = top + 14.5f
        canvas.drawText(timeText, left, timeTop - timePaint.ascent() * 0.86f, timePaint)
        drawProgress(canvas, left + timePaint.measureText(timeText), right, timeTop + 9.5f)
        drawBricks(canvas, left, right, top + 40.5f)
    }

    /** Маленькая круговая диаграмма прочитанного справа от времени — если трекер знает страницы. */
    private fun drawProgress(canvas: Canvas, textEnd: Float, right: Float, cy: Float) {
        val p = progress ?: return
        val r = 8.5f
        val cx = right - r
        if (textEnd + 6f > cx - r) return
        stroke.color = col(R.color.rt_band_rule); stroke.strokeWidth = 1.4f
        canvas.drawCircle(cx, cy, r, stroke)
        if (p > 0f) {
            fill.color = col(R.color.rt_band_text)
            canvas.drawArc(RectF(cx - r + 1.6f, cy - r + 1.6f, cx + r - 1.6f, cy + r - 1.6f), -90f, 360f * p, true, fill)
        }
        stroke.color = col(R.color.rt_band_text); stroke.strokeWidth = 1f
        canvas.drawCircle(cx, cy, r, stroke)
    }

    private fun drawBricks(canvas: Canvas, left: Float, right: Float, top: Float) {
        if (bricks.isEmpty()) return
        val n = bricks.size
        val gap = if (n > 10) 2f else 3f
        val w = if (n <= 4) 30f else (right - left - gap * (n - 1)) / n
        val h = 9f
        var x = left
        for (b in bricks) {
            val r = RectF(x, top, x + w, top + h)
            when (b) {
                Brick.READ -> { fill.color = col(R.color.rt_brick); canvas.drawRoundRect(r, 1.5f, 1.5f, fill) }
                Brick.EMPTY -> {
                    stroke.color = col(R.color.rt_brick_empty); stroke.strokeWidth = 1f
                    canvas.drawRoundRect(RectF(r.left + 0.5f, r.top + 0.5f, r.right - 0.5f, r.bottom - 0.5f), 1.5f, 1.5f, stroke)
                }
                Brick.TODAY_READ -> {
                    fill.color = col(R.color.rt_brick_today); canvas.drawRoundRect(r, 1.5f, 1.5f, fill)
                    stroke.color = col(R.color.rt_brick_today); stroke.strokeWidth = 1.2f
                    canvas.drawRoundRect(RectF(r.left - 1.8f, r.top - 1.8f, r.right + 1.8f, r.bottom + 1.8f), 3f, 3f, stroke)
                }
                Brick.TODAY_EMPTY -> {
                    stroke.color = col(R.color.rt_brick_today); stroke.strokeWidth = 1.5f
                    canvas.drawRoundRect(RectF(r.left + 0.75f, r.top + 0.75f, r.right - 0.75f, r.bottom - 0.75f), 1.5f, 1.5f, stroke)
                }
            }
            x += w + gap
        }
    }

    companion object {
        /** Сдвиг стопки внутри вида: слева запас под торец корешка, сверху — под арки и хвосты ляссе. */
        const val OX = 6f
        const val OY = 30f
        const val W = 176f
        const val H = 292f
    }
}
