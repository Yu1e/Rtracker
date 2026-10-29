package com.readingtracker.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Значки нижнего меню (эскиз docs/design/09-ikonki-menyu.png, геометрия — docs/design/eskizy-0.9).
 * Рисуются в поле 28×28 (с запасом −2…30). Обычное состояние — контур цвета текста,
 * выбранное — заливка в цветах оформления.
 */
object NavIcons {
    enum class Kind { HOME, BOOKS, STATS, SETTINGS }

    /**
     * Цвета значка. colored — выбранный значок в стекле (радуга, пастельные заливки);
     * иначе заливки — оттенки цвета контура (fills пустые — значок прозрачный внутри).
     */
    class Colors(
        val stroke: Int,
        val f: Int, val f2: Int, val f3: Int,
        val side: Int,
        val base: Int,
        val colored: Boolean,
        val filled: Boolean,
        val width: Float
    )

    private const val VIO = 0xFF6343F0.toInt()

    /** Выбранный значок в стекле. */
    fun glassSelected(night: Boolean) = Colors(
        stroke = if (night) 0xFFB9A3FF.toInt() else VIO,
        f = 0xFFC9BCFF.toInt(), f2 = 0xFFFF9CC9.toInt(), f3 = 0xFF9FE8FF.toInt(),
        side = 0xFFE3DCFA.toInt(),
        base = if (night) 0xFFA58EFF.toInt() else 0xFF4A2FC4.toInt(),
        colored = true, filled = true, width = 1.7f
    )

    /** Контур без заливок: обычное состояние, а также выбранное на чернилах (белым по чёрной плашке). */
    fun outline(stroke: Int) = Colors(stroke, 0, 0, 0, 0, stroke, colored = false, filled = false, width = 1.6f)

    /** Выбранное в ч/б: заливки — оттенки цвета текста. */
    fun monoSelected(ink: Int) = Colors(
        stroke = ink,
        f = RtColors.withAlpha(ink, 0.45f), f2 = RtColors.withAlpha(ink, 0.25f), f3 = RtColors.withAlpha(ink, 0.12f),
        side = RtColors.withAlpha(ink, 0.08f), base = ink,
        colored = false, filled = true, width = 1.7f
    )

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    /** Рисует значок по центру (cx, cy) размером size px (на поле 32 единицы). */
    fun draw(canvas: Canvas, kind: Kind, cx: Float, cy: Float, size: Float, c: Colors) {
        val k = size / 32f
        // Свой слой: «пустые» грани стирают то, что за ними, и значок остаётся прозрачным внутри
        val layer = canvas.saveLayer(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, null)
        canvas.translate(cx - 14f * k, cy - 14f * k)
        canvas.scale(k, k)
        when (kind) {
            Kind.HOME -> home(canvas, c)
            Kind.BOOKS -> books(canvas, c)
            Kind.STATS -> stats(canvas, c)
            Kind.SETTINGS -> settings(canvas, c)
        }
        canvas.restoreToCount(layer)
    }

    /** Заливка грани: цветом, а если цвета нет — стиранием (то, что за гранью, не видно). */
    private fun face(canvas: Canvas, path: Path, color: Int) {
        if (color == 0) canvas.drawPath(path, clear)
        else { fill.color = color; canvas.drawPath(path, fill) }
    }

    private fun line(canvas: Canvas, path: Path, color: Int, w: Float) {
        stroke.color = color; stroke.strokeWidth = w
        canvas.drawPath(path, stroke)
    }

    // ── главная: радуга страниц ───────────────────────────────────────────────

    /** Страница-лента: от корешка круто вверх, потом отгибается наружу; к корешку сужается. */
    private fun band(sx: Float, sy: Float, ex: Float, ey: Float, hw: Float): Path {
        val dx = ex - sx; val dy = ey - sy
        val l = hypot(dx, dy); val nx = -dy / l; val ny = dx / l
        val etx = ex + nx * hw; val ety = ey + ny * hw
        val ebx = ex - nx * hw; val eby = ey - ny * hw
        val c1x = sx + dx * .18f + nx * hw * .6f; val c1y = ey + (sy - ey) * .42f
        val c2x = sx + dx * .3f - nx * hw * .4f; val c2y = ey + (sy - ey) * .3f
        return Path().apply {
            moveTo(sx, sy); quadTo(c1x, c1y, etx, ety); lineTo(ebx, eby); quadTo(c2x, c2y, sx, sy); close()
        }
    }

    private fun home(canvas: Canvas, c: Colors) {
        val left = arrayOf(1f to 16.4f, 2f to 12.2f, 4.2f to 8.4f, 7.8f to 5f)
        val right = arrayOf(27f to 16.4f, 26f to 12.2f, 23.8f to 8.4f, 20.2f to 5f)
        val alphas = floatArrayOf(.9f, .65f, .45f, .28f)
        val lc = intArrayOf(0xFF6343F0.toInt(), 0xFF8F74FF.toInt(), 0xFFC9A4FF.toInt(), 0xFFFF8AC2.toInt())
        val rc = intArrayOf(0xFF6343F0.toInt(), 0xFF8F74FF.toInt(), 0xFF9FC9FF.toInt(), 0xFF5FD3F0.toInt())
        for (i in 0 until 4) {
            fill.color = if (c.colored) lc[i] else RtColors.withAlpha(c.stroke, alphas[i])
            canvas.drawPath(band(13.4f, 18.4f, left[i].first, left[i].second, 1.25f), fill)
            fill.color = if (c.colored) rc[i] else RtColors.withAlpha(c.stroke, alphas[i])
            canvas.drawPath(band(14.6f, 18.4f, right[i].first, right[i].second, 1.25f), fill)
        }
        val cover = Path().apply {
            moveTo(0.6f, 19.4f); quadTo(8f, 17.6f, 12.8f, 20.6f); quadTo(14f, 21.8f, 15.2f, 20.6f); quadTo(20f, 17.6f, 27.4f, 19.4f)
        }
        line(canvas, cover, if (c.colored) c.base else c.stroke, 2f)
    }

    // ── книги: две стопки в 2,5D ──────────────────────────────────────────────

    private fun books(canvas: Canvas, c: Colors) {
        val dx = 2.4f; val dy = -2.2f
        val fills = intArrayOf(c.f, c.f2, c.f3, c.f)
        fun book(x: Float, y: Float, w: Float, h: Float, f: Int, top: Boolean) {
            val side = Path().apply { moveTo(x + w, y); lineTo(x + w + dx, y + dy); lineTo(x + w + dx, y + h + dy); lineTo(x + w, y + h); close() }
            face(canvas, side, if (c.filled) c.side else 0); line(canvas, side, c.stroke, 1f)
            val front = Path().apply { addRoundRect(RectF(x, y, x + w, y + h), .6f, .6f, Path.Direction.CW) }
            face(canvas, front, if (c.filled) f else 0); line(canvas, front, c.stroke, 1f)
            if (top) {
                val lid = Path().apply { moveTo(x, y); lineTo(x + dx, y + dy); lineTo(x + w + dx, y + dy); lineTo(x + w, y); close() }
                face(canvas, lid, if (c.filled) c.side else 0); line(canvas, lid, c.stroke, 1f)
            }
        }
        fun stack(x0: Float, base: Float, n: Int, shift: Int) {
            val jit = floatArrayOf(0f, .9f, -.5f, .6f); val widths = floatArrayOf(14f, 12.8f, 13.6f, 12.2f); val h = 4f
            var y = base
            for (i in 0 until n) {
                y -= h
                book(x0 + jit[i], y, widths[i], h, fills[(i + shift) % 4], i == n - 1)
            }
        }
        stack(10.6f, 22.6f, 4, 1)
        stack(1.6f, 27.4f, 3, 0)
    }

    // ── статистика: веер-диаграмма ────────────────────────────────────────────

    private fun stats(canvas: Canvas, c: Colors) {
        // Обычное: доли серые и прозрачные вперемежку; выбранное — три цветные доли и одна пустая
        val sectors = if (c.filled) listOf(Triple(0f, 130f, c.f), Triple(130f, 230f, c.f2), Triple(230f, 300f, c.f3), Triple(300f, 360f, 0))
            else listOf(Triple(0f, 130f, RtColors.withAlpha(c.stroke, .42f)), Triple(130f, 230f, 0),
                Triple(230f, 300f, RtColors.withAlpha(c.stroke, .2f)), Triple(300f, 360f, 0))
        for ((a0, a1, f) in sectors) {
            val r = if (a0 == 300f) 9.5f else 11f
            val p = Path().apply {
                moveTo(14f, 14f)
                arcTo(RectF(14f - r, 14f - r, 14f + r, 14f + r), a0 - 90f, a1 - a0, false)
                close()
            }
            if (f != 0) { fill.color = f; canvas.drawPath(p, fill) }
            line(canvas, p, c.stroke, 1.2f)
            stroke.color = if (c.filled) c.stroke else RtColors.withAlpha(c.stroke, .45f * Color.alpha(c.stroke) / 255f)
            stroke.strokeWidth = .55f
            var a = a0 + 18f
            while (a < a1) {
                val rad = Math.toRadians((a - 90f).toDouble())
                canvas.drawLine(14f, 14f, 14f + r * .9f * cos(rad).toFloat(), 14f + r * .9f * sin(rad).toFloat(), stroke)
                a += 22f
            }
        }
        fill.color = c.stroke
        canvas.drawCircle(14f, 14f, 1.6f, fill)
    }

    // ── настройки: шестерёнка с ляссе ─────────────────────────────────────────

    private fun settings(canvas: Canvas, c: Colors) {
        val gear = Path()
        val teeth = 8; val ro = 9f; val ri = 6.8f; val cx = 10.6f; val cy = 10.4f
        var first = true
        for (i in 0 until teeth) {
            val a = 2 * PI * i / teeth
            for ((r, da) in listOf(ri to -0.36, ro to -0.2, ro to 0.2, ri to 0.36)) {
                val x = cx + r * cos(a + da).toFloat(); val y = cy + r * sin(a + da).toFloat()
                if (first) { gear.moveTo(x, y); first = false } else gear.lineTo(x, y)
            }
        }
        gear.close()
        face(canvas, gear, if (c.filled) c.f else 0); line(canvas, gear, c.stroke, c.width)
        val hole = Path().apply { addCircle(cx, cy, 2.8f, Path.Direction.CW) }
        face(canvas, hole, if (c.colored) Color.WHITE else 0); line(canvas, hole, c.stroke, c.width)
        // Ляссе продето через центр, хвост ушёл вбок и расстелился, на конце ласточкин хвост
        val ribbon = Path().apply {
            moveTo(9.4f, 11.8f)
            cubicTo(8.6f, 17f, 10.6f, 21f, 14f, 21.6f)
            cubicTo(17f, 22f, 18.4f, 19.6f, 21.2f, 20.4f)
            cubicTo(23.2f, 21f, 23.8f, 23.4f, 26.4f, 23.2f)
            lineTo(27.6f, 25.6f); lineTo(25.4f, 25.2f); lineTo(24.8f, 27.4f)
            cubicTo(22.4f, 26.4f, 21.6f, 24f, 19.6f, 23.8f)
            cubicTo(17.6f, 23.6f, 16.4f, 26f, 13.2f, 25.4f)
            cubicTo(8.4f, 24.4f, 6.6f, 19f, 7.6f, 11.8f)
            close()
        }
        face(canvas, ribbon, if (c.filled) c.f2 else 0); line(canvas, ribbon, c.stroke, 1.2f)
    }
}
