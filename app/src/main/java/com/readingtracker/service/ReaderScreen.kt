package com.readingtracker.service

import android.content.Context

/**
 * Что служба специальных возможностей прочитала с экрана читалки (пока только ЛитРес,
 * по журналу разведки 26–29 октября 2026):
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
