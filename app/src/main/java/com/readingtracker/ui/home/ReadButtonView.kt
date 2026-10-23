package com.readingtracker.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.readingtracker.R
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme

/**
 * Большая кнопка «читать» с названием читалки.
 * Ч/б — тёмная плитка; стекло — переливающаяся капсула с бликом, по которой иногда пробегает свет;
 * чернила — белая с толстой чёрной рамкой, под пальцем чёрная.
 */
class ReadButtonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var sub = ""
    private val style = RtTheme.style(context)
    private val motion = RtTheme.motion(context)
    private val night = RtColors.isNight(context)

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.semibold(context); textSize = 28f; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular(context); textSize = 13f; textAlign = Paint.Align.CENTER }

    // Пробегающий блик: начало текущего прохода
    private var sweepStart = 0L
    private val sweepMs = 1400L
    private val nextSweep = Runnable { sweepStart = SystemClock.uptimeMillis(); invalidate() }

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

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (style != RtTheme.Style.EINK) animate().scaleX(if (isPressed) 0.97f else 1f).scaleY(if (isPressed) 0.97f else 1f).setDuration(90).start()
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
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 18f, 18f, bg)
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

    private fun drawGlass(canvas: Canvas, w: Float, h: Float) {
        val rect = RectF(0f, 0f, w, h)
        val radius = 26f
        val colors = if (night) intArrayOf(0xFF6C4DF5.toInt(), 0xFFB04BE0.toInt(), 0xFFFF5C8A.toInt())
            else intArrayOf(0xFF7B5CFF.toInt(), 0xFFC651E8.toInt(), 0xFFFF7A93.toInt())
        bg.shader = LinearGradient(0f, 0f, w, h, colors, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(rect, bg)
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
        canvas.drawRoundRect(RectF(0.6f, 0.6f, w - 0.6f, h - 0.6f), radius, radius, border)
        title.setShadowLayer(6f, 0f, 1.5f, 0x40000000)
        texts(canvas, w, h, Color.WHITE, 0xE6FFFFFF.toInt())
        title.clearShadowLayer()
    }

    private fun texts(canvas: Canvas, w: Float, h: Float, titleColor: Int, subColor: Int) {
        title.color = titleColor
        canvas.drawText("читать", w / 2, h / 2 + 4f, title)
        subPaint.color = subColor
        if (sub.isNotBlank()) canvas.drawText(sub, w / 2, h / 2 + 24f, subPaint)
    }
}
