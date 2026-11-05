package com.readingtracker.stats

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import com.readingtracker.R
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtColors
import com.readingtracker.ui.RtTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * Календарь месяца по дням недели (пн…вс), как в обычном календаре: если первое число —
 * воскресенье, в верхнем ряду одна клетка справа; нижний ряд может быть неполным. Без подписей.
 * В прошедших днях и сегодня — минуты чтения (не часы), в будущих — число месяца. Цвет клетки —
 * по минутам. Тыц по прошедшему дню — onDaySelected с датой и минутами.
 * Палец вбок листает месяцы: вправо — прошлый, влево — следующий; дальше текущего месяца не идёт.
 */
class MonthGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var data: Map<LocalDate, Long> = emptyMap()
    private var today: LocalDate = LocalDate.now()
    private var selected: LocalDate? = null
    var onDaySelected: ((LocalDate, Long) -> Unit)? = null

    /** Показанный месяц; по умолчанию текущий. */
    var month: YearMonth = YearMonth.now()
        private set
    /** Месяц сменился пальцем: какой и текущий ли он. */
    var onMonthChanged: ((YearMonth, Boolean) -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    private val first get() = month.atDay(1)
    private val days get() = month.lengthOfMonth()
    /** Сколько пустых мест перед первым числом: понедельник — 0, воскресенье — 6. */
    private val offset get() = first.dayOfWeek.value - 1
    private val current get() = YearMonth.from(today)

    // Листание: сдвиг сетки за пальцем и анимация перехода
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragX = 0f
    private var anim: ValueAnimator? = null

    fun setData(dayMinutes: Map<LocalDate, Long>, today: LocalDate) {
        data = dayMinutes; this.today = today
        month = YearMonth.from(today)
        requestLayout(); invalidate()
    }

    private fun level(m: Long): Int = when {
        m <= 0 -> 0
        m < 15 -> 1
        m < 30 -> 2
        m < 60 -> 3
        else -> 4
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val gap = 5 * d
        val cell = (w - gap * 6) / 7f
        val rows = (offset + days + 6) / 7
        setMeasuredDimension(w, (rows * cell + (rows - 1) * gap).toInt() + 2)
    }

    override fun onDraw(canvas: Canvas) {
        // Во время листания сетка едет за пальцем и бледнеет к краю
        if (dragX != 0f) {
            canvas.save()
            canvas.translate(dragX, 0f)
        }
        val fade = if (width > 0) (1f - abs(dragX) / width * 1.2f).coerceIn(0.15f, 1f) else 1f
        val gap = 5 * d
        val cell = (width - gap * 6) / 7f
        val ink = RtColors.c(context, R.color.rt_ink)
        val ink2 = RtColors.c(context, R.color.rt_ink2)
        val ink3 = RtColors.c(context, R.color.rt_ink3)
        val eink = RtTheme.isEink(context)
        val radius = if (eink) 3 * d else 8 * d
        for (i in 0 until days) {
            val day = first.plusDays(i.toLong())
            val col = (offset + i) % 7; val row = (offset + i) / 7
            val x = col * (cell + gap); val y = row * (cell + gap)
            rect.set(x + 0.75f * d, y + 0.75f * d, x + cell - 0.75f * d, y + cell - 0.75f * d)
            val future = day.isAfter(today)
            val m = data[day] ?: 0L
            val lv = if (future) -1 else level(m)
            line.pathEffect = null
            val label: String
            if (lv < 0) {
                // Будущий день: пунктир и число месяца
                line.color = RtColors.withAlpha(ink3, 0.55f * fade); line.strokeWidth = 1.3f * d
                line.pathEffect = DashPathEffect(floatArrayOf(3 * d, 2.5f * d), 0f)
                canvas.drawRoundRect(rect, radius, radius, line)
                text.color = RtColors.withAlpha(ink3, fade)
                label = day.dayOfMonth.toString()
            } else if (lv == 0) {
                // Прошедший день без чтения: только контур и 0
                line.color = RtColors.withAlpha(ink2, (if (eink) 1f else 0.45f) * fade); line.strokeWidth = (if (eink) 1.5f else 1.3f) * d
                canvas.drawRoundRect(rect, radius, radius, line)
                text.color = RtColors.withAlpha(ink2, fade)
                label = "0"
            } else {
                val c = RtTheme.heat(context, lv)
                fill.color = c
                fill.alpha = (android.graphics.Color.alpha(c) * fade).toInt()
                canvas.drawRoundRect(rect, radius, radius, fill)
                text.color = RtColors.withAlpha(RtColors.onColor(c), fade)
                label = m.toString()
            }
            if (day == selected) {
                line.pathEffect = null; line.color = ink; line.strokeWidth = 2 * d
                canvas.drawRoundRect(rect, radius, radius, line)
            }
            text.textSize = (if (label.length >= 4) 10.5f else 13f) * d
            text.typeface = if (lv > 0) Fonts.semibold(context) else Fonts.regular(context)
            canvas.drawText(label, rect.centerX(), rect.centerY() - (text.ascent() + text.descent()) / 2, text)
        }
        if (dragX != 0f) canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel(); anim = null
                if (dragX != 0f) { dragX = 0f; invalidate() }
                downX = event.x; downY = event.y
                dragging = false
                // Пока не ясно, куда палец, экраны не листаются: жест вбок принадлежит календарю
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX; val dy = event.y - downY
                if (!dragging) {
                    if (abs(dy) > slop && abs(dy) > abs(dx)) {
                        // Вертикально — это прокрутка статистики, отдаём жест
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return false
                    }
                    if (abs(dx) > slop) dragging = true
                }
                if (dragging) {
                    // К будущему месяцу не пускает: лента лишь немного поддаётся и возвращается
                    dragX = if (dx < 0 && month >= current) dx * 0.15f else dx
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (dragging) {
                    val dx = event.x - downX
                    val far = abs(dx) > 56 * d
                    when {
                        far && dx > 0 -> flip(-1)
                        far && dx < 0 && month < current -> flip(1)
                        else -> settle()
                    }
                    dragging = false
                } else {
                    tap(event.x, event.y)
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = false
                settle()
            }
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        val gap = 5 * d
        val cell = (width - gap * 6) / 7f
        val col = (x / (cell + gap)).toInt()
        val row = (y / (cell + gap)).toInt()
        val i = row * 7 + col - offset
        if (col in 0..6 && i in 0 until days) {
            val day = first.plusDays(i.toLong())
            if (!day.isAfter(today)) {
                selected = day
                invalidate()
                onDaySelected?.invoke(day, data[day] ?: 0)
            }
        }
    }

    /** Вернуть сетку на место. */
    private fun settle() {
        if (dragX == 0f) return
        animateDrag(dragX, 0f, null)
    }

    /** Перелистнуть на месяц: dir = -1 — прошлый, 1 — следующий. Сетка уезжает и въезжает с другой стороны. */
    private fun flip(dir: Int) {
        val motion = RtTheme.motion(context) && !RtTheme.isEink(context)
        val apply = {
            month = month.plusMonths(dir.toLong())
            selected = null
            requestLayout()
            onMonthChanged?.invoke(month, month == current)
        }
        if (!motion) { dragX = 0f; apply(); invalidate(); return }
        val out = if (dir < 0) width.toFloat() else -width.toFloat()
        animateDrag(dragX, out) {
            apply()
            animateDrag(-out * 0.6f, 0f, null)
        }
    }

    private fun animateDrag(from: Float, to: Float, end: (() -> Unit)?) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(from, to).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener { dragX = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) { dragX = to; end?.invoke() }
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel(); anim = null
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean = super.performClick()
}
