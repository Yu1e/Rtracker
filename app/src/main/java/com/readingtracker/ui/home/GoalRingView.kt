package com.readingtracker.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.readingtracker.R
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors

/** Кольцо цели дня: минуты в центре; когда цель взята, кольцо замыкается и идёт второй круг. */
class GoalRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var minutes = 0L
    private var goal = 60

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.semibold(context); textAlign = Paint.Align.CENTER }
    private val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular(context); textAlign = Paint.Align.CENTER; textSize = 11f }

    fun set(minutes: Long, goal: Int) {
        this.minutes = minutes
        this.goal = goal.coerceAtLeast(1)
        contentDescription = "сегодня $minutes мин из $goal"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((84 * d).toInt(), widthMeasureSpec), resolveSize((84 * d).toInt(), heightMeasureSpec))
    }

    private fun col(id: Int) = RtColors.c(context, id)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.scale(d, d)
        val cx = 42f; val cy = 42f
        val outer = RectF(cx - 35f, cy - 35f, cx + 35f, cy + 35f)
        ring.strokeWidth = 9f
        ring.color = col(R.color.rt_ring_track)
        canvas.drawOval(outer, ring)
        ring.color = col(R.color.rt_ring_prog)
        if (minutes >= goal) {
            canvas.drawOval(outer, ring)
            val extra = ((minutes - goal) % goal).toFloat() / goal
            if (minutes > goal) {
                val inner = RectF(cx - 26f, cy - 26f, cx + 26f, cy + 26f)
                ring.strokeWidth = 5f
                ring.color = col(R.color.rt_ring_track2)
                canvas.drawOval(inner, ring)
                ring.color = col(R.color.rt_ring_prog2)
                canvas.drawArc(inner, -90f, if (extra == 0f) 360f else extra * 360f, false, ring)
            }
        } else if (minutes > 0) {
            canvas.drawArc(outer, -90f, minutes.toFloat() / goal * 360f, false, ring)
        }
        num.color = col(R.color.rt_ring_fg)
        num.textSize = if (minutes > 99) 24f else 26f
        canvas.drawText(minutes.toString(), cx, cy + 4f, num)
        unit.color = col(R.color.rt_ring_sub)
        canvas.drawText("мин", cx, cy + 17f, unit)
        canvas.restore()
    }
}
