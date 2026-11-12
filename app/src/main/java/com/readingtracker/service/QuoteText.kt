package com.readingtracker.service

/**
 * Текст страницы для окна цитаты: где цитата на странице и как сдвигать её границы
 * по словам и предложениям, как подклеивать страницы для длинной цитаты.
 *
 * Страница — абзацы через пустую строку. Цитата — отрезок [start, end) этой строки.
 * Без Android: только строки.
 */
object QuoteText {

    // ── Подготовка страницы ──────────────────────────────────────────────────

    /** Части слова через дефис, которые остаются с дефисом (кое-что, из-за, что-то, по-моему). */
    private val KEEP_LEFT = setOf("кое", "кой", "из", "по", "во", "в", "на", "полу", "пол", "вице", "экс", "северо", "юго", "обер", "унтер", "лейб", "штаб", "контр")
    private val KEEP_RIGHT = setOf("то", "либо", "нибудь", "ка", "таки", "де", "с", "тка")

    /**
     * Абзацы страницы → одна строка. Пробелы внутри абзаца — одиночные.
     * dehyphen — у ЛитРес в тексте страницы стоят переносы («Од-нако»): дефис между двумя
     * строчными русскими буквами убирается, кроме настоящих (кое-что, что-то, по-моему).
     */
    fun cleanPage(paragraphs: List<String>, dehyphen: Boolean): String =
        paragraphs.map { p ->
            val one = p.replace('­', ' ').replace(Regex("[ \\t\\r\\n\\u00A0]+"), " ").trim()
            if (dehyphen) unhyphen(one) else one
        }.filter { it.isNotEmpty() }.distinct().joinToString("\n\n")

    private val HYPHEN = Regex("([А-Яа-яЁё]+)-([а-яё]+)")

    fun unhyphen(s: String): String = HYPHEN.replace(s) { m ->
        val l = m.groupValues[1]; val r = m.groupValues[2]
        val ll = l.lowercase()
        if (!l.last().isLowerCase() || ll in KEEP_LEFT || r in KEEP_RIGHT) m.value else l + r
    }

    // ── Где цитата на странице ──────────────────────────────────────────────

    /** Знаки, которые при сравнении не считаются: пробелы, переводы строк, дефисы, мягкие переносы. */
    private fun skip(c: Char) = c.isWhitespace() || c == '-' || c == '­' || c == '‐' || c == '‑'

    private fun canon(c: Char): Char = when (val l = c.lowercaseChar()) {
        'ё' -> 'е'
        '«', '»', '„', '“', '”' -> '"'
        '’' -> '\''
        '–', '—' -> '-'
        else -> l
    }

    /** Отрезок страницы, где стоит цитата (без учёта пробелов, дефисов, регистра), или null. */
    fun locate(page: String, quote: String): IntRange? {
        if (quote.isBlank() || page.isEmpty()) return null
        val pIdx = ArrayList<Int>(page.length)
        val pc = StringBuilder()
        for ((i, ch) in page.withIndex()) if (!skip(ch)) { pc.append(canon(ch)); pIdx += i }
        val qc = StringBuilder()
        for (ch in quote) if (!skip(ch)) qc.append(canon(ch))
        if (qc.isEmpty()) return null
        val at = pc.indexOf(qc.toString())
        if (at < 0) return null
        return pIdx[at]..pIdx[at + qc.length - 1]
    }

    // ── Слова и предложения ──────────────────────────────────────────────────

    private fun isWord(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'
    private fun isEnd(c: Char) = c == '.' || c == '!' || c == '?' || c == '…'
    private fun isClose(c: Char) = c == '»' || c == '"' || c == '”' || c == ')' || c == ']' || c == '’'

    /** Начало предложения в позиции i: перед ней (без пробелов) конец предложения, абзац или начало страницы. */
    private fun sentenceStartAt(p: String, i: Int): Boolean {
        var k = i - 1
        while (k >= 0 && p[k] == ' ') k--
        if (k < 0) return true
        if (p[k] == '\n') return true
        var j = k
        while (j >= 0 && isClose(p[j])) j--
        return j >= 0 && isEnd(p[j]) && j < i - 1
    }

    /** Ближайшее начало слова не правее i (i — внутри слова или на его начале). */
    private fun wordStart(p: String, i: Int): Int {
        var k = i
        while (k > 0 && (isWord(p[k - 1]) || (p[k - 1] == '-' && k - 2 >= 0 && isWord(p[k - 2])))) k--
        return k
    }

    /** Начало цитаты: на одно слово раньше. */
    fun startPlusWord(p: String, s: Int): Int {
        var k = s
        // назад через пробелы и знаки до конца предыдущего слова
        while (k > 0 && !isWord(p[k - 1])) k--
        if (k == 0) return s.coerceAtMost(firstWord(p, 0))
        val w = wordStart(p, k - 1)
        return leadIn(p, w)
    }

    /** Начало цитаты: на одно слово позже (не дальше конца). */
    fun startMinusWord(p: String, s: Int, e: Int): Int {
        var k = s
        while (k < e && !isWord(p[k])) k++
        while (k < e && (isWord(p[k]) || p[k] == '-')) k++
        val n = firstWord(p, k)
        return if (n < e) leadIn(p, n) else s
    }

    /** Начало цитаты: к началу предложения; если уже там — к началу предыдущего. */
    fun startPlusSentence(p: String, s: Int): Int {
        val w = firstWord(p, s)
        var k = if (sentenceStartAt(p, w)) w - 1 else w
        while (k > 0 && !sentenceStartAt(p, k)) k--
        if (k < 0) k = 0
        return leadIn(p, firstWord(p, k))
    }

    /** Начало цитаты: к началу следующего предложения (если оно внутри цитаты). */
    fun startMinusSentence(p: String, s: Int, e: Int): Int {
        var k = firstWord(p, s) + 1
        while (k < e && !sentenceStartAt(p, k)) k++
        if (k >= e) return s
        val n = firstWord(p, k)
        return if (n < e) leadIn(p, n) else s
    }

    /** Конец цитаты: на одно слово дальше, со знаками препинания после него. */
    fun endPlusWord(p: String, e: Int): Int {
        var k = e
        while (k < p.length && !isWord(p[k])) {
            if (p[k] == '\n') { while (k < p.length && !isWord(p[k])) k++; break }
            k++
        }
        while (k < p.length && (isWord(p[k]) || (p[k] == '-' && k + 1 < p.length && isWord(p[k + 1])))) k++
        return tail(p, k)
    }

    /** Конец цитаты: на одно слово короче. */
    fun endMinusWord(p: String, s: Int, e: Int): Int {
        var k = e
        while (k > s && !isWord(p[k - 1])) k--
        while (k > s && (isWord(p[k - 1]) || (p[k - 1] == '-' && k - 2 >= s && isWord(p[k - 2])))) k--
        while (k > s && p[k - 1].isWhitespace()) k--
        // висячий дефис или тире в конце не оставляем
        while (k > s && (p[k - 1] == '—' || p[k - 1] == '–' || p[k - 1] == '-')) { k--; while (k > s && p[k - 1].isWhitespace()) k-- }
        return if (k > s) k else e
    }

    /** Конец цитаты: до конца предложения; если уже там — до конца следующего. */
    fun endPlusSentence(p: String, e: Int): Int {
        var k = e
        while (k < p.length && p[k].isWhitespace()) k++
        // уже стоим на конце предложения — ищем следующий
        while (k < p.length) {
            if (isEnd(p[k])) {
                var j = k
                while (j < p.length && isEnd(p[j])) j++
                while (j < p.length && isClose(p[j])) j++
                if (j >= p.length || p[j].isWhitespace()) return j
                k = j; continue
            }
            if (p[k] == '\n' && k > e) {
                var j = k
                while (j > e && p[j - 1].isWhitespace()) j--
                return j
            }
            k++
        }
        return p.length
    }

    /** Конец цитаты: убрать последнее предложение (если до него в цитате есть другое). */
    fun endMinusSentence(p: String, s: Int, e: Int): Int {
        var k = e
        while (k > s && (p[k - 1].isWhitespace() || isClose(p[k - 1]) || isEnd(p[k - 1]))) k--
        while (k > s) {
            val c = p[k - 1]
            if (c == '\n') { var j = k - 1; while (j > s && p[j - 1].isWhitespace()) j--; return if (j > s) j else e }
            if (isEnd(c)) {
                var j = k
                while (j < e && isClose(p[j])) j++
                if (j >= e || p[j].isWhitespace()) return j
            }
            k--
        }
        return e
    }

    private fun firstWord(p: String, i: Int): Int {
        var k = i
        while (k < p.length && !isWord(p[k])) k++
        return k
    }

    /** Открывающие кавычки и скобки перед словом идут в цитату. */
    private fun leadIn(p: String, w: Int): Int {
        var k = w
        while (k > 0 && (p[k - 1] == '«' || p[k - 1] == '"' || p[k - 1] == '„' || p[k - 1] == '“' || p[k - 1] == '(')) k--
        return k
    }

    /** Знаки препинания сразу после слова идут в цитату (запятая, точка, кавычка). */
    private fun tail(p: String, k0: Int): Int {
        var k = k0
        while (k < p.length && !p[k].isWhitespace() && !isWord(p[k]) && p[k] != '«' && p[k] != '(') k++
        return k
    }

    // ── Длинная цитата: страницы подклеиваются ───────────────────────────────

    class Merge(val text: String, val shift: Int, val pageStart: Int, val pageEnd: Int)

    /**
     * Подклеить страницу page к тексту buf: в конец (atEnd) или в начало. Абзацы, которые уже есть
     * в buf (в «Свете» абзац на стыке страниц виден целиком на обеих), второй раз не добавляются.
     * shift — на сколько сдвинулся старый текст (при подклейке в начало), pageStart..pageEnd — где
     * в новом тексте оказалась страница.
     */
    fun merge(buf: String, page: String, atEnd: Boolean): Merge {
        if (buf.isEmpty()) return Merge(page, 0, 0, page.length)
        val have = buf.split("\n\n")
        val paras = page.split("\n\n").filter { it.isNotBlank() }
        val fresh = paras.filter { it !in have }
        if (atEnd) {
            val text = if (fresh.isEmpty()) buf else buf + "\n\n" + fresh.joinToString("\n\n")
            val last = paras.lastOrNull() ?: return Merge(buf, 0, buf.length, buf.length)
            val firstPos = paras.firstOrNull()?.let { text.indexOf(it) }?.takeIf { it >= 0 } ?: buf.length
            val lastPos = text.lastIndexOf(last)
            return Merge(text, 0, firstPos, if (lastPos >= 0) lastPos + last.length else text.length)
        } else {
            val add = if (fresh.isEmpty()) "" else fresh.joinToString("\n\n") + "\n\n"
            val text = add + buf
            val first = paras.firstOrNull() ?: return Merge(buf, 0, 0, 0)
            val firstPos = text.indexOf(first).coerceAtLeast(0)
            val last = paras.last()
            val lastPos = text.indexOf(last, firstPos)
            return Merge(text, add.length, firstPos, if (lastPos >= 0) lastPos + last.length else add.length)
        }
    }
}
