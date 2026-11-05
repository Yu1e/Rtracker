package com.readingtracker.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.readingtracker.data.Book
import com.readingtracker.data.Reading
import com.readingtracker.data.Readings
import com.readingtracker.data.ReadingSession
import com.readingtracker.ui.book.formatMinutes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Где стоят теги: в YAML, первой строкой (после YAML), последней строкой файла. */
enum class TagPlace(val key: String) { YAML("yaml"), FIRST("first"), LAST("last") }

/** Что выгружать в статистике. */
enum class StatsPart { SUMMARY, DAYS, BOOKS, WHEN, LISTS, SESSIONS }

/** Что выводить в карточке книги. */
enum class BookField { MAIN, STATUS, TIME, NOTES }

data class ExportJob(
    val books: Boolean,
    val statuses: Set<String>,
    val tags: List<String>,
    val tagPlace: TagPlace,
    // Статистика
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val periodTitle: String = "",
    val periodFile: String = "",
    val parts: Set<StatsPart> = emptySet(),
    val csv: Boolean = false,
    // Карточки книг
    val singleFile: Boolean = true,
    val fields: Set<BookField> = emptySet()
)

class OutFile(val name: String, val text: String)

object Exporter {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val ru = Locale("ru")
    private val dayFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", ru)
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", ru)
    private val MONTHS_GEN = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    private fun Long.date(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    /** Теги без «#», по запятым и пробелам. */
    fun parseTags(s: String): List<String> =
        s.split(Regex("[,;\\s]+")).map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }.distinct()

    fun build(job: ExportJob, allBooks: List<Book>, allSessions: List<ReadingSession>,
              readings: List<Reading> = emptyList()): List<OutFile> {
        // «читаю» включает перечитывание
        val st = if (Book.STATUS_READING in job.statuses) job.statuses + Book.STATUS_REREADING else job.statuses
        val books = allBooks.filter { it.status in st }
        val ids = books.map { it.id }.toSet()
        val sessions = allSessions.filter { it.bookId in ids }
        partsOf = readings.groupBy { it.bookId }.mapNotNull { (id, rs) ->
            val bk = allBooks.firstOrNull { it.id == id } ?: return@mapNotNull null
            id to Readings.parts(bk, rs, allSessions)
        }.toMap()
        return if (job.books) bookFiles(job, books)
        else statsFiles(job, books, sessions, Readings.expand(books, sessions, readings))
    }

    /** Прочтения перечитанных книг (для полей «Когда», «Сколько», «Всего»). */
    private var partsOf: Map<Long, List<Readings.Part>> = emptyMap()

    /** Записать файлы в папку; одноимённые перезаписываются. Возвращает число файлов. */
    fun write(context: Context, folder: Uri, files: List<OutFile>): Int {
        val dir = DocumentFile.fromTreeUri(context, folder) ?: error("Нет доступа к папке")
        if (!dir.canWrite()) error("Нет доступа к папке, выбери её заново")
        for (f in files) {
            val doc = dir.findFile(f.name)?.takeIf { it.isFile }
                ?: dir.createFile("application/octet-stream", f.name)
                ?: error("Не удалось создать файл ${f.name}")
            val bytes = if (f.name.endsWith(".csv")) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + f.text.toByteArray()
            else f.text.toByteArray()
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { it.write(bytes) }
                ?: error("Не удалось записать ${f.name}")
        }
        return files.size
    }

    // ── Теги ──────────────────────────────────────────────────────────────────

    private fun withTags(body: String, job: ExportJob): String {
        if (job.tags.isEmpty()) return body
        val line = job.tags.joinToString(" ") { "#$it" }
        return when (job.tagPlace) {
            TagPlace.YAML -> "---\ntags:\n" + job.tags.joinToString("") { "  - $it\n" } + "---\n" + body
            TagPlace.FIRST -> "$line\n" + body
            TagPlace.LAST -> body.trimEnd('\n') + "\n\n$line\n"
        }
    }

    // ── Карточки книг ─────────────────────────────────────────────────────────

    fun statusWord(s: String) = when (s) {
        Book.STATUS_READING -> "читаю"
        Book.STATUS_FINISHED -> "дочитана"
        Book.STATUS_ABANDONED -> "брошена"
        Book.STATUS_PAUSED -> "отложена"
        Book.STATUS_REREADING -> "перечитываю"
        else -> s
    }

    /** «с 2 по 19 октября 2026», «с 28 сентября по 3 октября 2026», «с 2 октября 2026». */
    fun whenText(b: Book): String {
        val a = b.startDate.date()
        val end = b.endDate?.takeIf { !Book.isReading(b.status) }?.date()
        fun full(d: LocalDate) = "${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]} ${d.year}"
        return when {
            end == null || end.isBefore(a) -> "с ${full(a)}"
            end == a -> full(a)
            a.year != end.year -> "с ${full(a)} по ${full(end)}"
            a.monthValue != end.monthValue -> "с ${a.dayOfMonth} ${MONTHS_GEN[a.monthValue - 1]} по ${full(end)}"
            else -> "с ${a.dayOfMonth} по ${full(end)}"
        }
    }

    fun stars(r: Int) = "★".repeat(r.coerceIn(0, 10)) + "✩".repeat((10 - r).coerceIn(0, 10))

    private fun oneLine(s: String) = s.replace(Regex("\\s*\\n\\s*"), " ").trim()

    /** Поля книги по порядку; пустые поля тоже выводятся: «(Автор:: )». Жанров может быть несколько. */
    private fun fields(b: Book, f: Set<BookField>, withTitle: Boolean): List<String> {
        val out = mutableListOf<String>()
        fun field(name: String, value: String) { out += "($name:: ${oneLine(value)})" }
        if (BookField.MAIN in f) {
            field("Автор", b.author)
            if (withTitle) field("Название", b.title)
            val g = Book.splitGenres(b.genres)
            if (g.isEmpty()) field("Жанр", "") else out += g.joinToString(", ") { "(Жанр:: $it)" }
        }
        // Перечитанная книга: «Когда» и «Сколько» по прочтениям, через запятую, и «Всего»
        val parts = partsOf[b.id]?.takeIf { it.size >= 2 }
        if (BookField.STATUS in f) {
            field("Статус", statusWord(b.status))
            if (parts == null) field("Когда", whenText(b))
            else out += parts.joinToString(", ") {
                "(Когда:: ${whenText(b.copy(startDate = it.reading.startDate, endDate = it.reading.endDate, status = it.reading.status))})"
            }
        }
        if (BookField.TIME in f) {
            if (parts == null) field("Сколько", if (b.totalMinutes > 0) formatMinutes(b.totalMinutes) else "")
            else {
                out += parts.joinToString(", ") { "(Сколько:: ${if (it.minutes > 0) formatMinutes(it.minutes) else ""})" }
                field("Всего", formatMinutes(parts.sumOf { it.minutes }))
            }
        }
        if (BookField.STATUS in f) field("Источник", b.appName)
        if (BookField.TIME in f) field("Оценка", b.rating?.let { "${stars(it)} $it из 10" } ?: "")
        return out
    }

    private fun bookFiles(job: ExportJob, books: List<Book>): List<OutFile> {
        val sorted = books.sortedByDescending { it.startDate }
        if (job.singleFile) {
            val body = StringBuilder()
            for (b in sorted) {
                val name = oneLine(b.title).ifBlank { oneLine(b.author).ifBlank { "Без названия" } }
                val parts = fields(b, job.fields, withTitle = false).toMutableList()
                if (BookField.NOTES in job.fields) parts += "(Заметки:: ${oneLine(b.notes)})"
                body.append("- ").append(name)
                if (parts.isNotEmpty()) body.append(' ').append(parts.joinToString(" "))
                body.append("\n\n")
            }
            return listOf(OutFile("Книги.md", withTags(body.toString().trimEnd('\n') + "\n", job)))
        }
        val used = HashSet<String>()
        return sorted.map { b ->
            val body = StringBuilder()
            fields(b, job.fields, withTitle = true).forEach { body.append(it).append('\n') }
            if (BookField.NOTES in job.fields) {
                body.append("\n##### Заметки\n")
                if (b.notes.isNotBlank()) body.append(b.notes.trim()).append('\n')
            }
            OutFile(uniqueName(fileBase(b), used) + ".md", withTags(body.toString(), job))
        }
    }

    /** Имя файла — название книги без кавычек и знаков, которые нельзя в имени файла. */
    fun fileBase(b: Book): String {
        fun clean(s: String) = s.replace(Regex("[«»\"„“”‘’'`/\\\\:*?<>|\\n\\r\\t]"), "")
            .replace(Regex("\\s+"), " ").trim().trimEnd('.').take(120).trim()
        return clean(b.title).ifBlank { clean(b.author) }.ifBlank { "Без названия" }
    }

    private fun uniqueName(base: String, used: MutableSet<String>): String {
        var name = base; var n = 2
        while (!used.add(name.lowercase())) { name = "$base ($n)"; n++ }
        return name
    }

    // ── Статистика ────────────────────────────────────────────────────────────

    /** Минуты по дням из сессий (сессия делится на границе суток), только внутри периода. */
    private fun dayMinutes(sessions: List<ReadingSession>, from: LocalDate, to: LocalDate, now: Long): Map<LocalDate, Double> {
        val acc = HashMap<LocalDate, Double>()
        for (s in sessions) {
            val end = s.endTime ?: now
            var t = s.startTime
            while (t < end) {
                val d = t.date()
                val next = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val segEnd = minOf(end, next)
                if (!d.isBefore(from) && !d.isAfter(to)) acc[d] = (acc[d] ?: 0.0) + (segEnd - t) / 60_000.0
                t = segEnd
            }
        }
        return acc
    }

    /** Время, внесённое вручную, — поровну на дни книги; часть, попавшая в период. */
    private fun manualIn(b: Book, from: LocalDate, to: LocalDate): Double {
        if (b.manualMinutes <= 0) return 0.0
        val a = b.startDate.date()
        var z = (b.endDate ?: b.startDate).date()
        if (z.isBefore(a)) z = a
        val days = ChronoUnit.DAYS.between(a, z) + 1
        val lo = maxOf(a, from); val hi = minOf(z, to)
        if (hi.isBefore(lo)) return 0.0
        return b.manualMinutes.toDouble() * (ChronoUnit.DAYS.between(lo, hi) + 1) / days
    }

    private fun inPeriod(ms: Long?, from: LocalDate, to: LocalDate) =
        ms != null && !ms.date().isBefore(from) && !ms.date().isAfter(to)

    private fun fmt(m: Double) = formatMinutes(Math.round(m))

    private class Table(val title: String, val head: List<String>, val rows: List<List<String>>)

    private fun md(t: Table): String = buildString {
        append("| ").append(t.head.joinToString(" | ")).append(" |\n")
        append("|").append(t.head.joinToString("|") { " --- " }).append("|\n")
        for (r in t.rows) append("| ").append(r.joinToString(" | ") { it.replace("|", "/").replace("\n", " ") }).append(" |\n")
    }

    private fun csv(t: Table): String = buildString {
        fun cell(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        append(t.head.joinToString(";") { cell(it) }).append("\r\n")
        for (r in t.rows) append(r.joinToString(";") { cell(it) }).append("\r\n")
    }

    private fun statsFiles(job: ExportJob, books: List<Book>, sessions: List<ReadingSession>, byReading: List<Book>): List<OutFile> {
        val now = System.currentTimeMillis()
        val today = now.date()
        val first = (sessions.map { it.startTime } + books.map { it.startDate }).minOrNull()?.date() ?: today
        val from = job.from ?: first
        val to = job.to ?: today
        val days = dayMinutes(sessions, from, to, now)
        val booksById = books.associateBy { it.id }

        // Время книги за период: сессии внутри периода и доля внесённого вручную
        val perBook = HashMap<Long, Double>()
        for (s in sessions) {
            val m = dayMinutes(listOf(s), from, to, now).values.sum()
            if (m > 0) perBook[s.bookId] = (perBook[s.bookId] ?: 0.0) + m
        }
        var manualTotal = 0.0
        for (b in books) {
            val m = manualIn(b, from, to)
            if (m > 0) { perBook[b.id] = (perBook[b.id] ?: 0.0) + m; manualTotal += m }
        }
        val total = perBook.values.sum()
        val listed = books.filter {
            (perBook[it.id] ?: 0.0) >= 0.5 || inPeriod(it.startDate, from, to) ||
                (it.status != Book.STATUS_READING && inPeriod(it.endDate, from, to))
        }.sortedByDescending { perBook[it.id] ?: 0.0 }

        val readDays = days.filterValues { it >= 0.5 }.keys.sorted()
        val tables = mutableListOf<Table>()
        val out = StringBuilder()
        out.append("# Статистика чтения — ${job.periodTitle}\n\n")
        val statusNote = if (job.statuses.size == 4) "все" else
            listOf(Book.STATUS_READING to "читаю", Book.STATUS_FINISHED to "дочитаны",
                Book.STATUS_PAUSED to "отложены", Book.STATUS_ABANDONED to "брошены")
                .filter { it.first in job.statuses }.joinToString(", ") { it.second }
        out.append("период: ${periodText(from, to)} · книги: $statusNote\n")

        if (StatsPart.SUMMARY in job.parts) {
            var best = 0; var run = 0; var prev: LocalDate? = null
            for (d in readDays) { run = if (prev != null && d == prev.plusDays(1)) run + 1 else 1; best = maxOf(best, run); prev = d }
            val bestDay = days.maxByOrNull { it.value }?.takeIf { it.value >= 0.5 }
            val rows = mutableListOf(
                listOf("время чтения", fmt(total)),
                listOf("дней с чтением", "${readDays.size}"),
                listOf("в среднем за день чтения", if (readDays.isEmpty()) "—" else fmt(days.values.sum() / readDays.size)),
                listOf("лучший день", bestDay?.let { "${it.key.format(dayFmt)} — ${fmt(it.value)}" } ?: "—"),
                listOf("дней подряд, лучшая серия", "$best"),
                listOf("книг начато", "${books.count { inPeriod(it.startDate, from, to) }}"),
                listOf("книг дочитано", "${byReading.count { it.status == Book.STATUS_FINISHED && inPeriod(it.endDate, from, to) }}"),
                listOf("книг брошено", "${byReading.count { it.status == Book.STATUS_ABANDONED && inPeriod(it.endDate, from, to) }}")
            )
            if (manualTotal >= 0.5) rows.add(1, listOf("из них внесено вручную", fmt(manualTotal)))
            tables += Table("итоги", listOf("показатель", "значение"), rows)
        }
        if (StatsPart.DAYS in job.parts) {
            // Весь период день за днём, если он не длиннее года; иначе только дни с чтением
            val long = ChronoUnit.DAYS.between(from, to) > 366
            val list = if (long) readDays else generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
            tables += Table("день за днём", listOf("дата", "день недели", "минут"),
                list.map { d -> listOf(d.format(iso), WEEKDAYS[d.dayOfWeek.value - 1], "${Math.round(days[d] ?: 0.0)}") })
        }
        if (StatsPart.BOOKS in job.parts) {
            tables += Table("книги", listOf("книга", "автор", "жанр", "статус", "за период", "всего", "начата", "закончена", "оценка"),
                listed.map { b ->
                    listOf(b.title.ifBlank { "Без названия" }, b.author, Book.splitGenres(b.genres).joinToString(", "),
                        statusWord(b.status), fmt(perBook[b.id] ?: 0.0), formatMinutes(b.totalMinutes),
                        b.startDate.date().format(iso),
                        b.endDate?.takeIf { b.status != Book.STATUS_READING }?.date()?.format(iso) ?: "",
                        b.rating?.let { "$it" } ?: "")
                })
        }
        if (StatsPart.WHEN in job.parts) {
            val hours = DoubleArray(24); val wd = DoubleArray(7)
            for (s in sessions) {
                val end = s.endTime ?: now
                var t = s.startTime
                while (t < end) {
                    val zt = Instant.ofEpochMilli(t).atZone(zone)
                    val next = zt.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                    val segEnd = minOf(end, next)
                    val d = zt.toLocalDate()
                    if (!d.isBefore(from) && !d.isAfter(to)) {
                        hours[zt.hour] += (segEnd - t) / 60_000.0
                        wd[zt.dayOfWeek.value - 1] += (segEnd - t) / 60_000.0
                    }
                    t = segEnd
                }
            }
            tables += Table("часы суток", listOf("час", "минут"),
                (0..23).filter { hours[it] >= 0.5 }.map { listOf(String.format(ru, "%02d:00", it), "${Math.round(hours[it])}") })
            tables += Table("дни недели", listOf("день", "минут"), (0..6).map { listOf(WEEKDAYS[it], "${Math.round(wd[it])}") })
        }
        if (StatsPart.LISTS in job.parts) {
            val authors = listed.filter { it.author.isNotBlank() }.groupBy { it.author }
                .map { (a, l) -> Triple(a, l.size, l.sumOf { perBook[it.id] ?: 0.0 }) }.sortedByDescending { it.third }
            tables += Table("авторы", listOf("автор", "книг", "за период"), authors.map { listOf(it.first, "${it.second}", fmt(it.third)) })
            val genres = LinkedHashMap<String, Pair<Int, Double>>()
            for (b in listed) for (g in Book.splitGenres(b.genres)) {
                val (n, m) = genres[g] ?: (0 to 0.0); genres[g] = (n + 1) to (m + (perBook[b.id] ?: 0.0))
            }
            tables += Table("жанры", listOf("жанр", "книг", "за период"),
                genres.entries.sortedByDescending { it.value.first }.map { listOf(it.key, "${it.value.first}", fmt(it.value.second)) })
            val apps = listed.groupBy { it.appName.ifBlank { "—" } }
                .map { (a, l) -> Triple(a, l.size, l.sumOf { perBook[it.id] ?: 0.0 }) }.sortedByDescending { it.third }
            tables += Table("читалки", listOf("где читаю", "книг", "за период"), apps.map { listOf(it.first, "${it.second}", fmt(it.third)) })
            val rated = listed.filter { it.rating != null }.sortedByDescending { it.rating }
            tables += Table("оценки", listOf("книга", "автор", "оценка"),
                rated.map { listOf(it.title.ifBlank { "Без названия" }, it.author, "${stars(it.rating!!)} ${it.rating} из 10") })
        }
        if (StatsPart.SESSIONS in job.parts) {
            val list = sessions.filter { inPeriod(it.startTime, from, to) }.sortedBy { it.startTime }
            tables += Table("сеансы", listOf("дата", "начало", "конец", "минут", "книга"), list.map { s ->
                val st = Instant.ofEpochMilli(s.startTime).atZone(zone)
                val en = Instant.ofEpochMilli(s.endTime ?: now).atZone(zone)
                listOf(st.toLocalDate().format(iso), st.format(timeFmt), en.format(timeFmt),
                    "${((s.endTime ?: now) - s.startTime) / 60_000}",
                    booksById[s.bookId]?.let { Book.displayName(it) } ?: "")
            })
        }

        for (t in tables) {
            out.append("\n## ").append(t.title).append("\n\n")
            if (t.rows.isEmpty()) out.append("нет данных\n") else out.append(md(t))
        }
        val base = "Статистика чтения — ${job.periodFile}"
        val files = mutableListOf(OutFile("$base.md", withTags(out.toString(), job)))
        if (job.csv) for (t in tables) if (t.rows.isNotEmpty()) files += OutFile("$base — ${t.title}.csv", csv(t))
        return files
    }

    private fun periodText(from: LocalDate, to: LocalDate): String {
        fun full(d: LocalDate) = "${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]} ${d.year}"
        return when {
            from == to -> full(from)
            from.year != to.year -> "с ${full(from)} по ${full(to)}"
            from.monthValue != to.monthValue -> "с ${from.dayOfMonth} ${MONTHS_GEN[from.monthValue - 1]} по ${full(to)}"
            else -> "с ${from.dayOfMonth} по ${full(to)}"
        }
    }

    private val WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
}
