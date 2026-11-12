package com.readingtracker.service

import android.content.Context

/**
 * Что служба специальных возможностей прочитала с экрана читалки. ЛитРес — по журналу
 * разведки 26–29 октября 2026; AlReader и «Свет» — 1–4 ноября (их разбор в ReaderAccessibilityService):
 * - экран чтения: id `book_name` — название, на каждой странице;
 * - меню читалки: `book_name_title` — название, `progress_text_progress` — «158 из 1329»;
 * - библиотека и магазин: `textViewBookName` + `textViewAuthor` в одной карточке — отсюда автор.
 * Заполняется службой спецвозможностей, читается службой отслеживания.
 */
object ReaderScreen {
    /** Последнее название с экрана чтения. */
    @Volatile var title: String? = null
        private set
    @Volatile var titlePkg: String? = null
        private set
    @Volatile var titleAt = 0L
        private set

    /** Страницы из меню читалки: «158 из 1329». */
    @Volatile var pagesRead = 0
        private set
    @Volatile var pagesTotal = 0
        private set
    @Volatile var pagesPkg: String? = null
        private set
    @Volatile var pagesAt = 0L
        private set

    /** Адрес из строки браузера (чтение «В интернете»). */
    @Volatile var webUrl: String? = null
        private set
    @Volatile var webUrlAt = 0L
        private set

    fun seeUrl(u: String) { webUrl = u; webUrlAt = System.currentTimeMillis() }

    /** Читалка сама сказала, что книга прочитана (Яндекс Книги: «Поздравляем! Вы прочитали еще одну книгу»). */
    @Volatile var finishedTitle: String? = null
        private set
    @Volatile var finishedPkg: String? = null
        private set
    @Volatile var finishedAt = 0L
        private set

    fun seeFinished(pkg: String, t: String) {
        if (pkg == finishedPkg && t == finishedTitle && System.currentTimeMillis() - finishedAt < 60_000) return
        finishedTitle = t; finishedPkg = pkg; finishedAt = System.currentTimeMillis()
    }

    private val PAGES = Regex("(\\d+)\\s*из\\s*(\\d+)")

    /** Отметка времени обновляется и при том же названии: значит, книга сейчас на экране. */
    fun seeTitle(pkg: String, t: String) {
        title = t; titlePkg = pkg; titleAt = System.currentTimeMillis()
    }

    fun seePages(pkg: String, text: String?) {
        val m = PAGES.find(text ?: return) ?: return
        val read = m.groupValues[1].toIntOrNull() ?: return
        val total = m.groupValues[2].toIntOrNull() ?: return
        if (total <= 0 || read > total) return
        if (pkg == pagesPkg && read == pagesRead && total == pagesTotal) return
        pagesRead = read; pagesTotal = total; pagesPkg = pkg; pagesAt = System.currentTimeMillis()
    }

    // ── Авторы из библиотеки ЛитРес: название → автор ────────────────────────

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("litres_authors", Context.MODE_PRIVATE)

    fun rememberAuthor(c: Context, title: String, author: String) {
        val key = normalize(title)
        if (key.isEmpty() || author.isBlank()) return
        val p = prefs(c)
        if (p.getString(key, null) == author) return
        // Не больше 500 книг: при переполнении журнал авторов начинается заново
        if (p.all.size > 500) p.edit().clear().apply()
        p.edit().putString(key, author.trim()).apply()
    }

    /** Автор книги, если трекер видел её в библиотеке; название с экрана может быть обрезано многоточием. */
    fun authorFor(c: Context, title: String): String? {
        val p = prefs(c)
        val key = normalize(title)
        if (key.isEmpty()) return null
        p.getString(key, null)?.let { return it }
        return p.all.entries.firstOrNull { sameTitle(it.key, title) }?.value as? String
    }

    // ── Жанры из читалки: название → наши жанры (через «|») ───────────────────

    private fun gPrefs(c: Context) = c.applicationContext.getSharedPreferences("reader_genres", Context.MODE_PRIVATE)

    /** Жанры AlReader («Фантастика, Фэнтэзи») → наши. Неясные (Проза, Приключения, Юмор…) пропускаются. */
    private val GENRE_MAP = mapOf(
        "фантастика" to "научная фантастика", "фэнтэзи" to "фэнтези", "фэнтези" to "фэнтези",
        "история" to "историческая литература", "детективы" to "детектив и триллер",
        "триллеры" to "детектив и триллер", "детское" to "детская", "поэзия" to "поэзия",
        "драматургия" to "пьеса", "классика" to "классика", "биографии" to "биография и мемуары",
        "мемуары" to "биография и мемуары", "научно-популярное" to "нон-фикшн", "публицистика" to "нон-фикшн"
    )

    fun rememberGenres(c: Context, title: String, raw: String) {
        val key = normalize(title)
        val mapped = raw.split(',', ' ').map { it.trim().lowercase().trimEnd('.') }.mapNotNull { GENRE_MAP[it] }.distinct()
        if (key.isEmpty() || mapped.isEmpty()) return
        val p = gPrefs(c)
        if (p.all.size > 500) p.edit().clear().apply()
        p.edit().putString(key, com.readingtracker.data.Book.joinGenres(mapped)).apply()
    }

    fun genresFor(c: Context, title: String): String? {
        val p = gPrefs(c)
        val key = normalize(title)
        if (key.isEmpty()) return null
        p.getString(key, null)?.let { return it }
        return p.all.entries.firstOrNull { sameTitle(it.key, title) }?.value as? String
    }

    /** Названия совпадают без учёта регистра, кавычек и многоточия в конце обрезанного названия. */
    fun sameTitle(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val x = normalize(a); val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val cutX = a.trim().endsWith("…") || a.trim().endsWith("...")
        val cutY = b.trim().endsWith("…") || b.trim().endsWith("...")
        return (cutX && x.length >= 6 && y.startsWith(x)) || (cutY && y.length >= 6 && x.startsWith(y))
    }

    fun normalize(t: String): String = t.lowercase()
        .replace('ё', 'е')
        .replace(Regex("[«»\"“”„…]|\\.\\.\\."), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
