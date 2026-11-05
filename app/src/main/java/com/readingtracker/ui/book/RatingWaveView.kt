package com.readingtracker.ui.book

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.readingtracker.R
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Оценка-волна: на левом краю незажжённая звезда — «без оценки», дальше десять звёзд по пологой
 * волне (сама линия волны не рисуется, её выстраивают звёзды): в левой половине провал вниз (плохие, красный → жёлтый), в правой подъём (хорошие,
 * мятный → голубой → синий → фиолетовый). Палец ведут слева направо где угодно над шкалой —
 * звёзды зажигаются по одной: вспыхивают, светятся, переливаются и рассыпают звёздочки.
 */
class RatingWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val eink = RtTheme.isEink(context)
    private val motion = RtTheme.motion(context)

    /** 0 — без оценки, 1…10. */
    var value = 0
        set(v) {
            val nv = v.coerceIn(0, 10)
            if (nv == field) return
            val old = field
            field = nv
            val now = SystemClock.uptimeMillis()
            // Новые зажжённые звёзды вспыхивают и рассыпают звёздочки
            for (i in old + 1..nv) { litAt[i] = now; burst(i, now) }
            contentDescription = if (nv == 0) "оценка: без оценки" else "оценка: $nv из 10"
            onChange?.invoke(nv)
            invalidate()
        }
    var onChange: ((Int) -> Unit)? = null

    private val litAt = LongArray(11)
    private val rnd = Random(System.nanoTime())

    private class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val r: Float,
                        val color: Int, val born: Long, val life: Long)
    private val sparks = ArrayList<Spark>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val star = Path()

    init {
        isClickable = true
        contentDescription = "оценка: без оценки"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((96 * d).toInt(), heightMeasureSpec))
    }

    // ── Геометрия ────────────────────────────────────────────────────────────

    private val pad get() = 16 * d
    /** Совсем пологая волна: неглубокий провал и такой же подъём. */
    private val amp get() = 7 * d
    private val mid get() = height / 2f + 4 * d

    private fun xAt(t: Float) = pad + t * (width - 2 * pad)
    private fun yAt(t: Float) = mid + amp * sin(2 * PI.toFloat() * t)
    /** Где на волне стоит звезда i (0 — «без оценки» на самом краю). */
    private fun tOf(i: Int) = if (i == 0) 0f else i / 10.4f + 0.02f
    private fun radius(i: Int) = (8.5f + i * 0.65f) * d

    // ── Касание ──────────────────────────────────────────────────────────────

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Шкалу ведут пальцем: экран не листается и не прокручивается
                parent?.requestDisallowInterceptTouchEvent(true)
                val t = ((e.x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
                value = ((t - 0.02f) * 10.4f).roundToInt().coerceIn(0, 10)
            }
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false); performClick() }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun burst(i: Int, now: Long) {
        if (!motion || eink || width == 0) return
        val cx = xAt(tOf(i)); val cy = yAt(tOf(i))
        repeat(if (i == 10) 14 else 7) {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val sp = (40f + rnd.nextFloat() * 70f) * d
            sparks += Spark(cx, cy, cos(a) * sp, sin(a) * sp - 20 * d, (1.6f + rnd.nextFloat() * 2.2f) * d,
                COLORS[(i - 1 + rnd.nextInt(3)) % 10], now, 700L + rnd.nextInt(600))
        }
    }

    // ── Отрисовка ────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0) return
        val now = SystemClock.uptimeMillis()
        val ink = RtColors.c(context, R.color.rt_ink)
        // Без оси незажжённые звёзды — единственная подсказка, где шкала: контур заметнее прежнего
        val dim = RtColors.withAlpha(ink, if (RtColors.isNight(context)) 0.36f else 0.28f)

        // Звёзды (оси нет: волну выстраивают сами звёзды)
        var animating = false
        for (i in 0..10) {
            val cx = xAt(tOf(i)); val cy = yAt(tOf(i))
            val on = i in 1..value
            var r = if (i == 0) 9 * d else radius(i)
            if (!on) {
                starPath(cx, cy, r, 0f)
                line.shader = null; line.strokeWidth = 1.5f * d
                line.color = if (i == 0) RtColors.withAlpha(ink, 0.55f) else dim
                canvas.drawPath(star, line)
                continue
            }
            val c = COLORS[i - 1]
            if (eink) {
                starPath(cx, cy, r, 0f); fill.shader = null; fill.color = Color.BLACK
                canvas.drawPath(star, fill); continue
            }
            // Вспышка при зажигании: звезда вырастает с перелётом
            val age = (now - litAt[i]).toFloat()
            if (motion && litAt[i] > 0 && age < 450f) {
                val k = age / 450f
                r *= (0.4f + 1.0f * k + 0.45f * sin(PI.toFloat() * k)).coerceAtMost(1.45f)
                animating = true
            }
            // Перелив: звезда дышит, свечение мерцает
            val tw = if (motion) sin(now / 380f + i * 1.3f) else 0f
            if (motion) animating = true
            val glowR = r * (2.3f + 0.35f * tw)
            fill.shader = RadialGradient(cx, cy, glowR,
                intArrayOf(RtColors.withAlpha(c, 0.55f), RtColors.withAlpha(c, 0f)), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, glowR, fill)
            fill.shader = null
            starPath(cx, cy, r * (1f + 0.05f * tw), tw * 6f)
            fill.color = RtColors.mix(c, Color.WHITE, 0.12f + 0.12f * (tw + 1f) / 2f)
            canvas.drawPath(star, fill)
            line.shader = null; line.color = Color.WHITE; line.strokeWidth = 1 * d
            canvas.drawPath(star, line)
            // Последняя зажжённая искрит сильнее: блик в середине
            if (i == value) {
                fill.color = RtColors.withAlpha(Color.WHITE, 0.6f + 0.3f * tw)
                canvas.drawCircle(cx, cy, r * 0.22f, fill)
            }
        }

        // Звёздочки
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            val age = (now - s.born).toFloat()
            if (age > s.life) { it.remove(); continue }
            val k = age / s.life
            val tSec = age / 1000f
            val x = s.x + s.vx * tSec
            val y = s.y + s.vy * tSec + 60 * d * tSec * tSec
            starPath(x, y, s.r * (1f - 0.4f * k), age / 4f)
            fill.color = RtColors.withAlpha(s.color, 1f - k)
            canvas.drawPath(star, fill)
            animating = true
        }

        if (animating && isShown) postInvalidateOnAnimation()
    }

    /** Пятиконечная звезда с центром (cx, cy), поворот в градусах. */
    private fun starPath(cx: Float, cy: Float, r: Float, rot: Float) {
        star.reset()
        for (k in 0 until 10) {
            val rr = if (k % 2 == 0) r else r * 0.45f
            val a = ((k * 36f - 90f + rot) * PI / 180f).toFloat()
            val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
            if (k == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
    }

    companion object {
        /** Слева тёплые (плохие оценки), справа холодные (хорошие). */
        private val COLORS = intArrayOf(
            0xFFFF3B3B.toInt(), 0xFFFF6A2E.toInt(), 0xFFFF9A1F.toInt(), 0xFFFFC21F.toInt(), 0xFFFFE14A.toInt(),
            0xFF3DF5C0.toInt(), 0xFF2EE6FF.toInt(), 0xFF4F8BFF.toInt(), 0xFF7B5CFF.toInt(), 0xFFB06BFF.toInt()
        )
    }
}
