package com.readingtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val appPackage: String,
    val appName: String,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val status: String = STATUS_READING,
    /** Оценка 1–10, null = без оценки. На статус не влияет. */
    val rating: Int? = null,
    val totalMinutes: Long = 0,
    /** Заметки простым текстом. */
    val notes: String = "",
    /** Не спрашивать «дочитана?» раньше этого момента (после «позже»). */
    val statusAskAt: Long? = null,
    /** Начата новая книга после этой — уточнить статус в трекере. */
    val needsStatusCheck: Boolean = false,
    /** Когда последний раз показано уведомление о статусе. */
    val lastNotifiedAt: Long? = null
) {
    companion object {
        const val STATUS_READING = "reading"
        const val STATUS_FINISHED = "finished"
        const val STATUS_ABANDONED = "abandoned"
        const val STATUS_PAUSED = "paused"

        const val PKG_PAPER = "paper"
        const val PKG_OTHER = "other"

        fun statusLabel(status: String) = when (status) {
            STATUS_READING -> "Читаю"
            STATUS_FINISHED -> "Дочитана"
            STATUS_ABANDONED -> "Брошена"
            STATUS_PAUSED -> "Отложена"
            else -> status
        }

        /** «Автор — Название» с учётом пустых полей. */
        fun displayName(b: Book): String = when {
            b.title.isNotBlank() && b.author.isNotBlank() -> "${b.author} — «${b.title}»"
            b.title.isNotBlank() -> "«${b.title}»"
            b.author.isNotBlank() -> "${b.author} (без названия)"
            else -> "Без названия"
        }
    }
}
