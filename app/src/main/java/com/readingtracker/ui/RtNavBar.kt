package com.readingtracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.readingtracker.R
import com.readingtracker.ui.home.Ribbons

/**
 * Нижнее меню словами: главная, книги, статистика, настройки.
 * Выбранный пункт полужирный; под пальцем пункт вспыхивает цветами ляссе текущей книги.
 */
class RtNavBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val d = resources.displayMetrics.density
    private val linePaint = Paint().apply { strokeWidth = 1f * resources.displayMetrics.density }
    private val items = mutableListOf<Pair<Int, NavItemView>>()
    var onSelect: ((Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
        setBackgroundColor(RtColors.c(context, R.color.rt_bg))
    }

    fun setItems(list: List<Pair<Int, String>>) {
        removeAllViews(); items.clear()
        for ((id, label) in list) {
            val v = NavItemView(context).apply {
                text = label
                setOnClickListener { onSelect?.invoke(id) }
            }
            addView(v, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            items += id to v
        }
    }

    fun select(id: Int) {
        for ((itemId, v) in items) v.selectedItem = itemId == id
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        linePaint.color = RtColors.c(context, R.color.rt_nav_line)
        val inset = 16 * d
        canvas.drawLine(inset, 0.5f * d, width - inset, 0.5f * d, linePaint)
    }
}

class NavItemView(context: Context) : AppCompatTextView(context) {

    private val d = resources.displayMetrics.density
    private var variant: PressVariant? = null
    private var glow = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val endGlow = Runnable { glow = false; variant = null; refresh() }

    var selectedItem = false
        set(value) { field = value; refresh() }

    init {
        gravity = Gravity.CENTER
        // Размер в dp: меню всегда в одну строку, даже при крупном системном шрифте
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        maxLines = 1
        isClickable = true
        isFocusable = true
        includeFontPadding = false
        refresh()
    }

    private fun bgColor() = RtColors.c(context, R.color.rt_bg)

    private fun refresh() {
        val pal = Ribbons.current
        val bg = bgColor()
        typeface = if (selectedItem || glow) Fonts.semibold(context) else Fonts.regular(context)
        val c = when (if (glow) variant else null) {
            null -> RtColors.c(context, if (selectedItem) R.color.rt_ink else R.color.rt_ink2)
            PressVariant.B -> RtColors.onColor(pal.base)
            PressVariant.A, PressVariant.D ->
                RtColors.mostReadable(bg, pal.stripe, pal.base, RtColors.mix(pal.base, 0xFFFFFFFF.toInt(), 0.35f))
            PressVariant.C ->
                RtColors.mostReadable(bg, pal.base, RtColors.mix(pal.base, 0xFFFFFFFF.toInt(), 0.35f), pal.stripe)
        }
        setTextColor(c)
        invalidate()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (isPressed && !glow) {
            removeCallbacks(endGlow)
            variant = PressVariant.next()
            glow = true
            refresh()
        } else if (!isPressed && glow) {
            removeCallbacks(endGlow)
            postDelayed(endGlow, 220)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val v = if (glow) variant else null
        val pal = Ribbons.current
        val tw = paint.apply { textSize = this@NavItemView.textSize; typeface = this@NavItemView.typeface }.measureText(text.toString())
        val cx = width / 2f
        val cy = height / 2f
        if (v == PressVariant.B) {
            paint.color = pal.base; paint.style = Paint.Style.FILL
            val r = RectF(cx - tw / 2 - 10 * d, cy - 14 * d, cx + tw / 2 + 10 * d, cy + 14 * d)
            canvas.drawRoundRect(r, 10 * d, 10 * d, paint)
        }
        super.onDraw(canvas)
        canvas.save(); canvas.scale(d, d)
        val cxd = cx / d; val cyd = cy / d; val twd = tw / d
        when (v) {
            PressVariant.A -> PressDraw.tab(canvas, cxd, 0f, pal)
            PressVariant.C -> {
                val dc = RtColors.mostReadable(bgColor(), pal.stripe, pal.base)
                PressDraw.diamonds(canvas, cxd, cyd + 13f, 1, 6f, 0f, dc)
            }
            PressVariant.D -> {
                paint.color = RtColors.mostReadable(bgColor(), pal.base, pal.stripe); paint.style = Paint.Style.FILL
                canvas.drawRoundRect(RectF(cxd - twd / 2, cyd + 11f, cxd + twd / 2, cyd + 14f), 1.5f, 1.5f, paint)
            }
            else -> {}
        }
        canvas.restore()
    }
}
