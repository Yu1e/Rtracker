package com.readingtracker.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.TooltipCompat
import com.readingtracker.R
import kotlin.math.min
import kotlin.math.sin

/**
 * Круглая кнопка ручного таймера в середине нижнего меню.
 * Ждёт — стеклянная капля с песочными часами. Идёт — розово-оранжевая, внутри минуты,
 * вокруг тихо пульсирует ореол; нажатие останавливает.
 * Подсказка — при долгом нажатии и при наведении мышью или стилусом.
 */
class TimerButtonView(context: Context, private val style: RtTheme.Style) : View(context) {

    private val d = resources.displayMetrics.density
    private val night = RtColors.isNight(context)
    private val motion = RtTheme.motion(context)

    private var running = false
    private var minutes = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.semibold(context); textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular(context); textAlign = Paint.Align.CENTER }

    // Ореол: фаза пульса 0..1
    private var pulse = 0f
    private var pulser: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        setState(false, 0)
    }

    fun setState(running: Boolean, minutes: Long) {
        val changed = running != this.running
        this.running = running
        this.minutes = minutes
        val hint = if (running) "остановить таймер" else "Таймер: читаю в другом месте"
        contentDescription = if (running) "таймер идёт, минут: $minutes. Остановить" else hint
        TooltipCompat.setTooltipText(this, hint)
        if (changed || pulser == null) updatePulse()
        invalidate()
    }

    private fun updatePulse() {
        pulser?.cancel(); pulser = null
        if (!running || !motion || !isAttachedToWindow) return
        pulser = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updatePulse() }

    override fun onDetachedFromWindow() { pulser?.cancel(); pulser = null; super.onDetachedFromWindow() }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (style != RtTheme.Style.EINK) animate().scaleX(if (isPressed) 0.92f else 1f).scaleY(if (isPressed) 0.92f else 1f).setDuration(110).start()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        // Запас по краю — под ореол
        val r = min(width, height) / 2f - 5f * d
        if (r <= 0) return
        when (style) {
            RtTheme.Style.GLASS -> drawGlass(canvas, cx, cy, r)
            RtTheme.Style.MONO -> drawMono(canvas, cx, cy, r)
            RtTheme.Style.EINK -> drawEink(canvas, cx, cy, r)
        }
    }

    private fun drawGlass(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        if (running) halo(canvas, cx, cy, r, 0xFFFF6F7D.toInt())
        // Тень
        fill.shader = null; fill.color = if (night) 0x40000000 else 0x262A1A6E
        canvas.drawCircle(cx, cy + 2.5f * d, r, fill)
        val colors = if (running) intArrayOf(0xFFFF5C8A.toInt(), 0xFFFF7A6B.toInt(), 0xFFFFA24D.toInt())
            else intArrayOf(0xFF7B5CFF.toInt(), 0xFFC651E8.toInt(), 0xFFFF7A93.toInt())
        fill.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, colors, floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, fill)
        // Блик стекла сверху
        fill.shader = LinearGradient(0f, cy - r, 0f, cy, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawOval(RectF(cx - r * 0.64f, cy - r * 0.86f, cx + r * 0.64f, cy - r * 0.28f), fill)
        fill.shader = null
        line.shader = null; line.color = Color.WHITE; line.strokeWidth = 2f * d
        canvas.drawCircle(cx, cy, r, line)
        if (running) minutesText(canvas, cx, cy, r, Color.WHITE) else hourglass(canvas, cx, cy, r, Color.WHITE)
    }

    private fun drawMono(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val strong = RtColors.c(context, R.color.rt_strong)
        val on = RtColors.c(context, R.color.rt_on_strong)
        if (running) halo(canvas, cx, cy, r, strong)
        fill.shader = null; fill.color = if (isPressed) RtColors.mix(strong, on, 0.14f) else strong
        canvas.drawCircle(cx, cy, r, fill)
        if (running) minutesText(canvas, cx, cy, r, on) else hourglass(canvas, cx, cy, r, on)
    }

    private fun drawEink(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val dark = running || isPressed
        fill.shader = null; fill.color = if (dark) Color.BLACK else Color.WHITE
        canvas.drawCircle(cx, cy, r, fill)
        line.shader = null; line.color = Color.BLACK; line.strokeWidth = 2.5f * d
        canvas.drawCircle(cx, cy, r - 1.25f * d, line)
        val fg = if (dark) Color.WHITE else Color.BLACK
        if (running) minutesText(canvas, cx, cy, r, fg) else hourglass(canvas, cx, cy, r, fg)
    }

    /** Ореол: мягкое кольцо расходится и тает. */
    private fun halo(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val t = if (motion) pulse else 0.5f
        val grow = 4.5f * d * (0.4f + 0.6f * sin(t * Math.PI).toFloat())
        val alpha = 0.18f + 0.22f * sin(t * Math.PI).toFloat()
        fill.shader = RadialGradient(cx, cy, r + grow,
            intArrayOf(RtColors.withAlpha(color, alpha), RtColors.withAlpha(color, 0f)),
            floatArrayOf(r / (r + grow), 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r + grow, fill)
        fill.shader = null
    }

    private fun minutesText(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val text = minutes.toString()
        numPaint.color = color
        numPaint.textSize = (if (text.length >= 3) 15f else 18f) * d
        subPaint.color = RtColors.withAlpha(color, 0.9f)
        subPaint.textSize = 9.5f * d
        canvas.drawText(text, cx, cy + 2f * d, numPaint)
        canvas.drawText("мин", cx, cy + 12f * d, subPaint)
    }

    /** Песочные часы: две планки, колба, песок сверху и горкой снизу. */
    private fun hourglass(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val k = r / 28f * 1.15f
        canvas.save(); canvas.translate(cx, cy); canvas.scale(k, k)
        line.shader = null; line.color = color
        line.strokeWidth = 2.4f
        canvas.drawLine(-8f, -11f, 8f, -11f, line)
        canvas.drawLine(-8f, 11f, 8f, 11f, line)
        line.strokeWidth = 2f
        val glass = Path().apply {
            moveTo(-6f, -11f); cubicTo(-6f, -3f, -1.6f, -2f, -1.6f, 0f); cubicTo(-1.6f, 2f, -6f, 3f, -6f, 11f)
            lineTo(6f, 11f); cubicTo(6f, 3f, 1.6f, 2f, 1.6f, 0f); cubicTo(1.6f, -2f, 6f, -3f, 6f, -11f); close()
        }
        canvas.drawPath(glass, line)
        fill.shader = null; fill.color = color
        canvas.drawPath(Path().apply { moveTo(-4f, -6f); lineTo(4f, -6f); lineTo(.6f, -1f); lineTo(-.6f, -1f); close() }, fill)
        canvas.drawPath(Path().apply { moveTo(-5f, 10f); cubicTo(-3f, 6f, 3f, 6f, 5f, 10f); close() }, fill)
        canvas.restore()
    }
}
