package com.readingtracker.stats

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.readingtracker.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Календарь-тепловая карта: столбцы — недели, строки — дни (пн…вс).
 * Чем дольше читала в день, тем темнее клетка (в тёмной теме — светлее).
 * Нажатие на клетку сообщает дату и минуты.
 */
class HeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onDaySelected: ((LocalDate, Long) -> Unit)? = null

    private var data: Map<LocalDate, Long> = emptyMap()
    private var today: LocalDate = LocalDate.now()
    private var weeks = 18
    private var selected: LocalDate? = null

    private val dp = resources.displayMetrics.density
    private val levels = intArrayOf(R.color.heat_0, R.color.heat_1, R.color.heat_2, R.color.heat_3, R.color.heat_4)
        .map { ContextCompat.getColor(context, it) }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * dp
        color = resolveColor(android.R.attr.textColorPrimary)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = resolveColor(android.R.attr.textColorSecondary)
        textSize = 10 * resources.displayMetrics.scaledDensity
    }
    private val rect = RectF()
    private val labelW = 18 * dp

    private fun resolveColor(attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
    }

    fun setData(dayMinutes: Map<LocalDate, Long>, today: LocalDate) {
        data = dayMinutes; this.today = today
        invalidate()
    }

    /** Ступень цвета: 0 — не читала; 1–4 — до 15 мин, до 30, до 60, больше часа. */
    private fun level(m: Long): Int = when {
        m <= 0 -> 0
        m < 15 -> 1
        m < 30 -> 2
        m < 60 -> 3
        else -> 4
    }

    private val firstMonday: LocalDate
        get() = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        // Сколько недель влезает при клетке ~16dp
        weeks = (((w - labelW) / (16 * dp)).toInt()).coerceIn(8, 53)
        val cell = (w - labelW) / weeks
        setMeasuredDimension(w, (cell * 7).toInt() + 2)
    }

    override fun onDraw(canvas: Canvas) {
        val cell = (width - labelW) / weeks
        val gap = 2 * dp
        val start = firstMonday
        val names = listOf("пн", "", "ср", "", "пт", "", "")
        for (r in 0 until 7) {
            if (names[r].isNotEmpty()) canvas.drawText(names[r], 0f, cell * r + cell * 0.75f, labelPaint)
        }
        for (w in 0 until weeks) {
            for (r in 0 until 7) {
                val day = start.plusDays((w * 7 + r).toLong())
                if (day.isAfter(today)) continue
                val x = labelW + w * cell
                val y = r * cell
                rect.set(x + gap / 2, y + gap / 2, x + cell - gap / 2, y + cell - gap / 2)
                cellPaint.color = levels[level(data[day] ?: 0)]
                canvas.drawRoundRect(rect, 3 * dp, 3 * dp, cellPaint)
                if (day == selected) canvas.drawRoundRect(rect, 3 * dp, 3 * dp, ringPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val cell = (width - labelW) / weeks
            val w = ((event.x - labelW) / cell).toInt()
            val r = (event.y / cell).toInt()
            if (w in 0 until weeks && r in 0 until 7) {
                val day = firstMonday.plusDays((w * 7 + r).toLong())
                if (!day.isAfter(today)) {
                    selected = day
                    invalidate()
                    onDaySelected?.invoke(day, data[day] ?: 0)
                }
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
