package com.readingtracker.rewards

import android.app.Activity
import android.view.ViewGroup

/** Очередь праздников: по одному, с короткой паузой между ними. */
object Celebrations {
    private val queue = ArrayDeque<Achievement>()
    private var playing = false
    private var demoIndex = 0

    fun play(activity: Activity, list: List<Achievement>) {
        if (list.isEmpty()) return
        queue.addAll(list)
        if (!playing) next(activity)
    }

    private fun next(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) { queue.clear(); playing = false; return }
        val a = queue.removeFirstOrNull()
        if (a == null) { playing = false; return }
        playing = true
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: run { playing = false; return }
        val view = CelebrationView(activity, a) { v ->
            root.removeView(v)
            root.postDelayed({ next(activity) }, 250)
        }
        root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Показать эффект из настроек: каждый раз следующий. */
    fun demo(activity: Activity) {
        val samples = listOf(
            Achievement("demo", Effect.RIBBONS, "дочитано", "Мастер и Маргарита", "Михаил Булгаков"),
            Achievement("demo", Effect.BUBBLES, "часов с книгой", "100"),
            Achievement("demo", Effect.FIREWORK, "книг дочитано", "25"),
            Achievement("demo", Effect.LEAVES, "дней подряд", "30"),
            Achievement("demo", Effect.SPARKLES, "дней с книгой", "365"),
            Achievement("demo", Effect.CONFETTI, "цель дня взята", "60 мин")
        )
        play(activity, listOf(samples[demoIndex++ % samples.size]))
    }
}
