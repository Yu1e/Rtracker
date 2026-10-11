package com.readingtracker.ui.home

import android.content.Context
import android.graphics.Canvas
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

/**
 * Книга «сейчас читаю»: обложка с условным объёмом (страницы, вторая крышка, торец корешка),
 * манжетка с общим временем и шкалой дней, цветное ляссе.
 * Всё рисуется в dp; обложка 150×225 лежит в точке (OX, OY) внутри вида.
 */
class BookCoverView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Brick { READ, EMPTY, TODAY_READ, TODAY_EMPTY }

    private val d = resources.displayMetrics.density

    private var author: String = ""
    private var title: String = ""
    private var timeText: String = ""
    private var bricks: List<Brick> = emptyList()
    private var hasBook = false
    private var palette: RibbonPalette = Ribbons.fallback
    private var pattern: RibbonPattern = RibbonPattern.DIAMONDS
    private var route: RibbonRoute = RibbonRoute.random()
    private var titleLayout: StaticLayout? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Fonts.regular(context) }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 19f; typeface = Fonts.semibold(context) }
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Fonts.semibold(context) }

    private fun col(id: Int) = RtColors.c(context, id)

    fun setBook(
        author: String, title: String, timeText: String, bricks: List<Brick>,
        palette: RibbonPalette, pattern: RibbonPattern
    ) {
        this.hasBook = true
        this.author = author
        this.title = title
        this.timeText = timeText
        this.bricks = bricks
        this.palette = palette
        this.pattern = pattern
        titleLayout = null
        contentDescription = listOf(author, title, timeText).filter { it.isNotBlank() }.joinToString(", ")
        invalidate()
    }

    fun setEmpty(text: String) {
        hasBook = false
        author = ""
        title = text
        timeText = ""
        bricks = emptyList()
        titleLayout = null
        contentDescription = text
        invalidate()
    }

    /** Новый изгиб ленты. */
    fun reshuffleRibbon() { route = RibbonRoute.random(); invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((W * d).toInt(), widthMeasureSpec), resolveSize((H * d).toInt(), heightMeasureSpec))
    }

    private fun buildTitle(): StaticLayout {
        titlePaint.color = col(if (hasBook) R.color.rt_ink else R.color.rt_ink2)
        if (!hasBook) titlePaint.typeface = Fonts.regular(context) else titlePaint.typeface = Fonts.semibold(context)
        val width = (150f - 20f - 12f).toInt()
        return StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, width)
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

        // Ляссе: под обложкой сверху, между страницами снизу
        if (hasBook) RibbonPainter(palette, pattern).draw(canvas, route)

        // Обложка
        val cover = RectF(0.75f, 0.75f, 149.25f, 224.25f)
        fill.color = col(R.color.rt_cover); canvas.drawRoundRect(cover, 3f, 3f, fill)
        stroke.color = col(R.color.rt_cover_stroke); stroke.strokeWidth = 1.5f
        canvas.drawRoundRect(cover, 3f, 3f, stroke)
        fill.color = col(R.color.rt_cover_hinge)
        canvas.drawRect(10f, 1.5f, 11f, 223.5f, fill)

        // Автор и название
        var y = 14f
        if (author.isNotBlank()) {
            authorPaint.color = col(R.color.rt_ink2)
            val a = TextUtils.ellipsize(author, authorPaint, 118f, TextUtils.TruncateAt.END).toString()
            canvas.drawText(a, 20f, y - authorPaint.ascent(), authorPaint)
            y += authorPaint.descent() - authorPaint.ascent() + 6f
        }
        val tl = titleLayout ?: buildTitle().also { titleLayout = it }
        canvas.save(); canvas.translate(20f, y); tl.draw(canvas); canvas.restore()

        if (hasBook) drawBand(canvas)

        canvas.restore()
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
        drawBricks(canvas, left, right, top + 40.5f)
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
        /** Сдвиг обложки внутри вида: слева запас под торец корешка, сверху — под арку ляссе. */
        const val OX = 6f
        const val OY = 30f
        const val W = 170f
        const val H = 292f
    }
}
