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

/**
 * Дни подряд как счётные кубики: сотня — квадрат, десяток — палочка, единица — кубик.
 * Недостающее до рекорда дорисовано призрачными кубиками.
 */
class StreakBlocksView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var streak = 0
    private var record = 0

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(2f, 1.5f), 0f)
    }
    private val solidStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }

    private val big get() = maxOf(streak, record) >= 100
    private val cube get() = if (big) 4f else 7f
    private val rodH get() = if (big) 22f else 31f
    private val gridGap get() = if (big) 2f else 1f

    fun set(streak: Int, record: Int) {
        this.streak = streak
        this.record = maxOf(record, streak)
        requestLayout(); invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((rodH * d).toInt() + 1, heightMeasureSpec))
    }

    private fun col(id: Int) = RtColors.c(context, id)

    private fun split(n: Int) = Triple(n / 100, (n / 10) % 10, n % 10)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.scale(d, d)
        val ink = col(R.color.rt_ink)
        val ghostColor = col(if (big) R.color.rt_block_ghost_big else R.color.rt_block_ghost)
        val s = split(streak); val g = split(maxOf(record - streak, 0))
        var x = 0f
        val bottom = rodH

        fun block(r: RectF, ghost: Boolean, radius: Float) {
            if (!ghost) { fill.color = ink; canvas.drawRoundRect(r, radius, radius, fill) }
            else if (big) { solidStroke.color = ghostColor; canvas.drawRoundRect(inset(r), radius, radius, solidStroke) }
            else { dashed.color = ghostColor; canvas.drawRoundRect(inset(r), radius, radius, dashed) }
        }

        repeat(s.first) { block(RectF(x, bottom - rodH, x + rodH, bottom), false, 2f); x += rodH + 3f }
        repeat(g.first) { block(RectF(x, bottom - rodH, x + rodH, bottom), true, 2f); x += rodH + 3f }
        repeat(s.second) { block(RectF(x, bottom - rodH, x + cube, bottom), false, 1f); x += cube + 3f }
        repeat(g.second) { block(RectF(x, bottom - rodH, x + cube, bottom), true, 1f); x += cube + 3f }
        // Кубики столбцами по четыре, сверху вниз
        val total = s.third + g.third
        for (i in 0 until total) {
            val c = i / 4; val r = i % 4
            val cx = x + c * (cube + gridGap)
            val cy = bottom - rodH + r * (cube + gridGap)
            block(RectF(cx, cy, cx + cube, cy + cube), i >= s.third, 0f)
        }
        canvas.restore()
    }

    private fun inset(r: RectF) = RectF(r.left + 0.5f, r.top + 0.5f, r.right - 0.5f, r.bottom - 0.5f)
}
