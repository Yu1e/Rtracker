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
    val lastNotifiedAt: Long? = null,
    /** Время, добавленное вручную (книга внесена руками); уже входит в totalMinutes. */
    val manualMinutes: Long = 0,
    /** Страница, на которой книга открыта в читалке («158 из 1329»); 0 — неизвестно. */
    val pagesRead: Int = 0,
    /** Всего страниц; 0 — неизвестно. */
    val pagesTotal: Int = 0,
    /** Жанры через «|» в порядке списка Book.GENRES; пусто — не указан. */
    val genres: String = "",
    /** Книга «В интернете»: адрес, с которого читается (последняя открытая часть). */
    val url: String = ""
) {
    companion object {
        const val STATUS_READING = "reading"
        const val STATUS_FINISHED = "finished"
        const val STATUS_ABANDONED = "abandoned"
        const val STATUS_PAUSED = "paused"
        const val STATUS_REREADING = "rereading"

        /** Книга сейчас читается: впервые или снова. */
        fun isReading(status: String) = status == STATUS_READING || status == STATUS_REREADING
        /** Временный статус: книга открыта (читается, перечитывается или отложена). */
        fun isOpen(status: String) = isReading(status) || status == STATUS_PAUSED

        const val PKG_PAPER = "paper"
        const val PKG_OTHER = "other"
        const val PKG_ONLINE = "online"

        /** Основные жанры, без распыления. У книги их может быть несколько. */
        val GENRES = listOf(
            "классика", "современная проза", "детектив и триллер", "научная фантастика", "фэнтези",
            "историческая литература", "нон-фикшн", "биография и мемуары", "поэзия", "пьеса", "эпос",
            "детская", "другое"
        )

        fun splitGenres(s: String): List<String> = s.split('|').map { it.trim() }.filter { it.isNotEmpty() }

        /** Жанры в порядке общего списка, без повторов. */
        fun joinGenres(list: Collection<String>): String =
            list.distinct().sortedBy { GENRES.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }.joinToString("|")

        fun statusLabel(status: String) = when (status) {
            STATUS_READING -> "Читаю"
            STATUS_FINISHED -> "Дочитана"
            STATUS_ABANDONED -> "Брошена"
            STATUS_PAUSED -> "Отложена"
            STATUS_REREADING -> "Перечитываю"
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
