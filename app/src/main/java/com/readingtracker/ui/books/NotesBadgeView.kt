package com.readingtracker.ui.books

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.readingtracker.ui.RtTheme
import kotlin.math.hypot

/**
 * Значок «есть мои заметки» в списке книг: свиток, на нём перо — фиолетовое,
 * к кончику переливается радугой. На чернилах — чёрным по белому.
 * Рисуется в квадрате 24×24 условных единиц.
 */
class NotesBadgeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val eink = RtTheme.isEink(context)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    init { contentDescription = "есть заметки" }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = (28 * d).toInt()
        setMeasuredDimension(resolveSize(s, widthMeasureSpec), resolveSize(s, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val k = minOf(width, height) / 24f
        canvas.save()
        canvas.translate((width - 24 * k) / 2f, (height - 24 * k) / 2f)
        canvas.scale(k, k)

        val paper = if (eink) Color.WHITE else 0xFFF6EBD0.toInt()
        val roll = if (eink) Color.WHITE else 0xFFE4CF9F.toInt()
        val edge = if (eink) Color.BLACK else 0xFFA4844C.toInt()
        val script = if (eink) Color.BLACK else 0xFFC7AF80.toInt()

        // Свиток: лист и два валика
        line.strokeWidth = if (eink) 1.1f else 0.8f
        fill.shader = null
        fill.color = paper
        val sheet = RectF(4.5f, 4.5f, 16.5f, 20.5f)
        canvas.drawRect(sheet, fill)
        line.color = edge
        canvas.drawLine(sheet.left, sheet.top, sheet.left, sheet.bottom, line)
        canvas.drawLine(sheet.right, sheet.top, sheet.right, sheet.bottom, line)
        for (r in arrayOf(RectF(3f, 2.5f, 18f, 5.8f), RectF(3f, 19.2f, 18f, 22.5f))) {
            fill.color = roll
            canvas.drawRoundRect(r, 1.65f, 1.65f, fill)
            canvas.drawRoundRect(r, 1.65f, 1.65f, line)
            // Торец валика
            canvas.drawOval(RectF(r.right - 2.2f, r.top, r.right, r.bottom), line)
        }
        // Строчки на листе
        line.color = script; line.strokeWidth = 0.9f
        canvas.drawLine(6.5f, 9f, 14f, 9f, line)
        canvas.drawLine(6.5f, 11.8f, 14.5f, 11.8f, line)
        canvas.drawLine(6.5f, 14.6f, 11.5f, 14.6f, line)

        // Перо: кончик внизу слева на листе, опахало уходит вправо вверх
        val nx = 8.6f; val ny = 18.6f
        val tx = 22.6f; val ty = 1.4f
        val len = hypot(tx - nx, ty - ny)
        val ux = (tx - nx) / len; val uy = (ty - ny) / len
        val px = -uy; val py = ux   // нормаль: влево-вверх
        fun at(t: Float, off: Float) = floatArrayOf(nx + ux * len * t + px * off, ny + uy * len * t + py * off)

        val a = at(0.3f, 0f)
        val c1 = at(0.5f, 3.6f); val c2 = at(0.88f, 2.8f)
        val c3 = at(0.86f, -2.1f); val c4 = at(0.52f, -2.4f)
        val b = at(0.33f, 0f)
        path.reset()
        path.moveTo(a[0], a[1])
        path.cubicTo(c1[0], c1[1], c2[0], c2[1], tx, ty)
        path.cubicTo(c3[0], c3[1], c4[0], c4[1], b[0], b[1])
        path.close()
        if (eink) {
            fill.color = Color.BLACK
        } else {
            fill.shader = LinearGradient(a[0], a[1], tx, ty, VANE, floatArrayOf(0f, 0.35f, 0.62f, 0.82f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawPath(path, fill)
        fill.shader = null

        // Стержень и кончик
        line.color = if (eink) Color.BLACK else 0xFF3B2A8F.toInt(); line.strokeWidth = 0.75f
        val shaftEnd = at(0.94f, 0f)
        canvas.drawLine(nx, ny, shaftEnd[0], shaftEnd[1], line)
        fill.color = if (eink) Color.BLACK else 0xFF2A2350.toInt()
        val n1 = at(0.13f, 0.7f); val n2 = at(0.13f, -0.7f)
        path.reset()
        path.moveTo(nx - ux * 0.6f, ny - uy * 0.6f)
        path.lineTo(n1[0], n1[1]); path.lineTo(n2[0], n2[1]); path.close()
        canvas.drawPath(path, fill)

        canvas.restore()
    }

    private companion object {
        /** Фиолетовое перо, к кончику — радужный перелив. */
        val VANE = intArrayOf(
            0xFF5B2EE8.toInt(), 0xFF8F5CFF.toInt(), 0xFFD65CF0.toInt(), 0xFFFF8FB0.toInt(), 0xFF7FE3FF.toInt()
        )
    }
}
