package com.readingtracker.ui

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Rubik из assets (переменный шрифт): обычный 400, средний 500, полужирный 600. */
object Fonts {
    private var regular: Typeface? = null
    private var medium: Typeface? = null
    private var semibold: Typeface? = null

    private fun load(c: Context, weight: Int): Typeface = try {
        Typeface.Builder(c.applicationContext.assets, "fonts/Rubik.ttf")
            .setFontVariationSettings("'wght' $weight")
            .setWeight(weight)
            .build() ?: Typeface.DEFAULT
    } catch (e: Exception) {
        Typeface.DEFAULT
    }

    fun regular(c: Context): Typeface = regular ?: load(c, 400).also { regular = it }
    fun medium(c: Context): Typeface = medium ?: load(c, 500).also { medium = it }
    fun semibold(c: Context): Typeface = semibold ?: load(c, 600).also { semibold = it }

    /** Проставить Rubik всем подписям внутри: полужирные — 600, остальные — 400. */
    fun apply(root: View) {
        val c = root.context
        when (root) {
            is TextView -> {
                val bold = root.typeface?.isBold == true
                root.typeface = if (bold) semibold(c) else regular(c)
            }
            is ViewGroup -> for (i in 0 until root.childCount) apply(root.getChildAt(i))
        }
    }
}
