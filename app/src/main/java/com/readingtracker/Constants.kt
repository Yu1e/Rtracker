package com.readingtracker

import android.content.Context
import android.content.Intent

object Constants {

    // Известные package name читалок. Дополнительно приложения ищутся по названию
    // среди установленных (см. detectTrackedApps), так что точное имя пакета не критично.
    private val KNOWN_PACKAGES = mapOf(
        "ru.litres.android" to "ЛитРес",
        "ru.yandex.mobile.books" to "Яндекс Книги",
        "ru.yandex.books" to "Яндекс Книги",
        "com.neverland.alreader" to "AlReader",
        "com.neverland.alreaderpro" to "AlReader",
        "ru.rsl.svetApp" to "Свет"
    )

    // Ключевые слова в названии приложения (в нижнем регистре)
    private val LABEL_KEYWORDS = mapOf(
        "литрес" to "ЛитРес",
        "litres" to "ЛитРес",
        "яндекс книги" to "Яндекс Книги",
        "яндекс.книги" to "Яндекс Книги",
        "alreader" to "AlReader"
    )

    // Точное совпадение названия (слишком общие слова, чтобы искать вхождение)
    private val LABEL_EXACT = mapOf(
        "свет" to "Свет",
        "свет нэб" to "Свет"
    )

    /** Ищет среди установленных приложений читалки. Возвращает package -> название. */
    fun detectTrackedApps(context: Context): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = try {
            pm.queryIntentActivities(launcher, 0)
        } catch (e: Exception) {
            emptyList()
        }
        for (info in installed) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue
            val known = KNOWN_PACKAGES[pkg]
            if (known != null) {
                result[pkg] = known
                continue
            }
            val label = info.loadLabel(pm).toString().lowercase()
            val match = LABEL_KEYWORDS.entries.firstOrNull { label.contains(it.key) }?.value
                ?: LABEL_EXACT[label.trim()]
            if (match != null) result[pkg] = match
        }
        return result
    }

    /** Варианты «где читаю» для ручного добавления: название → package. */
    fun sourceOptions(context: Context): List<Pair<String, String>> {
        val detected = detectTrackedApps(context)
        fun pkgFor(name: String, fallback: String) =
            detected.entries.firstOrNull { it.value == name }?.key ?: fallback
        return listOf(
            "ЛитРес" to pkgFor("ЛитРес", "ru.litres.android"),
            "Яндекс Книги" to pkgFor("Яндекс Книги", "ru.yandex.mobile.books"),
            "AlReader" to pkgFor("AlReader", "com.neverland.alreader"),
            "Свет" to pkgFor("Свет", "ru.rsl.svetApp"),
            "Бумажная книга" to com.readingtracker.data.Book.PKG_PAPER,
            "Другое" to com.readingtracker.data.Book.PKG_OTHER
        )
    }

    const val NOTIFICATION_CHANNEL_ID = "reading_tracker_service"
    const val REMINDER_CHANNEL_ID = "reading_reminders"
    const val NOTIFICATION_ID = 1001

    const val ABANDONED_DAYS_THRESHOLD = 21

    // Как часто проверять, какое приложение на переднем плане
    const val POLL_INTERVAL_MS = 2500L
}
