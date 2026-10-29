package com.readingtracker.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.readingtracker.R

/**
 * Нижнее меню значками без подписей (подпись — только для озвучки): главная, книги,
 * в середине круглая кнопка таймера, статистика, настройки.
 * Ч/б: линия сверху. Стекло: плавающая капсула, под выбранным значком перетекает
 * стеклянная линза. Чернила: выбранный значок — на чёрной плашке.
 */
class RtNavBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val d = resources.displayMetrics.density
    private val style = RtTheme.style(context)
    private val night = RtColors.isNight(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val items = mutableListOf<Pair<Int, NavItemView>>()
    private var selectedId = -1
    var onSelect: ((Int) -> Unit)? = null

    /** Кнопка ручного таймера в середине меню. */
    val timer = TimerButtonView(context, style)

    // Линза под выбранным пунктом: края в px, плавно перетекают
    private var lensL = 0f
    private var lensR = 0f
    private var animator: ValueAnimator? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
        // Кнопка таймера чуть выступает над капсулой
        clipToPadding = false
        when (style) {
            RtTheme.Style.GLASS -> {
                setBackgroundColor(Color.TRANSPARENT)
                setPadding((18 * d).toInt(), (8 * d).toInt(), (18 * d).toInt(), (10 * d).toInt())
            }
            else -> setBackgroundColor(RtColors.c(context, R.color.rt_bg))
        }
    }

    /** Пункты меню значками; между второй и третьей парой встаёт кнопка таймера. */
    fun setItems(list: List<Pair<Int, NavIcons.Kind>>, labels: Map<Int, String>) {
        removeAllViews(); items.clear()
        for ((i, pair) in list.withIndex()) {
            val (id, kind) = pair
            if (i == list.size / 2) {
                val lp = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.1f)
                // Выступает над капсулой на высоту верхнего отступа
                lp.topMargin = -paddingTop
                addView(timer, lp)
            }
            val v = NavItemView(context, style).apply {
                icon = kind
                text = ""
                contentDescription = labels[id] ?: ""
                setOnClickListener { onSelect?.invoke(id) }
            }
            addView(v, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            items += id to v
        }
    }

    fun select(id: Int) {
        val first = selectedId == -1
        selectedId = id
        for ((itemId, v) in items) v.selectedItem = itemId == id
        if (width > 0) moveLens(!first) else post { moveLens(false) }
    }

    private fun lensTarget(): Pair<Float, Float>? {
        val v = items.firstOrNull { it.first == selectedId }?.second ?: return null
        val tw = v.contentWidth()
        val cx = v.left + v.width / 2f
        val half = tw / 2 + 12 * d
        return (cx - half) to (cx + half)
    }

    private fun moveLens(animate: Boolean) {
        val (l, r) = lensTarget() ?: return
        animator?.cancel()
        if (!animate || !RtTheme.motion(context) || lensR == 0f) {
            lensL = l; lensR = r; invalidate(); return
        }
        val l0 = lensL; val r0 = lensR
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                val t = it.animatedValue as Float
                // Капля: передний край бежит быстрее, задний догоняет
                val lead = minOf(1f, t * 1.35f)
                if (l > l0) { lensR = r0 + (r - r0) * lead; lensL = l0 + (l - l0) * t }
                else { lensL = l0 + (l - l0) * lead; lensR = r0 + (r - r0) * t }
                invalidate()
            }
            start()
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed && animator?.isRunning != true) lensTarget()?.let { (a, z) -> lensL = a; lensR = z }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (style) {
            RtTheme.Style.MONO -> {
                paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f * d
                paint.color = RtColors.c(context, R.color.rt_nav_line)
                canvas.drawLine(16 * d, 0.5f * d, width - 16 * d, 0.5f * d, paint)
            }
            RtTheme.Style.EINK -> {
                paint.shader = null; paint.style = Paint.Style.FILL
                paint.color = Color.BLACK
                canvas.drawRect(0f, 0f, width.toFloat(), 2f * d, paint)
                if (lensR > lensL) {
                    val top = 10 * d; val bottom = height - 10 * d
                    canvas.drawRoundRect(RectF(lensL, top, lensR, bottom), 8 * d, 8 * d, paint)
                }
            }
            RtTheme.Style.GLASS -> drawGlass(canvas)
        }
    }

    private fun drawGlass(canvas: Canvas) {
        val cap = RectF(paddingLeft - 8 * d, paddingTop.toFloat(), width - paddingRight + 8 * d, height - paddingBottom.toFloat())
        val r = cap.height() / 2
        // Тень под капсулой
        paint.style = Paint.Style.FILL; paint.shader = null
        paint.color = if (night) 0x33000000 else 0x1A3A2A80
        canvas.drawRoundRect(RectF(cap.left + 2 * d, cap.top + 3 * d, cap.right - 2 * d, cap.bottom + 3 * d), r, r, paint)
        // Стекло
        paint.shader = LinearGradient(0f, cap.top, 0f, cap.bottom,
            if (night) 0x38FFFFFF else 0xD9FFFFFF.toInt(), if (night) 0x14FFFFFF else 0x8CFFFFFF.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(cap, r, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f * d
        paint.shader = LinearGradient(cap.left, cap.top, cap.right, cap.bottom,
            if (night) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt(), if (night) 0x1AFFFFFF else 0x80FFFFFF.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(cap, r, r, paint)
        // Линза под выбранным пунктом
        if (lensR > lensL) {
            val lens = RectF(lensL, cap.top + 5 * d, lensR, cap.bottom - 5 * d)
            val lr = lens.height() / 2
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, lens.top, 0f, lens.bottom,
                if (night) intArrayOf(0x66A58EFF, 0x40FF6FB5) else intArrayOf(0x40FFFFFF, 0x337B5CFF),
                null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(lens, lr, lr, paint)
            // Блик линзы сверху
            paint.shader = LinearGradient(0f, lens.top, 0f, lens.centerY(), 0x99FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(lens.left + 3 * d, lens.top + 1.5f * d, lens.right - 3 * d, lens.centerY()), lr, lr, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f * d
            paint.shader = LinearGradient(lens.left, lens.top, lens.right, lens.bottom, 0xE6FFFFFF.toInt(), 0x40FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(lens, lr, lr, paint)
        }
        paint.shader = null
    }
}

class NavItemView(context: Context, private val style: RtTheme.Style) : AppCompatTextView(context) {

    private val d = resources.displayMetrics.density
    private val night = RtColors.isNight(context)

    var selectedItem = false
        set(value) { field = value; refresh() }

    /** Значок пункта. */
    var icon: NavIcons.Kind? = null
        set(value) { field = value; invalidate() }

    private val iconSize get() = 32f * d

    init {
        gravity = Gravity.CENTER
        maxLines = 1
        isClickable = true
        isFocusable = true
        includeFontPadding = false
        refresh()
    }

    /** Ширина значка — по ней строится линза. */
    fun contentWidth(): Float = iconSize * 0.9f

    private fun refresh() { invalidate() }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        refresh()
        if (style == RtTheme.Style.GLASS) animate().scaleX(if (isPressed) 0.92f else 1f).scaleY(if (isPressed) 0.92f else 1f).setDuration(120).start()
    }

    private fun colors(): NavIcons.Colors {
        val active = selectedItem || isPressed
        return when (style) {
            RtTheme.Style.EINK -> NavIcons.outline(if (selectedItem) Color.WHITE else Color.BLACK)
            RtTheme.Style.GLASS -> if (active) NavIcons.glassSelected(night) else NavIcons.outline(RtColors.c(context, R.color.rt_ink2))
            RtTheme.Style.MONO -> if (active) NavIcons.monoSelected(RtColors.c(context, R.color.rt_ink))
                else NavIcons.outline(RtColors.c(context, R.color.rt_ink2))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kind = icon ?: return
        NavIcons.draw(canvas, kind, width / 2f, height / 2f, iconSize, colors())
    }
}
