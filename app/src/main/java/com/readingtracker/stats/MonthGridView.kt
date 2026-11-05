package com.readingtracker.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.readingtracker.R
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme
import java.time.LocalDate

/**
 * Календарь текущего месяца по дням недели (пн…вс), как в обычном календаре: если первое число —
 * воскресенье, в верхнем ряду одна клетка справа; нижний ряд может быть неполным. Без подписей. В прошедших днях и сегодня —
 * минуты чтения (не часы), в будущих — число месяца. Цвет клетки — по минутам. Тыц по прошедшему
 * дню — onDaySelected с датой и минутами.
 */
class MonthGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var data: Map<LocalDate, Long> = emptyMap()
    private var today: LocalDate = LocalDate.now()
    private var selected: LocalDate? = null
    var onDaySelected: ((LocalDate, Long) -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    private val first get() = today.withDayOfMonth(1)
    private val days get() = today.lengthOfMonth()
    /** Сколько пустых мест перед первым числом: понедельник — 0, воскресенье — 6. */
    private val offset get() = first.dayOfWeek.value - 1

    fun setData(dayMinutes: Map<LocalDate, Long>, today: LocalDate) {
        data = dayMinutes; this.today = today
        requestLayout(); invalidate()
    }

    private fun level(m: Long): Int = when {
        m <= 0 -> 0
        m < 15 -> 1
        m < 30 -> 2
        m < 60 -> 3
        else -> 4
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val gap = 5 * d
        val cell = (w - gap * 6) / 7f
        val rows = (offset + days + 6) / 7
        setMeasuredDimension(w, (rows * cell + (rows - 1) * gap).toInt() + 2)
    }

    override fun onDraw(canvas: Canvas) {
        val gap = 5 * d
        val cell = (width - gap * 6) / 7f
        val ink = RtColors.c(context, R.color.rt_ink)
        val ink2 = RtColors.c(context, R.color.rt_ink2)
        val ink3 = RtColors.c(context, R.color.rt_ink3)
        val eink = RtTheme.isEink(context)
        val radius = if (eink) 3 * d else 8 * d
        for (i in 0 until days) {
            val day = first.plusDays(i.toLong())
            val col = (offset + i) % 7; val row = (offset + i) / 7
            val x = col * (cell + gap); val y = row * (cell + gap)
            rect.set(x + 0.75f * d, y + 0.75f * d, x + cell - 0.75f * d, y + cell - 0.75f * d)
            val future = day.isAfter(today)
            val m = data[day] ?: 0L
            val lv = if (future) -1 else level(m)
            line.pathEffect = null
            val label: String
            if (lv < 0) {
                // Будущий день: пунктир и число месяца
                line.color = RtColors.withAlpha(ink3, 0.55f); line.strokeWidth = 1.3f * d
                line.pathEffect = DashPathEffect(floatArrayOf(3 * d, 2.5f * d), 0f)
                canvas.drawRoundRect(rect, radius, radius, line)
                text.color = ink3
                label = day.dayOfMonth.toString()
            } else if (lv == 0) {
                // Прошедший день без чтения: только контур и 0
                line.color = RtColors.withAlpha(ink2, if (eink) 1f else 0.45f); line.strokeWidth = (if (eink) 1.5f else 1.3f) * d
                canvas.drawRoundRect(rect, radius, radius, line)
                text.color = ink2
                label = "0"
            } else {
                val c = RtTheme.heat(context, lv)
                fill.color = c
                canvas.drawRoundRect(rect, radius, radius, fill)
                text.color = RtColors.onColor(c)
                label = m.toString()
            }
            if (day == selected) {
                line.pathEffect = null; line.color = ink; line.strokeWidth = 2 * d
                canvas.drawRoundRect(rect, radius, radius, line)
            }
            text.textSize = (if (label.length >= 4) 10.5f else 13f) * d
            text.typeface = if (lv > 0) Fonts.semibold(context) else Fonts.regular(context)
            canvas.drawText(label, rect.centerX(), rect.centerY() - (text.ascent() + text.descent()) / 2, text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val gap = 5 * d
            val cell = (width - gap * 6) / 7f
            val col = (event.x / (cell + gap)).toInt()
            val row = (event.y / (cell + gap)).toInt()
            val i = row * 7 + col - offset
            if (col in 0..6 && i in 0 until days) {
                val day = first.plusDays(i.toLong())
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
