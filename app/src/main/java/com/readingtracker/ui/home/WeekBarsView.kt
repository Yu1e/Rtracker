package com.readingtracker.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.readingtracker.R
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * «минут чтения по дням»: семь столбиков за последние семь дней, сегодня справа.
 * Числа в минутах: у высоких внутри, у низких сверху. Корона над рекордом недели.
 * Сегодняшний столбик и подпись дня обведены рамкой.
 */
class WeekBarsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var values = IntArray(7)
    private var today = 6
    private var days = arrayOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.semibold(context); textAlign = Paint.Align.CENTER }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular(context); textAlign = Paint.Align.CENTER; textSize = 12f }
    private val crown = Path().apply {
        // 24×18 → 20×15
        val s = 20f / 24f
        moveTo(3 * s, 15 * s); lineTo(5 * s, 4 * s); lineTo(10 * s, 9 * s); lineTo(12 * s, 2 * s)
        lineTo(14 * s, 9 * s); lineTo(19 * s, 4 * s); lineTo(21 * s, 15 * s); close()
    }

    private val glass = com.readingtracker.ui.RtTheme.isGlass(context)

    /** Корона над лучшим днём; выключается настройкой «только статистика». */
    var crownOn = true

    /** labels — подписи дней по порядку столбиков (скользящие 7 дней, сегодня последний). */
    fun set(values: IntArray, todayIndex: Int, labels: List<String>? = null) {
        if (labels != null && labels.size == 7) days = labels.toTypedArray()
        this.values = values.copyOf(7)
        this.today = todayIndex
        contentDescription = days.indices.joinToString(", ") { "${days[it]} ${this.values[it]} мин" }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, resolveSize((133 * d).toInt(), heightMeasureSpec))
    }

    private fun col(id: Int) = RtColors.c(context, id)

    /** Рамка сегодняшнего дня: в стекле фиолетовая, в ч/б и на чернилах — цвет текста. */
    private fun todayColor(): Int =
        if (glass) (if (RtColors.isNight(context)) 0xFFC9B6FF.toInt() else 0xFF6343F0.toInt()) else col(R.color.rt_ink)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.scale(d, d)
        val wDp = width / d; val hDp = height / d
        val gap = 5f
        // Запас по краям, чтобы рамка сегодняшнего дня у крайнего столбика не обрезалась
        val side = 1.5f
        val colW = (wDp - 2 * side - gap * 6) / 7f
        // Столбик уже колонки: рамка сегодняшнего дня отходит от него сбоку так же, как сверху (5)
        val max = max(values.maxOrNull() ?: 0, 1)
        // Корона — лучшему дню недели, когда есть с чем сравнить: хотя бы два дня с чтением
        val best = if (crownOn && values.count { it > 0 } >= 2) values.indexOfFirst { it == values.maxOrNull() } else -1
        val labelH = 19f
        val barBottom = hDp - labelH - 8f - 2f

        for (i in 0 until 7) {
            val v = values[i]
            val x0 = side + i * (colW + gap)
            val cx = x0 + colW / 2
            val r = v / max.toFloat()
            val barH = if (v == 0) 3f else max(14f, (r * 72f).roundToInt().toFloat())
            val barTop = barBottom - barH
            val bar = RectF(x0 + BAR_INSET, barTop, x0 + colW - BAR_INSET, barBottom)
            val fillColor = when {
                v == 0 -> col(R.color.rt_bar0)
                r > 0.75f -> col(R.color.rt_bar4)
                r > 0.55f -> col(R.color.rt_bar3)
                r > 0.3f -> col(R.color.rt_bar2)
                else -> col(R.color.rt_bar1)
            }
            fill.color = fillColor
            // Стекло: столбик светлее кверху, как капля
            fill.shader = if (glass && v > 0) android.graphics.LinearGradient(0f, bar.top, 0f, bar.bottom,
                RtColors.mix(fillColor, android.graphics.Color.WHITE, 0.35f), fillColor, android.graphics.Shader.TileMode.CLAMP) else null
            canvas.drawRoundRect(bar, if (glass) 8f else 6f, if (glass) 8f else 6f, fill)
            fill.shader = null


            val text = v.toString()
            numPaint.textSize = if (v > 999) 12f else 15f
            val inside = barH >= 36f
            val numTop: Float
            if (inside) {
                numPaint.color = RtColors.onColor(fillColor).let { on ->
                    if (RtColors.contrast(on, fillColor) >= 3.0) on else col(R.color.rt_ink)
                }
                numTop = barTop + 6f
                canvas.drawText(text, cx, numTop - numPaint.ascent() * 0.78f, numPaint)
            } else {
                numPaint.color = if (v == 0) col(R.color.rt_bar_zero_text) else col(R.color.rt_ink)
                val textH = -numPaint.ascent() * 0.78f
                numTop = barTop - 7f - textH
                canvas.drawText(text, cx, numTop + textH, numPaint)
            }

            if (i == best) {
                val cTop = (if (inside) barTop else numTop) - 7f - 15f
                canvas.save(); canvas.translate(cx - 10f, cTop)
                stroke.color = col(R.color.rt_ink); stroke.strokeWidth = 1.5f; stroke.strokeJoin = Paint.Join.ROUND
                canvas.drawPath(crown, stroke)
                canvas.restore()
                stroke.strokeWidth = 1.5f
            }

            // Подпись дня
            dayPaint.color = col(R.color.rt_ink2)
            val labelCenterY = hDp - labelH / 2
            canvas.drawText(days[i], cx, labelCenterY - (dayPaint.ascent() + dayPaint.descent()) / 2, dayPaint)
            if (i == today) {
                // Одна рамка вокруг всего дня: число, столбик и подпись
                stroke.color = todayColor()
                stroke.strokeWidth = 1.8f
                val frameTop = min(numTop, barTop) - FRAME_PAD
                val fl = x0 + BAR_INSET - FRAME_PAD; val fr = x0 + colW - BAR_INSET + FRAME_PAD
                canvas.drawRoundRect(RectF(fl, frameTop, fr, hDp - 0.9f), 9f, 9f, stroke)
                stroke.strokeWidth = 1.5f
            }
        }
        canvas.restore()
    }

    private companion object {
        /** Отступ столбика от края колонки. */
        const val BAR_INSET = 5f
        /** Отступ рамки сегодняшнего дня от числа сверху и от столбика с боков. */
        const val FRAME_PAD = 5f
    }
}
