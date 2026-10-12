package com.readingtracker.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.readingtracker.Prefs
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.data.ReadingSession
import com.readingtracker.widget.WidgetUpdater
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/** Резервная копия всей базы в JSON-файл и восстановление из него. */
object BackupManager {

    private const val AUTO_PREFIX = "rtracker-auto-"
    private const val KEEP_AUTO = 7

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ── В JSON и обратно ──────────────────────────────────────────────────────

    private suspend fun toJson(context: Context): String {
        val db = AppDatabase.getInstance(context)
        val books = JSONArray()
        for (b in db.bookDao().getAllBooksSync()) {
            books.put(JSONObject().apply {
                put("id", b.id); put("title", b.title); put("author", b.author)
                put("appPackage", b.appPackage); put("appName", b.appName)
                put("startDate", b.startDate); put("endDate", b.endDate ?: JSONObject.NULL)
                put("status", b.status); put("rating", b.rating ?: JSONObject.NULL)
                put("totalMinutes", b.totalMinutes); put("notes", b.notes)
                put("statusAskAt", b.statusAskAt ?: JSONObject.NULL)
                put("needsStatusCheck", b.needsStatusCheck)
                put("lastNotifiedAt", b.lastNotifiedAt ?: JSONObject.NULL)
                put("manualMinutes", b.manualMinutes)
            })
        }
        val sessions = JSONArray()
        for (s in db.sessionDao().getAllSessions()) {
            sessions.put(JSONObject().apply {
                put("id", s.id); put("bookId", s.bookId); put("startTime", s.startTime)
                put("endTime", s.endTime ?: JSONObject.NULL); put("appPackage", s.appPackage)
            })
        }
        return JSONObject().apply {
            put("format", "rtracker")
            put("version", 2)
            put("created", System.currentTimeMillis())
            put("books", books)
            put("sessions", sessions)
        }.toString(1)
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

    data class Parsed(val books: List<Book>, val sessions: List<ReadingSession>, val created: Long)

    fun parse(text: String): Parsed {
        val root = JSONObject(text)
        require(root.optString("format") == "rtracker") { "Это не файл копии трекера" }
        val books = mutableListOf<Book>()
        val ba = root.getJSONArray("books")
        for (i in 0 until ba.length()) {
            val o = ba.getJSONObject(i)
            books += Book(
                id = o.getLong("id"),
                title = o.optString("title"),
                author = o.optString("author"),
                appPackage = o.optString("appPackage"),
                appName = o.optString("appName"),
                startDate = o.getLong("startDate"),
                endDate = o.optLongOrNull("endDate"),
                status = o.optString("status", Book.STATUS_READING),
                rating = o.optIntOrNull("rating"),
                totalMinutes = o.optLong("totalMinutes"),
                notes = o.optString("notes"),
                statusAskAt = o.optLongOrNull("statusAskAt"),
                needsStatusCheck = o.optBoolean("needsStatusCheck"),
                lastNotifiedAt = o.optLongOrNull("lastNotifiedAt"),
                manualMinutes = o.optLong("manualMinutes")
            )
        }
        val sessions = mutableListOf<ReadingSession>()
        val sa = root.getJSONArray("sessions")
        for (i in 0 until sa.length()) {
            val o = sa.getJSONObject(i)
            sessions += ReadingSession(
                id = o.getLong("id"),
                bookId = o.getLong("bookId"),
                startTime = o.getLong("startTime"),
                endTime = o.optLongOrNull("endTime"),
                appPackage = o.optString("appPackage")
            )
        }
        return Parsed(books, sessions, root.optLong("created"))
    }

    // ── Запись ────────────────────────────────────────────────────────────────

    /** Копия в выбранную папку. auto = ежедневная, хранится 7 последних. */
    suspend fun saveToFolder(context: Context, auto: Boolean): Result<String> = runCatching {
        val folderUri = Prefs.backupFolder(context) ?: error("Папка для копий не выбрана")
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: error("Нет доступа к папке")
        if (!folder.canWrite()) error("Нет доступа к папке, выберите её заново")

        val name = if (auto) "$AUTO_PREFIX${today()}.json"
        else "rtracker-${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())}.json"

        folder.findFile(name)?.delete()
        val file = folder.createFile("application/json", name) ?: error("Не удалось создать файл")
        val json = toJson(context)
        context.contentResolver.openOutputStream(file.uri, "wt")!!.use { it.write(json.toByteArray()) }

        if (auto) {
            folder.listFiles()
                .filter { it.name?.startsWith(AUTO_PREFIX) == true }
                .sortedByDescending { it.name }
                .drop(KEEP_AUTO)
                .forEach { it.delete() }
        }
        val info = "${SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date())} — $name"
        Prefs.setLastBackup(context, today(), info)
        name
    }

    /** Копия в файл, выбранный вручную (если папка не задана). */
    suspend fun saveToUri(context: Context, uri: Uri): Result<Unit> = runCatching {
        val json = toJson(context)
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
    }

    /** Ежедневная автокопия: раз в день, если папка выбрана. */
    suspend fun autoBackupIfNeeded(context: Context) {
        if (Prefs.backupFolder(context) == null) return
        if (Prefs.lastBackupDay(context) == today()) return
        saveToFolder(context, auto = true)
    }

    // ── Восстановление ────────────────────────────────────────────────────────

    fun readFile(context: Context, uri: Uri): Result<Parsed> = runCatching {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        parse(text)
    }

    /** Полностью заменяет текущие данные данными из копии. */
    suspend fun restore(context: Context, parsed: Parsed) {
        val db = AppDatabase.getInstance(context)
        db.withTransaction {
            db.sessionDao().deleteAll()
            db.bookDao().deleteAll()
            db.bookDao().insertAll(parsed.books)
            db.sessionDao().insertAll(parsed.sessions)
        }
        WidgetUpdater.update(context)
    }
}
