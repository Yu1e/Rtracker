package com.readingtracker.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import com.readingtracker.R
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme

/**
 * Кольцо «сегодня за книгой», минуты в центре.
 * Цель: когда цель взята, кольцо замыкается и идёт второй круг внутри.
 * Час: кольцо заполняется за 60 минут и начинается заново, полные часы — точками сверху.
 */
class GoalRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val glass = RtTheme.isGlass(context)
    private var minutes = 0L
    private var goal = 60
    private var hourMode = false

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = if (glass) Paint.Cap.ROUND else Paint.Cap.BUTT
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.semibold(context); textAlign = Paint.Align.CENTER }
    private val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular(context); textAlign = Paint.Align.CENTER; textSize = 11f }

    fun set(minutes: Long, goal: Int, hourMode: Boolean) {
        this.minutes = minutes
        this.hourMode = hourMode
        this.goal = if (hourMode) 60 else goal.coerceAtLeast(1)
        contentDescription = if (hourMode) "сегодня $minutes мин" else "сегодня $minutes мин из $goal"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((84 * d).toInt(), widthMeasureSpec), resolveSize((84 * d).toInt(), heightMeasureSpec))
    }

    private fun col(id: Int) = RtColors.c(context, id)

    /** Стекло: прогресс переливается от лилового к розовому и бирюзе. */
    private fun progressPaint(cx: Float, cy: Float, base: Int) {
        if (!glass) { ring.shader = null; ring.color = base; return }
        val g = SweepGradient(cx, cy, intArrayOf(0xFF7B5CFF.toInt(), 0xFFFF5CA8.toInt(), 0xFF2EC4E6.toInt(), 0xFF7B5CFF.toInt()), null)
        g.setLocalMatrix(Matrix().apply { setRotate(-90f, cx, cy) })
        ring.shader = g
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.scale(d, d)
        val cx = 42f; val cy = 42f
        val outer = RectF(cx - 35f, cy - 35f, cx + 35f, cy + 35f)
        ring.strokeWidth = 9f
        ring.shader = null
        ring.color = col(R.color.rt_ring_track)
        canvas.drawOval(outer, ring)
        progressPaint(cx, cy, col(R.color.rt_ring_prog))
        if (hourMode) {
            val inHour = minutes % 60
            val sweep = if (minutes > 0 && inHour == 0L) 360f else inHour / 60f * 360f
            if (sweep > 0f) canvas.drawArc(outer, -90f, sweep, false, ring)
            // Полные часы — точки над числом
            val hours = (minutes / 60).toInt().coerceAtMost(8)
            if (hours > 0) {
                dot.color = col(R.color.rt_ring_fg)
                val step = 6f
                var x = cx - step * (hours - 1) / 2f
                repeat(hours) { canvas.drawCircle(x, cy - 21f, 1.8f, dot); x += step }
            }
        } else if (minutes >= goal) {
            canvas.drawOval(outer, ring)
            val extra = ((minutes - goal) % goal).toFloat() / goal
            if (minutes > goal) {
                val inner = RectF(cx - 26f, cy - 26f, cx + 26f, cy + 26f)
                ring.strokeWidth = 5f
                ring.shader = null
                ring.color = col(R.color.rt_ring_track2)
                canvas.drawOval(inner, ring)
                ring.color = col(R.color.rt_ring_prog2)
                canvas.drawArc(inner, -90f, if (extra == 0f) 360f else extra * 360f, false, ring)
            }
        } else if (minutes > 0) {
            canvas.drawArc(outer, -90f, minutes.toFloat() / goal * 360f, false, ring)
        }
        ring.shader = null
        num.color = col(R.color.rt_ring_fg)
        num.textSize = if (minutes > 99) 24f else 26f
        canvas.drawText(minutes.toString(), cx, cy + 6f, num)
        unit.color = col(R.color.rt_ring_sub)
        canvas.drawText("мин", cx, cy + 19f, unit)
        canvas.restore()
    }
}
