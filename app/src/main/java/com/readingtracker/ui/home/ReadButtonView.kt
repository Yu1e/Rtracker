package com.readingtracker.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.readingtracker.R
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Большая кнопка «читать» с названием читалки.
 * Ч/б — тёмная плитка; стекло — переливающаяся капсула с бликом, по которой иногда пробегает свет;
 * чернила — белая с толстой чёрной рамкой, под пальцем чёрная.
 *
 * Каждое нажатие — новый эффект, без повтора подряд: волна-перелив от пальца, веер страничек,
 * ляссе-серпантин, капля, водяной матрас. Цвет капсулы каждый раз чуть сдвигается по кругу оттенков.
 * Читалка открывается сразу, эффект доигрывает поверх.
 */
class ReadButtonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Effect { WAVE, FAN, RIBBONS, DROP, MATTRESS }

    private val d = resources.displayMetrics.density
    private var sub = ""
    private val style = RtTheme.style(context)
    private val motion = RtTheme.motion(context)
    private val night = RtColors.isNight(context)
    private val effects = style != RtTheme.Style.EINK && motion

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fx = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.semibold(context); textSize = 28f; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular(context); textSize = 13f; textAlign = Paint.Align.CENTER }

    // Пробегающий блик: начало текущего прохода
    private var sweepStart = 0L
    private val sweepMs = 1400L
    private val nextSweep = Runnable { sweepStart = SystemClock.uptimeMillis(); invalidate() }

    // Текущий эффект и точка касания (в dp)
    private var effect: Effect? = null
    private var effectStart = 0L
    private var touchX = 0f
    private var touchY = 0f

    // Сдвиг оттенка капсулы: перетекает от прежнего к новому
    private var hueFrom = hueShift
    private var hueStart = 0L

    init {
        isClickable = true
        isFocusable = true
    }

    fun setReader(name: String) {
        sub = name
        contentDescription = "читать, $name"
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (style == RtTheme.Style.GLASS && motion) postDelayed(nextSweep, 1800)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(nextSweep)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && effects && isEnabled) {
            touchX = event.x / d; touchY = event.y / d
            startEffect()
        }
        return super.onTouchEvent(event)
    }

    /** Следующий эффект — любой, кроме прошлого; капсула сдвигается по кругу оттенков. */
    private fun startEffect() {
        val choices = Effect.values().filter { it != lastEffect }
        val e = choices[Random.nextInt(choices.size)]
        lastEffect = e
        effect = e
        effectStart = SystemClock.uptimeMillis()
        hueFrom = hueShift
        hueStart = effectStart
        hueShift = (hueShift + if (e == Effect.DROP || e == Effect.MATTRESS) 50f + Random.nextFloat() * 40f
            else 18f + Random.nextFloat() * 22f) % 360f
        when (e) {
            Effect.FAN, Effect.RIBBONS -> burst(e)
            else -> {}
        }
        invalidate()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (style == RtTheme.Style.EINK) { invalidate(); return }
        pivotX = width / 2f
        if (effect == Effect.DROP && motion) {
            // Капля: под пальцем сплющивается и растекается вширь, отпустили — подпрыгивает
            pivotY = height.toFloat()
            if (isPressed) animate().scaleX(1.06f).scaleY(0.86f).setDuration(120).setInterpolator(null).start()
            else animate().scaleX(0.95f).scaleY(1.08f).setDuration(140).setInterpolator(null).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(360).setInterpolator(OvershootInterpolator(2.6f)).start()
            }.start()
        } else {
            pivotY = height / 2f
            animate().scaleX(if (isPressed) 0.97f else 1f).scaleY(if (isPressed) 0.97f else 1f).setDuration(90).setInterpolator(null).start()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((98 * d).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.scale(d, d)
        val w = width / d; val h = height / d
        when (style) {
            RtTheme.Style.GLASS -> drawGlass(canvas, w, h)
            RtTheme.Style.EINK -> drawEink(canvas, w, h)
            RtTheme.Style.MONO -> drawMono(canvas, w, h)
        }
        canvas.restore()
    }

    private fun drawMono(canvas: Canvas, w: Float, h: Float) {
        val strong = RtColors.c(context, R.color.rt_strong)
        val on = RtColors.c(context, R.color.rt_on_strong)
        bg.shader = null
        bg.color = if (isPressed) RtColors.mix(strong, on, 0.14f) else strong
        val shape = shapePath(w, h, 18f)
        canvas.drawPath(shape, bg)
        canvas.save(); canvas.clipPath(shape)
        drawInnerEffect(canvas, w, h, Ribbons.accent(context))
        canvas.restore()
        texts(canvas, w, h, on, RtColors.c(context, R.color.rt_on_strong2))
    }

    private fun drawEink(canvas: Canvas, w: Float, h: Float) {
        val r = RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
        bg.shader = null
        bg.color = if (isPressed) Color.BLACK else Color.WHITE
        canvas.drawRoundRect(r, 12f, 12f, bg)
        border.color = Color.BLACK; border.strokeWidth = 3f; border.shader = null
        canvas.drawRoundRect(r, 12f, 12f, border)
        val fg = if (isPressed) Color.WHITE else Color.BLACK
        texts(canvas, w, h, fg, fg)
    }

    /** Оттенок капсулы сейчас: во время эффекта перетекает к новому. */
    private fun currentHue(): Float {
        if (hueStart == 0L) return hueShift
        val t = ((SystemClock.uptimeMillis() - hueStart) / 600f).coerceIn(0f, 1f)
        if (t >= 1f) { hueStart = 0L; return hueShift }
        var delta = hueShift - hueFrom
        if (delta < 0) delta += 360f
        return (hueFrom + delta * t) % 360f
    }

    private fun drawGlass(canvas: Canvas, w: Float, h: Float) {
        val radius = 26f
        val hue = currentHue()
        val base = if (night) intArrayOf(0xFF6C4DF5.toInt(), 0xFFB04BE0.toInt(), 0xFFFF5C8A.toInt())
            else intArrayOf(0xFF7B5CFF.toInt(), 0xFFC651E8.toInt(), 0xFFFF7A93.toInt())
        val colors = IntArray(3) { rotateHue(base[it], hue) }
        val shape = shapePath(w, h, radius)
        val rect = RectF(0f, 0f, w, h)
        bg.shader = LinearGradient(0f, 0f, w, h, colors, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.save()
        canvas.clipPath(shape)
        canvas.drawRect(rect, bg)
        drawInnerEffect(canvas, w, h, colors[1])
        // Блик стекла: светлая верхняя половина
        gloss.shader = LinearGradient(0f, 0f, 0f, h * 0.55f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(4f, 3f, w - 4f, h * 0.55f), radius - 3f, radius - 3f, gloss)
        if (isPressed) { gloss.shader = null; gloss.color = 0x29FFFFFF; canvas.drawRect(rect, gloss) }
        // Пробегающий свет
        if (motion && sweepStart > 0) {
            val t = (SystemClock.uptimeMillis() - sweepStart) / sweepMs.toFloat()
            if (t <= 1f) {
                val x = -w * 0.4f + t * w * 1.8f
                gloss.shader = LinearGradient(x - 40f, 0f, x + 40f, h,
                    intArrayOf(0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                canvas.drawRect(rect, gloss)
                postInvalidateOnAnimation()
            } else {
                sweepStart = 0
                postDelayed(nextSweep, 6500)
            }
        }
        canvas.restore()
        // Кромка: ярче сверху слева
        border.strokeWidth = 1.2f
        border.shader = LinearGradient(0f, 0f, w, h, 0xCCFFFFFF.toInt(), 0x33FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawPath(shapePath(w, h, radius, 0.6f), border)
        title.setShadowLayer(6f, 0f, 1.5f, 0x40000000)
        texts(canvas, w, h, Color.WHITE, 0xE6FFFFFF.toInt())
        title.clearShadowLayer()
        if (hueStart != 0L) postInvalidateOnAnimation()
    }

    // ── Форма капсулы: в покое скруглённый прямоугольник, у матраса колышется ───

    private fun effectT(durationMs: Float): Float {
        if (effect == null) return 1f
        return ((SystemClock.uptimeMillis() - effectStart) / durationMs).coerceAtLeast(0f)
    }

    private fun shapePath(w: Float, h: Float, r: Float, inset: Float = 0f): Path {
        val p = Path()
        val t = effectT(MATTRESS_MS)
        if (effect != Effect.MATTRESS || t >= 1f) {
            p.addRoundRect(RectF(inset, inset, w - inset, h - inset), r, r, Path.Direction.CW)
            return p
        }
        // Водяной матрас: тяжёлая волна катится по верху и низу, края гладкие и только внутрь
        val amp = 8f * exp(-2.6f * t)
        val phase = (t * 1.3f).toDouble() * 2 * PI
        val start = (touchX / w).toDouble()
        fun top(x: Float) = inset + amp * (0.5f + 0.5f * sin((x / w - start) * 2 * PI * 0.8 - phase + PI / 2).toFloat())
        fun bot(x: Float) = h - inset - amp * 0.8f * (0.5f + 0.5f * sin((x / w - start) * 2 * PI * 0.8 - phase).toFloat())
        val side = amp * 0.45f * (0.5f + 0.5f * sin(phase).toFloat())
        val l = inset + side; val rr = w - inset - side
        val steps = 24
        p.moveTo(l + r, top(l + r))
        for (i in 1..steps) { val x = l + r + (rr - l - 2 * r) * i / steps; p.lineTo(x, top(x)) }
        p.quadTo(rr, top(rr - r), rr, top(rr - r) + r)
        p.lineTo(rr, bot(rr - r) - r)
        p.quadTo(rr, bot(rr - r), rr - r, bot(rr - r))
        for (i in 1..steps) { val x = rr - r - (rr - l - 2 * r) * i / steps; p.lineTo(x, bot(x)) }
        p.quadTo(l, bot(l + r), l, bot(l + r) - r)
        p.lineTo(l, top(l + r) + r)
        p.quadTo(l, top(l + r), l + r, top(l + r))
        p.close()
        return p
    }

    /** Эффекты внутри капсулы: волна от пальца и пятна матраса. Холст уже обрезан по капсуле. */
    private fun drawInnerEffect(canvas: Canvas, w: Float, h: Float, tint: Int) {
        when (effect) {
            Effect.WAVE -> {
                val ms = SystemClock.uptimeMillis() - effectStart
                val ringColors = intArrayOf(0xFF5FD3F0.toInt(), 0xFFFFC94A.toInt(), Color.WHITE)
                var alive = false
                fx.style = Paint.Style.STROKE; fx.shader = null
                for (i in 0 until 3) {
                    val t = ((ms - i * 110) / 650f)
                    if (t < 0f) { alive = true; continue }
                    if (t > 1f) continue
                    alive = true
                    val ease = 1 - (1 - t) * (1 - t)
                    fx.strokeWidth = 4f - 2.5f * t
                    fx.color = RtColors.withAlpha(ringColors[i], 0.85f * (1 - t))
                    canvas.drawCircle(touchX, touchY, 6f + ease * w * 0.85f, fx)
                }
                fx.style = Paint.Style.FILL
                if (alive) postInvalidateOnAnimation() else effect = null
            }
            Effect.MATTRESS -> {
                val t = effectT(MATTRESS_MS)
                if (t >= 1f) { effect = null; return }
                // Внутри переливаются цветные пятна: плывут и размешиваются
                val blobs = intArrayOf(0xFF3FE0A0.toInt(), 0xFFFFC14D.toInt(), 0xFF2EB7F0.toInt(), 0xFFFF4FA0.toInt())
                val fade = sin(min(1f, t * 1.4f) * PI).toFloat().coerceAtLeast(0f)
                fx.style = Paint.Style.FILL
                for (i in blobs.indices) {
                    val a = t * 2.2f * PI.toFloat() + i * 1.7f
                    val cx = w * (0.25f + 0.5f * (0.5f + 0.5f * sin(a)))
                    val cy = h * (0.3f + 0.4f * (0.5f + 0.5f * sin(a * 1.3f + i)))
                    val r = h * 0.55f
                    fx.shader = RadialGradient(cx, cy, r, intArrayOf(RtColors.withAlpha(blobs[i], 0.75f * fade), RtColors.withAlpha(blobs[i], 0f)),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawCircle(cx, cy, r, fx)
                }
                fx.shader = null
                postInvalidateOnAnimation()
            }
            Effect.DROP -> {
                // Цвет перетекает: светлая волна поднимается снизу вверх
                val t = effectT(600f)
                if (t >= 1f) { effect = null; return }
                val y = h * (1 - t)
                fx.style = Paint.Style.FILL
                fx.shader = LinearGradient(0f, y - h * 0.4f, 0f, y + h * 0.1f,
                    intArrayOf(0x00FFFFFF, RtColors.withAlpha(rotateHue(tint, 40f), 0.45f * (1 - t)), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, fx)
                fx.shader = null
                postInvalidateOnAnimation()
            }
            else -> effect = null
        }
    }

    // ── Веер страниц и ляссе-серпантин: вылетают за кнопку, рисуются поверх окна ──

    private fun burst(kind: Effect) {
        val root = rootView ?: return
        val loc = IntArray(2); getLocationInWindow(loc)
        var scale = 1f
        var v: View? = this
        while (v != null) { scale *= v.scaleX; v = v.parent as? View }
        val rect = RectF(loc[0].toFloat(), loc[1].toFloat(), loc[0] + width * scale, loc[1] + height * scale)
        val palettes = Ribbons.palettes.shuffled().take(4)
        val drawable = BurstDrawable(kind, rect, d * scale, palettes)
        drawable.setBounds(0, 0, root.width, root.height)
        root.overlay.add(drawable)
        drawable.onDone = { root.overlay.remove(drawable) }
        drawable.invalidateSelf()
    }

    private fun texts(canvas: Canvas, w: Float, h: Float, titleColor: Int, subColor: Int) {
        title.color = titleColor
        canvas.drawText("читать", w / 2, h / 2 + 4f, title)
        subPaint.color = subColor
        if (sub.isNotBlank()) canvas.drawText(sub, w / 2, h / 2 + 24f, subPaint)
    }

    companion object {
        private const val MATTRESS_MS = 1100f

        /** Сдвиг оттенка капсулы между открытиями экрана сохраняется. */
        private var hueShift = 0f
        private var lastEffect: Effect? = null

        fun rotateHue(color: Int, deg: Float): Int {
            if (deg == 0f) return color
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[0] = (hsv[0] + deg) % 360f
            return Color.HSVToColor(Color.alpha(color), hsv)
        }
    }
}

/** Эффект поверх окна: странички веером или ленты-ляссе вылетают из кнопки и тают. */
private class BurstDrawable(
    private val kind: ReadButtonView.Effect,
    private val btn: RectF,
    private val u: Float,
    private val palettes: List<RibbonPalette>
) : android.graphics.drawable.Drawable() {

    var onDone: (() -> Unit)? = null
    private val start = SystemClock.uptimeMillis()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var finished = false

    override fun draw(canvas: Canvas) {
        val ms = SystemClock.uptimeMillis() - start
        val dur = if (kind == ReadButtonView.Effect.FAN) 800f else 950f
        val t = ms / dur
        if (t >= 1f) {
            if (!finished) { finished = true; scheduleSelf({ onDone?.invoke() }, SystemClock.uptimeMillis()) }
            return
        }
        if (kind == ReadButtonView.Effect.FAN) fan(canvas, t) else ribbons(canvas, t)
        invalidateSelf()
    }

    /** Семь страничек веером из верхнего края кнопки. */
    private fun fan(canvas: Canvas, t: Float) {
        val n = 7
        val ox = btn.centerX(); val oy = btn.top + 10 * u
        val ease = 1 - (1 - t) * (1 - t) * (1 - t)
        val alpha = if (t < 0.55f) 1f else (1 - (t - 0.55f) / 0.45f)
        for (j in 0 until n) {
            val ang = Math.toRadians((-160.0 + 140.0 * j / (n - 1)))
            val dist = (8 + 62 * ease) * u
            val x = ox + (dist * Math.cos(ang)).toFloat()
            val y = oy + (dist * Math.sin(ang)).toFloat()
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate((Math.toDegrees(ang) + 90).toFloat() * ease)
            val pw = 13 * u; val ph = 17 * u
            val page = RectF(-pw / 2, -ph / 2, pw / 2, ph / 2)
            paint.style = Paint.Style.FILL; paint.color = RtColors.withAlpha(Color.WHITE, 0.95f * alpha)
            canvas.drawRoundRect(page, 1.5f * u, 1.5f * u, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f * u
            paint.color = RtColors.withAlpha(0xFF6343F0.toInt(), alpha)
            canvas.drawRoundRect(page, 1.5f * u, 1.5f * u, paint)
            paint.strokeWidth = 0.8f * u
            for (k in 0 until 3) {
                val ly = -ph / 2 + (5 + k * 3.6f) * u
                canvas.drawLine(-pw / 2 + 3 * u, ly, pw / 2 - 3 * u, ly, paint)
            }
            canvas.restore()
        }
    }

    /** Четыре ленты в цветах книг: выстреливают вверх, извиваются и опадают. */
    private fun ribbons(canvas: Canvas, t: Float) {
        val up = (t / 0.45f).coerceAtMost(1f)
        val rise = 1 - (1 - up) * (1 - up)
        val fall = ((t - 0.45f) / 0.55f).coerceIn(0f, 1f)
        val alpha = 1 - fall * fall
        val len = 64 * u * rise
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        for ((i, pal) in palettes.withIndex()) {
            val bx = btn.left + btn.width() * (0.2f + 0.2f * i)
            val by = btn.top + 4 * u + fall * 26 * u
            val path = Path()
            val steps = 14
            for (k in 0..steps) {
                val s = k / steps.toFloat()
                val y = by - len * s
                val x = bx + (5 * u) * sin((s * 3.2f + t * 5f + i) * PI).toFloat() * (0.4f + s)
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.color = RtColors.withAlpha(pal.base, alpha); paint.strokeWidth = 6 * u
            canvas.drawPath(path, paint)
            paint.color = RtColors.withAlpha(pal.stripe, alpha); paint.strokeWidth = 1.6f * u
            canvas.drawPath(path, paint)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
