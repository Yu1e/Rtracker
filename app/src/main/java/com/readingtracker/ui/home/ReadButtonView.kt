package com.readingtracker.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.readingtracker.R
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.PressDraw
import com.readingtracker.ui.PressVariant
import com.readingtracker.ui.RtColors

/**
 * Большая кнопка «читать» с названием читалки.
 * В покое ч/б; под пальцем вспыхивает цветами ляссе текущей книги, вариант каждый раз случайный.
 */
class ReadButtonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var sub = ""
    private var variant: PressVariant? = null
    private var glow = false

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.semibold(context); textSize = 28f; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular(context); textSize = 13f; textAlign = Paint.Align.CENTER }
    private val deco = Paint(Paint.ANTI_ALIAS_FLAG)
    private val endGlow = Runnable { glow = false; variant = null; animateScale(1f); invalidate() }

    init {
        isClickable = true
        isFocusable = true
    }

    fun setReader(name: String) {
        sub = name
        contentDescription = "читать, $name"
        invalidate()
    }

    private fun animateScale(s: Float) {
        animate().scaleX(s).scaleY(s).setDuration(90).start()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (isPressed && !glow) {
            removeCallbacks(endGlow)
            variant = PressVariant.next()
            glow = true
            animateScale(0.97f)
            invalidate()
        } else if (!isPressed && glow) {
            removeCallbacks(endGlow)
            postDelayed(endGlow, 220)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((98 * d).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.scale(d, d)
        val w = width / d; val h = height / d
        val rect = RectF(0f, 0f, w, h)
        val pal = Ribbons.current
        val v = if (glow) variant else null

        val bgColor = when (v) {
            PressVariant.A, PressVariant.C -> pal.base
            PressVariant.B, PressVariant.D -> pal.stripe
            null -> RtColors.c(context, R.color.rt_strong)
        }
        bg.color = bgColor
        val clip = Path().apply { addRoundRect(rect, 18f, 18f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(rect, bg)

        val on = RtColors.onColor(bgColor)
        val other = if (bgColor == pal.base) pal.stripe else pal.base
        val accent = if (RtColors.contrast(other, bgColor) >= 3.0) other else on
        var titleColor = if (v == null) RtColors.c(context, R.color.rt_on_strong) else on
        var subColor = if (v == null) RtColors.c(context, R.color.rt_on_strong2) else RtColors.withAlpha(on, 0.75f)

        when (v) {
            PressVariant.A -> {
                deco.color = pal.stripe
                canvas.drawRoundRect(RectF(16f, 12f, w - 16f, 15f), 2f, 2f, deco)
                canvas.drawRoundRect(RectF(16f, h - 15f, w - 16f, h - 12f), 2f, 2f, deco)
                subColor = accent
            }
            PressVariant.C -> {
                PressDraw.diamonds(canvas, w / 2, 14f, 7, 6f, 9f, pal.stripe)
                PressDraw.diamonds(canvas, w / 2, h - 14f, 7, 6f, 9f, pal.stripe)
                titleColor = accent
            }
            PressVariant.D -> PressDraw.sash(canvas, rect, pal)
            else -> {}
        }
        canvas.restore()

        title.color = titleColor
        canvas.drawText("читать", w / 2, h / 2 + 4f, title)
        subPaint.color = subColor
        if (sub.isNotBlank()) canvas.drawText(sub, w / 2, h / 2 + 24f, subPaint)
        canvas.restore()
    }
}
