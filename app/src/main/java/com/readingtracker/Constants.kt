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
        "com.neverland.alreader" to "Alreader",
        "com.neverland.alreaderpro" to "Alreader"
    )

    // Ключевые слова в названии приложения (в нижнем регистре)
    private val LABEL_KEYWORDS = mapOf(
        "литрес" to "ЛитРес",
        "litres" to "ЛитРес",
        "яндекс книги" to "Яндекс Книги",
        "яндекс.книги" to "Яндекс Книги",
        "alreader" to "Alreader"
    )

    /** Ищет среди установленных приложений три читалки. Возвращает package -> название. */
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
            KNOWN_PACKAGES[pkg]?.let { result[pkg] = it; continue }
            val label = info.loadLabel(pm).toString().lowercase()
            LABEL_KEYWORDS.entries.firstOrNull { label.contains(it.key) }?.let {
                result[pkg] = it.value
            }
        }
        return result
    }

    const val NOTIFICATION_CHANNEL_ID = "reading_tracker_service"
    const val NOTIFICATION_ID = 1001

    const val ABANDONED_DAYS_THRESHOLD = 21

    // Как часто проверять, какое приложение на переднем плане
    const val POLL_INTERVAL_MS = 2500L
}
