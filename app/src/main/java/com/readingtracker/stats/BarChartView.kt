package com.readingtracker.stats

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.readingtracker.R

/**
 * Простой столбчатый график одного ряда. Нажатие на столбик показывает подпись со значением.
 * Тонкие столбики со скруглённым верхом, зазор между ними, бледная базовая линия.
 */
class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var labels: List<String> = emptyList()
    private var values: List<Float> = emptyList()
    private var format: (Float) -> String = { it.toInt().toString() }
    private var labelEvery = 1
    private var selected = -1
    private var highlightLast = false

    private val dp = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.chart_bar) }
    private val gridPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.chart_grid); strokeWidth = 1 * dp
    }
    private val textSecondary = resolveColor(android.R.attr.textColorSecondary)
    private val textPrimary = resolveColor(android.R.attr.textColorPrimary)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textSecondary; textSize = 10 * resources.displayMetrics.scaledDensity; textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPrimary; textSize = 12 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val rect = RectF()
    private val path = Path()

    private fun resolveColor(attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
    }

    fun setData(
        labels: List<String>, values: List<Float>,
        labelEvery: Int = 1, highlightLast: Boolean = false,
        format: (Float) -> String = { it.toInt().toString() }
    ) {
        this.labels = labels; this.values = values; this.labelEvery = labelEvery
        this.format = format; this.highlightLast = highlightLast
        selected = if (highlightLast && values.isNotEmpty()) values.lastIndex else -1
        contentDescription = labels.zip(values).joinToString(", ") { "${it.first}: ${format(it.second)}" }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (150 * dp).toInt())
    }

    private val topPad get() = 22 * dp
    private val bottomPad get() = 18 * dp

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val n = values.size
        val chartH = height - topPad - bottomPad
        val slot = width.toFloat() / n
        val gap = 2 * dp
        val barW = (slot - gap).coerceAtMost(28 * dp)
        val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val baseY = topPad + chartH

        canvas.drawLine(0f, baseY, width.toFloat(), baseY, gridPaint)

        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            val h = chartH * (values[i] / max)
            if (values[i] > 0) {
                rect.set(cx - barW / 2, baseY - h.coerceAtLeast(2 * dp), cx + barW / 2, baseY)
                val r = minOf(4 * dp, barW / 2, rect.height())
                // Скруглён только верх, низ стоит на базовой линии
                path.reset()
                path.addRoundRect(rect, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
                barPaint.alpha = if (selected == -1 || selected == i) 255 else 140
                canvas.drawPath(path, barPaint)
            }
            if (i % labelEvery == 0 || i == n - 1) {
                canvas.drawText(labels[i], cx, height - 4 * dp, labelPaint)
            }
        }
        if (selected in 0 until n) {
            val cx = (slot * selected + slot / 2).coerceIn(30 * dp, width - 30 * dp)
            val h = chartH * (values[selected] / max)
            val y = (baseY - h - 6 * dp).coerceAtLeast(14 * dp)
            canvas.drawText(format(values[selected]), cx, y, valuePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (values.isEmpty()) return false
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(event.action == MotionEvent.ACTION_MOVE)
            val i = (event.x / (width.toFloat() / values.size)).toInt().coerceIn(0, values.lastIndex)
            if (i != selected) { selected = i; invalidate() }
            return true
        }
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
