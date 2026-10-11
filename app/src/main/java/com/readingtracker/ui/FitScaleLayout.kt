package com.readingtracker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup

/**
 * Держит экран целиком без прокрутки: единственный ребёнок раскладывается
 * по ширине макета (390 dp) и затем равномерно масштабируется так,
 * чтобы поместиться и по ширине, и по высоте. Пропорции макета не меняются
 * ни от размера экрана, ни от системного размера шрифта.
 */
class FitScaleLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    var designWidthDp = 390f
    var maxScale = 1.25f
    private var scale = 1f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val child = if (childCount > 0) getChildAt(0) else null
        if (child == null) { setMeasuredDimension(w, h); return }

        val dw = (designWidthDp * resources.displayMetrics.density).toInt()
        child.measure(
            MeasureSpec.makeMeasureSpec(dw, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val ch = child.measuredHeight.coerceAtLeast(1)
        val byWidth = w / dw.toFloat()
        val byHeight = if (hMode == MeasureSpec.UNSPECIFIED) Float.MAX_VALUE else h / ch.toFloat()
        scale = minOf(byWidth, byHeight, maxScale)
        val outH = if (hMode == MeasureSpec.UNSPECIFIED) (ch * scale).toInt() else h
        setMeasuredDimension(w, outH)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val child = if (childCount > 0) getChildAt(0) else return
        child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scale
        child.scaleY = scale
        child.translationX = (width - child.measuredWidth * scale) / 2f
        child.translationY = 0f
    }
}
