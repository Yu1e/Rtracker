package com.readingtracker.ui.welcome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.readingtracker.MainActivity
import com.readingtracker.Prefs
import com.readingtracker.ui.Fonts
import com.readingtracker.ui.RtTheme

/**
 * Первый запуск после установки: сразу выбрать оформление, чтобы на электронных чернилах
 * не искать эту настройку. Экран чёрно-белый, крупный и без движения — читается на любом экране.
 */
class WelcomeActivity : Activity() {

    companion object {
        /** Нужно ли показать экран: свежая установка, оформление ещё не выбиралось. */
        fun needed(c: Context): Boolean {
            if (Prefs.welcomed(c)) return false
            val fresh = try {
                val info = c.packageManager.getPackageInfo(c.packageName, 0)
                info.firstInstallTime == info.lastUpdateTime
            } catch (_: Exception) { false }
            if (!fresh || Prefs.hasStyle(c)) { Prefs.setWelcomed(c); return false }
            return true
        }
    }

    private val dp get() = resources.displayMetrics.density
    private var style = "glass"
    private var night = "system"
    private val styleViews = mutableListOf<Pair<String, LinearLayout>>()
    private val nightViews = mutableListOf<Pair<String, TextView>>()
    private lateinit var nightBlock: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        style = Prefs.style(this); night = Prefs.night(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(px(22), px(28), px(22), px(22))
        }
        root.addView(text("оформление", 28f, bold = true))
        root.addView(text("Потом его можно сменить в настройках → «оформление».", 16f).apply {
            setPadding(0, px(6), 0, px(18))
        })
        root.addView(option("glass", "стекло", "цветной свет и матовые плитки"))
        root.addView(option("mono", "ч/б", "спокойное, чёрное и белое"))
        root.addView(option("eink", "электронные чернила", "для читалок вроде Onyx Boox: без движения, крупные рамки"))

        nightBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nightBlock.addView(text("светлая или тёмная", 16f).apply { setPadding(0, px(14), 0, px(8)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((key, label) in listOf("system" to "как в системе", "light" to "светлая", "dark" to "тёмная")) {
            val chip = text(label, 16f).apply {
                setPadding(px(14), px(9), px(14), px(9))
                setOnClickListener { night = key; refresh() }
            }
            row.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(8) })
            nightViews += key to chip
        }
        nightBlock.addView(row)
        root.addView(nightBlock)

        val next = text("дальше", 20f, bold = true).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = box(Color.BLACK, Color.BLACK, 14f)
            setPadding(0, px(16), 0, px(16))
            setOnClickListener { finishChoice() }
        }
        root.addView(next, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(28) })

        setContentView(ScrollView(this).apply { setBackgroundColor(Color.WHITE); addView(root) })
        Fonts.apply(root)
        refresh()
    }

    private fun option(key: String, title: String, sub: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(14), px(16), px(14))
            isClickable = true
            setOnClickListener { style = key; refresh() }
        }
        card.addView(text(title, 20f, bold = true))
        card.addView(text(sub, 14f).apply { setPadding(0, px(3), 0, 0) })
        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(12) }
        styleViews += key to card
        return card
    }

    /** Выбранное — толстая чёрная рамка и «✓»; так видно и на чернилах. */
    private fun refresh() {
        for ((key, card) in styleViews) {
            val on = key == style
            card.background = box(Color.WHITE, Color.BLACK, 12f, if (on) 3.5f else 1.2f)
            (card.getChildAt(0) as TextView).text =
                (if (on) "✓ " else "") + when (key) { "glass" -> "стекло"; "mono" -> "ч/б"; else -> "электронные чернила" }
        }
        // У чернил всегда светлая
        nightBlock.visibility = if (style == "eink") View.GONE else View.VISIBLE
        for ((key, chip) in nightViews) {
            val on = key == night
            chip.setTextColor(if (on) Color.WHITE else Color.BLACK)
            chip.background = box(if (on) Color.BLACK else Color.WHITE, Color.BLACK, 20f, 1.5f)
        }
    }

    private fun finishChoice() {
        Prefs.setStyle(this, style)
        Prefs.setNight(this, night)
        Prefs.setWelcomed(this)
        RtTheme.applyNight(this)
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        overridePendingTransition(0, 0)
        finish()
    }

    private fun text(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextColor(Color.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun box(fill: Int, stroke: Int, radius: Float, width: Float = 1.5f) = GradientDrawable().apply {
        setColor(fill); cornerRadius = radius * dp; setStroke((width * dp).toInt().coerceAtLeast(1), stroke)
    }

    private fun px(v: Int) = (v * dp).toInt()
}
