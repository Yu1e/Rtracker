package com.readingtracker.ui.book

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.readingtracker.R
import com.readingtracker.data.Book
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme

/**
 * Плитка статуса в карточке книги: картинка и подпись. Выбранная обведена рамкой.
 * Картинки пока нейтральные: читаю — раскрытая книга; дочитана — улыбается;
 * отложена — лежит и спит; брошена — в корзине.
 */
class StatusTileView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val style = RtTheme.style(context)

    var status: String = Book.STATUS_READING
        set(value) { field = value; contentDescription = label(); invalidate() }

    var checked = false
        set(value) {
            field = value
            contentDescription = label() + if (value) ", выбрано" else ""
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 16f }

    init {
        isClickable = true
        isFocusable = true
    }

    private fun label() = when (status) {
        Book.STATUS_FINISHED -> "дочитана"
        Book.STATUS_PAUSED -> "отложена"
        Book.STATUS_ABANDONED -> "брошена"
        else -> "читаю"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((112 * d).toInt(), heightMeasureSpec))
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (style != RtTheme.Style.EINK) animate().scaleX(if (isPressed) 0.96f else 1f).scaleY(if (isPressed) 0.96f else 1f).setDuration(100).start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.scale(d, d)
        val w = width / d; val h = height / d
        val ink = RtColors.c(context, R.color.rt_ink)
        val accent = if (style == RtTheme.Style.EINK) Color.BLACK else RtTheme.accent(context)

        // Плитка
        val tile = RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
        fill.color = when (style) {
            RtTheme.Style.GLASS -> if (RtColors.isNight(context)) 0x24FFFFFF else 0xA6FFFFFF.toInt()
            RtTheme.Style.EINK -> Color.WHITE
            RtTheme.Style.MONO -> RtColors.c(context, R.color.rt_tile)
        }
        canvas.drawRoundRect(tile, 16f, 16f, fill)
        line.color = if (checked) accent else RtColors.c(context, R.color.rt_tile_stroke)
        line.strokeWidth = if (checked) 3f else if (style == RtTheme.Style.EINK) 1.5f else 1f
        canvas.drawRoundRect(tile, 16f, 16f, line)

        // Картинка: поле 60×50 по центру над подписью
        val k = 1.15f
        canvas.save()
        canvas.translate(w / 2 - 30f * k, 12f)
        canvas.scale(k, k)
        val soft = if (style == RtTheme.Style.EINK) Color.WHITE else RtColors.withAlpha(accent, 0.22f)
        picture(canvas, ink, soft, accent)
        canvas.restore()

        text.color = if (checked) accent else ink
        text.typeface = if (checked) Fonts.semibold(context) else Fonts.regular(context)
        canvas.drawText(label(), w / 2, h - 16f, text)
        canvas.restore()
    }

    private fun picture(canvas: Canvas, ink: Int, soft: Int, accent: Int) {
        line.color = ink; line.strokeWidth = 1.8f
        when (status) {
            Book.STATUS_FINISHED -> {
                // Закрытая книга стоит и улыбается
                val cover = RectF(19f, 4f, 43f, 44f)
                fill.color = soft; canvas.drawRoundRect(cover, 3f, 3f, fill)
                canvas.drawRoundRect(cover, 3f, 3f, line)
                line.strokeWidth = 1.2f; canvas.drawLine(23f, 5f, 23f, 43f, line)
                fill.color = ink
                canvas.drawCircle(28.5f, 20f, 1.7f, fill); canvas.drawCircle(36.5f, 20f, 1.7f, fill)
                line.strokeWidth = 1.6f
                canvas.drawArc(RectF(28f, 21f, 37f, 29f), 20f, 140f, false, line)
            }
            Book.STATUS_PAUSED -> {
                // Лежит горизонтально и спит: глаза закрыты
                val cover = RectF(8f, 26f, 52f, 42f)
                fill.color = soft; canvas.drawRoundRect(cover, 3f, 3f, fill)
                canvas.drawRoundRect(cover, 3f, 3f, line)
                line.strokeWidth = 1.2f; canvas.drawLine(9f, 30f, 51f, 30f, line)
                line.strokeWidth = 1.5f
                canvas.drawArc(RectF(22f, 32f, 28f, 37f), 20f, 140f, false, line)
                canvas.drawArc(RectF(32f, 32f, 38f, 37f), 20f, 140f, false, line)
            }
            Book.STATUS_ABANDONED -> {
                // Книга наискосок в корзине
                canvas.save(); canvas.rotate(-18f, 30f, 26f)
                val book = RectF(22f, 6f, 38f, 30f)
                fill.color = soft; canvas.drawRoundRect(book, 2f, 2f, fill)
                canvas.drawRoundRect(book, 2f, 2f, line)
                canvas.restore()
                val basket = Path().apply { moveTo(10f, 26f); lineTo(50f, 26f); lineTo(46f, 46f); lineTo(14f, 46f); close() }
                fill.color = if (style == RtTheme.Style.EINK) Color.WHITE else RtColors.mix(soft, Color.WHITE, 0.3f)
                canvas.drawPath(basket, fill)
                canvas.drawPath(basket, line)
                line.strokeWidth = 1f
                for (i in 1..4) { val x = 10f + i * 8f; canvas.drawLine(x, 26f, x - (x - 30f) * 0.1f, 46f, line) }
                canvas.drawLine(11.3f, 33f, 48.7f, 33f, line)
                canvas.drawLine(12.7f, 40f, 47.3f, 40f, line)
                line.strokeWidth = 2.2f
                canvas.drawLine(8f, 26f, 52f, 26f, line)
            }
            else -> {
                // Раскрытая книга, из середины выглядывает ляссе
                val left = Path().apply { moveTo(30f, 12f); quadTo(20f, 7f, 6f, 9f); lineTo(6f, 40f); quadTo(20f, 38f, 30f, 42f); close() }
                val right = Path().apply { moveTo(30f, 12f); quadTo(40f, 7f, 54f, 9f); lineTo(54f, 40f); quadTo(40f, 38f, 30f, 42f); close() }
                fill.color = soft
                canvas.drawPath(left, fill); canvas.drawPath(right, fill)
                canvas.drawPath(left, line); canvas.drawPath(right, line)
                line.strokeWidth = 1f
                for (i in 0 until 3) {
                    val y = 18f + i * 6f
                    canvas.drawLine(11f, y, 25f, y + 1f, line)
                    canvas.drawLine(35f, y + 1f, 49f, y, line)
                }
                line.color = accent; line.strokeWidth = 2.6f
                canvas.drawLine(31.5f, 41f, 33f, 49f, line)
            }
        }
    }
}
