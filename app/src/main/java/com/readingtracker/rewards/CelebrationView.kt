package com.readingtracker.rewards

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import com.readingtracker.R
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme
import com.readingtracker.ui.home.RibbonPalette
import com.readingtracker.ui.home.Ribbons
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Праздник поверх экрана: эффект и карточка с поводом посередине.
 * Ленты-ляссе за дочитанную книгу, бумажное конфетти, мыльные пузыри, салют из ромбиков,
 * листопад из букв, искры. На чернилах — только карточка, без движения. Касание закрывает.
 */
@SuppressLint("ViewConstructor")
class CelebrationView(
    context: Context,
    private val a: Achievement,
    private val onDone: (CelebrationView) -> Unit
) : View(context) {

    private val d = resources.displayMetrics.density
    private val style = RtTheme.style(context)
    private val motion = RtTheme.motion(context)
    private val night = RtColors.isNight(context)
    private val rnd = Random(System.nanoTime())
    private val start = SystemClock.uptimeMillis()
    private var endAt = start + if (motion) 3900L else 4200L
    private var done = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    /** Цвета эффекта: в стекле — цвета оформления, в ч/б — цвета лент-ляссе. */
    private val colors: IntArray = when (style) {
        RtTheme.Style.GLASS -> intArrayOf(0xFF7B5CFF.toInt(), 0xFFFF5CA8.toInt(), 0xFF2EC4E6.toInt(),
            0xFFFFC94A.toInt(), 0xFF6BE38E.toInt(), 0xFFFF8A3D.toInt())
        else -> Ribbons.palettes.flatMap { listOf(it.base, it.stripe) }.toIntArray()
    }
    private fun color() = colors[rnd.nextInt(colors.size)]

    init {
        isClickable = true
        contentDescription = listOf(a.label, a.big, a.small).filter { it.isNotBlank() }.joinToString(", ")
    }

    // ── Частицы ───────────────────────────────────────────────────────────────

    private class P(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var rot: Float, val vrot: Float, val phase: Float, val size: Float, val color: Int,
        val t0: Float = 0f, val life: Float = 9f, val text: String = "", val extra: Float = 0f
    )

    private class Streamer(val x: Float, val speed: Float, val amp: Float, val wave: Float,
                           val phase: Float, val len: Float, val pal: RibbonPalette, val delay: Float)

    private var parts = mutableListOf<P>()
    private var streamers = listOf<Streamer>()
    private var lastFrame = 0L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (motion && parts.isEmpty() && streamers.isEmpty()) seed(w.toFloat(), h.toFloat())
    }

    private fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    private fun seed(w: Float, h: Float) {
        when (a.effect) {
            Effect.CONFETTI -> repeat(130) { i ->
                val left = i % 2 == 0
                val ang = Math.toRadians((if (left) r(-80f, -45f) else r(-135f, -100f)).toDouble())
                val sp = r(900f, 1600f) * d
                parts += P(if (left) 0f else w, h * 0.92f, (cos(ang) * sp).toFloat(), (sin(ang) * sp).toFloat(),
                    r(0f, 360f), r(-400f, 400f), r(0f, 6.28f), r(6f, 11f) * d, color(), extra = r(4f, 9f))
            }
            Effect.BUBBLES -> repeat(30) {
                parts += P(r(0.05f, 0.95f) * w, h + r(20f, 500f) * d, 0f, -r(130f, 260f) * d,
                    0f, 0f, r(0f, 6.28f), r(10f, 34f) * d, color(), extra = r(0.12f, 0.5f) * h)
            }
            Effect.FIREWORK -> repeat(6) { k ->
                val cx = r(0.15f, 0.85f) * w; val cy = r(0.12f, 0.55f) * h
                val c1 = color(); val c2 = color()
                repeat(38) {
                    val ang = r(0f, 6.28f); val sp = r(220f, 560f) * d
                    parts += P(cx, cy, cos(ang) * sp, sin(ang) * sp, ang * 57.3f, 0f, 0f,
                        r(4f, 7f) * d, if (it % 3 == 0) c2 else c1, t0 = k * 0.38f, life = r(1.0f, 1.5f))
                }
            }
            Effect.LEAVES -> {
                val letters = "абвгдеёжзийклмнопрстуфхцчшщэюяАБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЭЮЯ"
                repeat(38) {
                    parts += P(r(0f, 1f) * w, -r(30f, h * 0.9f / d) * d, 0f, r(110f, 190f) * d,
                        0f, 0f, r(0f, 6.28f), r(18f, 40f) * d, color(),
                        text = letters[rnd.nextInt(letters.length)].toString(), extra = r(20f, 55f) * d)
                }
            }
            Effect.SPARKLES -> repeat(46) {
                val ang = r(0f, 6.28f); val rad = r(70f, 230f) * d
                parts += P(w / 2 + cos(ang) * rad, h * 0.45f + sin(ang) * rad * 0.8f, 0f, -r(10f, 30f) * d,
                    r(0f, 45f), 0f, 0f, r(5f, 13f) * d, color(), t0 = r(0f, 2.6f), life = r(0.6f, 1.2f))
            }
            Effect.RIBBONS -> {
                val own = a.bookId?.let { Ribbons.paletteOf(context, it) }
                streamers = List(10) { i ->
                    Streamer(r(0.04f, 0.96f) * w, r(260f, 420f) * d, r(18f, 42f) * d, r(55f, 95f) * d,
                        r(0f, 6.28f), r(140f, 240f) * d,
                        if (own != null && i % 2 == 0) own else Ribbons.palettes[rnd.nextInt(Ribbons.palettes.size)],
                        r(0f, 0.9f))
                }
                // Немного конфетти вдогонку
                repeat(40) {
                    parts += P(r(0f, 1f) * w, -r(10f, 300f) * d, r(-40f, 40f) * d, r(120f, 220f) * d,
                        r(0f, 360f), r(-300f, 300f), r(0f, 6.28f), r(5f, 9f) * d, color(), extra = r(4f, 8f))
                }
            }
        }
    }

    // ── Отрисовка ─────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val t = (now - start) / 1000f
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceIn(0f, 0.05f)
        lastFrame = now
        val total = (endAt - start) / 1000f
        // Общая прозрачность: в конце всё тает
        val fade = ((endAt - now) / 450f).coerceIn(0f, 1f)

        if (motion) {
            canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (fade * 255).toInt())
            when (a.effect) {
                Effect.CONFETTI -> confetti(canvas, dt, t)
                Effect.BUBBLES -> bubbles(canvas, dt, t)
                Effect.FIREWORK -> firework(canvas, dt, t)
                Effect.LEAVES -> leaves(canvas, dt, t)
                Effect.SPARKLES -> sparkles(canvas, t)
                Effect.RIBBONS -> { ribbons(canvas, t); confetti(canvas, dt, t, gravity = 0.25f) }
            }
            canvas.restore()
        }
        card(canvas, t, total, fade)

        if (now >= endAt) finish()
        else if (motion) postInvalidateOnAnimation()
        else postInvalidateDelayed(endAt - now)
    }

    private fun confetti(canvas: Canvas, dt: Float, t: Float, gravity: Float = 1f) {
        val g = 1500f * d * gravity
        val drag = exp(-1.6f * dt)
        for (p in parts) {
            p.vx *= drag; p.vy = p.vy * drag + g * dt
            // Бумажки порхают, падая
            if (p.vy > 0) p.vx += sin(t * 4f + p.phase) * 260f * d * dt
            p.x += p.vx * dt; p.y += p.vy * dt
            p.rot += p.vrot * dt
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rot)
            canvas.scale(1f, cos(t * p.extra + p.phase))
            paint.shader = null; paint.color = p.color
            canvas.drawRect(-p.size / 2, -p.size * 0.3f, p.size / 2, p.size * 0.3f, paint)
            canvas.restore()
        }
    }

    private fun bubbles(canvas: Canvas, dt: Float, t: Float) {
        for (p in parts) {
            val popped = p.t0 > 0f
            if (!popped) {
                p.y += p.vy * dt
                val x = p.x + sin(t * 2.2f + p.phase) * 14f * d
                if (p.y < p.extra) {
                    // Лопнул: время лопания хранится в rot
                    p.rot = t
                    p.x = x
                    parts.set(parts.indexOf(p), P(x, p.y, 0f, 0f, t, 0f, p.phase, p.size, p.color, t0 = t))
                    continue
                }
                val rad = p.size
                paint.shader = RadialGradient(x - rad * 0.3f, p.y - rad * 0.3f, rad * 1.3f,
                    intArrayOf(0x14FFFFFF, 0x0AFFFFFF, RtColors.withAlpha(p.color, 0.22f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(x, p.y, rad, paint)
                stroke.strokeWidth = max(1.2f * d, rad * 0.06f)
                stroke.shader = SweepGradient(x, p.y, intArrayOf(0xCCFF5CA8.toInt(), 0xCC2EC4E6.toInt(), 0xCCFFE066.toInt(),
                    0xCC7B5CFF.toInt(), 0xCCFF5CA8.toInt()), null)
                canvas.drawCircle(x, p.y, rad, stroke)
                stroke.shader = null
                paint.shader = null; paint.color = 0xD9FFFFFF.toInt()
                canvas.drawCircle(x - rad * 0.38f, p.y - rad * 0.38f, rad * 0.16f, paint)
            } else {
                // Брызги лопнувшего пузыря
                val k = (t - p.t0) / 0.35f
                if (k > 1f) continue
                paint.shader = null; paint.color = RtColors.withAlpha(p.color, 1f - k)
                for (i in 0 until 8) {
                    val ang = p.phase + i * PI.toFloat() / 4
                    val rr = p.size * (0.8f + k * 0.9f)
                    canvas.drawCircle(p.x + cos(ang) * rr, p.y + sin(ang) * rr, 2.2f * d * (1f - k * 0.6f), paint)
                }
            }
        }
    }

    private fun diamond(canvas: Canvas, x: Float, y: Float, s: Float, rot: Float) {
        canvas.save(); canvas.translate(x, y); canvas.rotate(rot)
        path.reset(); path.moveTo(0f, -s); path.lineTo(s * 0.6f, 0f); path.lineTo(0f, s); path.lineTo(-s * 0.6f, 0f); path.close()
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun firework(canvas: Canvas, dt: Float, t: Float) {
        val drag = exp(-2.2f * dt)
        val g = 260f * d
        for ((i, p) in parts.withIndex()) {
            val age = t - p.t0
            if (age < 0f || age > p.life) continue
            p.vx *= drag; p.vy = p.vy * drag + g * dt
            p.x += p.vx * dt; p.y += p.vy * dt
            val k = age / p.life
            paint.shader = null
            // Вспышка в начале залпа
            if (age < 0.12f && i % 38 == 0) {
                paint.color = RtColors.withAlpha(Color.WHITE, 0.5f * (1 - age / 0.12f))
                canvas.drawCircle(p.x, p.y, 40f * d * (age / 0.12f), paint)
            }
            paint.color = RtColors.withAlpha(p.color, 1f - k * k)
            diamond(canvas, p.x, p.y, p.size * (1f - k * 0.5f), p.rot + 90f)
            // Хвостик
            paint.color = RtColors.withAlpha(p.color, (1f - k) * 0.35f)
            canvas.drawCircle(p.x - p.vx * 0.03f, p.y - p.vy * 0.03f, p.size * 0.3f, paint)
        }
    }

    private val letterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Fonts.semibold(context) }

    private fun leaves(canvas: Canvas, dt: Float, t: Float) {
        for (p in parts) {
            p.y += p.vy * dt
            val x = p.x + sin(t * 1.6f + p.phase) * p.extra
            val rot = sin(t * 1.9f + p.phase) * 38f
            canvas.save(); canvas.translate(x, p.y); canvas.rotate(rot)
            letterPaint.color = RtColors.withAlpha(p.color, 0.9f)
            letterPaint.textSize = p.size
            canvas.drawText(p.text, 0f, p.size * 0.35f, letterPaint)
            canvas.restore()
        }
    }

    private fun sparkles(canvas: Canvas, t: Float) {
        for (p in parts) {
            val age = t - p.t0
            if (age < 0f || age > p.life) continue
            val s = sin(PI.toFloat() * age / p.life) * p.size
            val y = p.y + p.vy * age
            paint.shader = null; paint.color = p.color
            canvas.save(); canvas.translate(p.x, y); canvas.rotate(p.rot)
            path.reset()
            path.moveTo(0f, -s); path.quadTo(0f, 0f, s, 0f); path.quadTo(0f, 0f, 0f, s)
            path.quadTo(0f, 0f, -s, 0f); path.quadTo(0f, 0f, 0f, -s); path.close()
            canvas.drawPath(path, paint)
            paint.color = RtColors.withAlpha(Color.WHITE, 0.8f)
            canvas.drawCircle(0f, 0f, s * 0.18f, paint)
            canvas.restore()
        }
    }

    /** Ленты-ляссе падают волной и переворачиваются: видна то лицевая сторона, то изнанка. */
    private fun ribbons(canvas: Canvas, t: Float) {
        val hw = 4.5f * d
        for (s in streamers) {
            val tt = t - s.delay
            if (tt < 0f) continue
            val head = -s.len + tt * s.speed
            val n = 40
            val step = s.len / n
            var px = 0f; var py = 0f; var pw = 0f
            for (i in 0..n) {
                val y = head - i * step
                val x = s.x + s.amp * sin(y / s.wave + s.phase + tt * 2.4f)
                val twist = cos(i * 0.16f + tt * 3.2f + s.phase)
                val w = hw * max(abs(twist), 0.18f)
                if (i > 0) {
                    paint.shader = null
                    paint.color = if (twist >= 0) s.pal.base else s.pal.back
                    path.reset()
                    path.moveTo(px - pw, py); path.lineTo(px + pw, py); path.lineTo(x + w, y); path.lineTo(x - w, y); path.close()
                    canvas.drawPath(path, paint)
                    paint.color = if (twist >= 0) s.pal.stripe else s.pal.stripeBack
                    val sw = 0.22f
                    path.reset()
                    path.moveTo(px - pw * sw, py); path.lineTo(px + pw * sw, py); path.lineTo(x + w * sw, y); path.lineTo(x - w * sw, y); path.close()
                    canvas.drawPath(path, paint)
                }
                px = x; py = y; pw = w
            }
            // Ласточкин хвост на конце
            paint.color = s.pal.base
            path.reset()
            path.moveTo(px - pw, py); path.lineTo(px + pw, py); path.lineTo(px + pw, py - 9f * d)
            path.lineTo(px, py - 4f * d); path.lineTo(px - pw, py - 9f * d); path.close()
            canvas.drawPath(path, paint)
        }
    }

    // ── Карточка с поводом ────────────────────────────────────────────────────

    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Fonts.regular(context) }
    private val bigPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.semibold(context) }
    private val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Fonts.regular(context) }
    private var bigLayout: StaticLayout? = null

    private fun card(canvas: Canvas, t: Float, total: Float, fade: Float) {
        val eink = style == RtTheme.Style.EINK
        val appear = if (motion) (t / 0.35f).coerceIn(0f, 1f) else 1f
        // Пружинка при появлении
        val scale = if (motion) 0.86f + 0.14f * (1f - (1f - appear) * (1f - appear)) + 0.03f * sin(appear * PI.toFloat()) else 1f
        val alpha = min(appear, fade)

        val k = if (eink) 1.3f else 1f
        val cw = min(320f * d * k, width - 48f * d)
        val ink = if (eink) Color.BLACK else RtColors.c(context, R.color.rt_ink)
        val ink2 = if (eink) Color.BLACK else RtColors.c(context, R.color.rt_ink2)
        labelPaint.textSize = 15f * d * k; labelPaint.color = ink2
        smallPaint.textSize = 13f * d * k; smallPaint.color = ink2
        val isTitle = a.bookId != null || a.big.length > 6
        bigPaint.textSize = (if (isTitle) 26f else 60f) * d * k
        bigPaint.color = ink
        val bl = bigLayout ?: StaticLayout.Builder.obtain(a.big, 0, a.big.length, bigPaint, (cw - 40f * d).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false).build().also { bigLayout = it }
        val pad = 22f * d
        val labelH = labelPaint.textSize * 1.4f
        val smallH = if (a.small.isNotBlank()) smallPaint.textSize * 1.6f else 0f
        val ch = pad + labelH + 6f * d + bl.height + smallH + pad
        val cx = width / 2f; val cy = height * 0.45f
        val rect = RectF(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2)

        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        val ra = (alpha * 255).toInt()
        val radius = if (eink) 12f * d else 26f * d
        when (style) {
            RtTheme.Style.GLASS -> {
                paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
                    if (night) 0x99231F3D.toInt() else 0xE6FFFFFF.toInt(), if (night) 0x80140F2A.toInt() else 0xB3FFFFFF.toInt(), Shader.TileMode.CLAMP)
                paint.alpha = ra
                canvas.drawRoundRect(rect, radius, radius, paint)
                stroke.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, 0xFFFFFFFF.toInt(), 0x40FFFFFF, Shader.TileMode.CLAMP)
                stroke.strokeWidth = 1.2f * d; stroke.alpha = ra
                canvas.drawRoundRect(rect, radius, radius, stroke)
                stroke.shader = null
            }
            RtTheme.Style.EINK -> {
                paint.shader = null; paint.color = Color.WHITE; paint.alpha = ra
                canvas.drawRoundRect(rect, radius, radius, paint)
                stroke.color = Color.BLACK; stroke.strokeWidth = 3f * d; stroke.alpha = ra
                canvas.drawRoundRect(rect, radius, radius, stroke)
                // Ромбики сверху и снизу вместо движения
                paint.color = Color.BLACK; paint.alpha = ra
                for (i in -2..2) {
                    diamond(canvas, cx + i * 22f * d, rect.top + 11f * d, 4f * d, 0f)
                    diamond(canvas, cx + i * 22f * d, rect.bottom - 11f * d, 4f * d, 0f)
                }
            }
            RtTheme.Style.MONO -> {
                paint.shader = null; paint.color = RtColors.c(context, R.color.rt_tile); paint.alpha = ra
                canvas.drawRoundRect(rect, radius, radius, paint)
                stroke.color = RtColors.c(context, R.color.rt_ink); stroke.strokeWidth = 1.5f * d; stroke.alpha = ra
                canvas.drawRoundRect(rect, radius, radius, stroke)
            }
        }
        paint.shader = null
        var y = rect.top + pad
        labelPaint.alpha = ra
        canvas.drawText(a.label, cx, y - labelPaint.ascent(), labelPaint)
        y += labelH + 6f * d
        bigPaint.alpha = ra
        canvas.save(); canvas.translate(rect.left + 20f * d, y); bl.draw(canvas); canvas.restore()
        y += bl.height
        if (a.small.isNotBlank()) {
            smallPaint.alpha = ra
            val s = TextUtils.ellipsize(a.small, smallPaint, cw - 40f * d, TextUtils.TruncateAt.END).toString()
            canvas.drawText(s, cx, y + smallH * 0.75f, smallPaint)
        }
        canvas.restore()
    }

    // ── Закрытие ──────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val now = SystemClock.uptimeMillis()
            if (endAt - now > 300) { endAt = now + 300; invalidate() }
        }
        return true
    }

    private fun finish() {
        if (done) return
        done = true
        post { onDone(this) }
    }
}
