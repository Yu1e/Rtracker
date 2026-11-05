package com.readingtracker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.readingtracker.Constants
import com.readingtracker.diag.ScoutLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Служба специальных возможностей.
 * 1) Отмечает активность в читалках (перелистывания, касания) — для паузы через 3 минуты.
 * 2) Разведка: записывает в журнал текст экранов и нажатий в читалках,
 *    чтобы потом научиться брать автора и название автоматически.
 */
class ReaderAccessibilityService : AccessibilityService() {

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(context, ReaderAccessibilityService::class.java)
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == me
            }
        }

        @Volatile private var instance: ReaderAccessibilityService? = null

        /** Ручной таймер: слушать все приложения (true) или только читалки (false). */
        fun widen(all: Boolean) {
            val s = instance ?: return
            try {
                s.serviceInfo = s.serviceInfo.apply {
                    packageNames = if (all || s.tracked.isEmpty()) null else s.tracked.keys.toTypedArray()
                }
            } catch (_: Exception) {}
        }

        /** Счётчики событий по типам — для экрана проверки. */
        val eventTypeCounts = ConcurrentHashMap<String, Int>()
    }

    private var tracked: Map<String, String> = emptyMap()
    private val ACTIVE_TYPES = setOf(
        AccessibilityEvent.TYPE_VIEW_SCROLLED, AccessibilityEvent.TYPE_VIEW_CLICKED,
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_VIEW_SELECTED
    )
    private val lastDumpAt = HashMap<String, Long>()
    private val lastDumpHash = HashMap<String, Int>()

    override fun onServiceConnected() {
        instance = this
        tracked = Constants.detectTrackedApps(this)
        serviceInfo = serviceInfo.apply {
            if (tracked.isNotEmpty() && !ActivitySignal.manual) packageNames = tracked.keys.toTypedArray()
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 150
        }
        ActivitySignal.accessibilityConnected = true
        ScoutLog.append(this, "=== служба подключена, читалки: ${tracked.values.joinToString()}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in tracked) {
            // Ручной таймер: перелистывания в любой другой читалке, кроме самого трекера и системной шторки
            if (ActivitySignal.manual && pkg != packageName && pkg != "com.android.systemui" &&
                event.eventType in ACTIVE_TYPES) ActivitySignal.touchAny()
            return
        }
        val type = AccessibilityEvent.eventTypeToString(event.eventType)
        val key = "${tracked[pkg]}: $type"
        eventTypeCounts[key] = (eventTypeCounts[key] ?: 0) + 1

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> ActivitySignal.touch(pkg)
        }

        try {
            scout(pkg, event)
        } catch (_: Exception) {}
        when (tracked[pkg]) {
            "ЛитРес" -> try { litres(pkg, event) } catch (_: Exception) {}
            "AlReader" -> try { alreader(pkg, event) } catch (_: Exception) {}
            "Свет" -> try { svet(pkg, event) } catch (_: Exception) {}
            "Яндекс Книги" -> try { yandex(pkg, event) } catch (_: Exception) {}
        }
    }

    private fun AccessibilityNodeInfo.str() = text?.toString()?.replace('\n', ' ')?.trim()?.takeIf { it.isNotEmpty() }
    private fun AccessibilityNodeInfo.desc() = contentDescription?.toString()?.replace('\n', ' ')?.trim()?.takeIf { it.isNotEmpty() }

    private fun ids(pkg: String, node: AccessibilityNodeInfo, name: String): List<AccessibilityNodeInfo> =
        try { node.findAccessibilityNodeInfosByViewId("$pkg:id/$name") ?: emptyList() } catch (_: Exception) { emptyList() }

    /** Текст события нажатия: подписи и описание. */
    private fun clickText(event: AccessibilityEvent): String {
        val src = try { event.source } catch (_: Exception) { null }
        return listOfNotNull(
            event.text?.joinToString(" ") { it?.toString() ?: "" },
            event.contentDescription?.toString(),
            src?.str(), src?.desc()
        ).joinToString(" ").trim()
    }

    /** Книга, выбранная в библиотеке или карточке, — пока не нажали «Читать». */
    private var chosenTitle: String? = null
    private var chosenAuthor: String? = null
    private var chosenPkg: String? = null
    private var chosenAt = 0L

    private fun choose(pkg: String, title: String, author: String?) {
        chosenTitle = title; chosenAuthor = author; chosenPkg = pkg; chosenAt = System.currentTimeMillis()
        if (author != null) ReaderScreen.rememberAuthor(this, title, author)
    }

    /** Нажали «Читать» — выбранная книга считается открытой на экране. */
    private fun readPressed(pkg: String) {
        val t = chosenTitle ?: return
        if (chosenPkg != pkg || System.currentTimeMillis() - chosenAt > 10 * 60_000) return
        ReaderScreen.seeTitle(pkg, t)
        chosenTitle = null
    }

    private var screenAt = HashMap<String, Long>()

    /** Содержимое экрана читаем не чаще раза в 1,2 с на читалку. */
    private fun throttled(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - (screenAt[pkg] ?: 0L) < 1200) return true
        screenAt[pkg] = now
        return false
    }

    // ── AlReader (журнал разведки 1–4 ноября 2026) ───────────────────────────
    // Экран чтения рисуется сам, текста на нём нет. Книга видна в библиотеке
    // (textFileList0 desc — название прописными, textFileList3 — автор) и в «Свойствах»
    // (textView1 desc — название, textView2 desc — автор), оттуда «Читать» (propertymenu_read).
    // Страницы — в окне «Проценты/Страницы»: edittext2 — страница, text2 — «страница из 680».

    private fun alreader(pkg: String, event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val src = event.source
            val id = src?.viewIdResourceName?.substringAfter(":id/")
            if (id == "propertymenu_read" || clickText(event).split(' ').any { it == "Читать" }) readPressed(pkg)
            // Книга открыта прямо из списка файлов: «Автор • Название | zip …» — сразу открыта
            event.text?.map { it?.toString() ?: "" }?.firstOrNull { it.contains(" • ") }?.let { line ->
                val author = line.substringBefore(" • ").trim()
                val title = line.substringAfter(" • ").trim()
                if (title.isNotEmpty()) { choose(pkg, title, author.ifEmpty { null }); readPressed(pkg) }
            }
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (throttled(pkg)) return
        val root = rootInActiveWindow ?: return

        // «Свойства» книги: название и автор в обычном написании
        val title = ids(pkg, root, "textView1").firstNotNullOfOrNull { it.desc() ?: it.str() }
        if (title != null && ids(pkg, root, "propertymenu_read").isNotEmpty()) {
            val author = ids(pkg, root, "textView2").firstNotNullOfOrNull { it.desc() ?: it.str() }
            choose(pkg, title, author)
            ids(pkg, root, "textView3").firstNotNullOfOrNull { it.desc() ?: it.str() }
                ?.let { ReaderScreen.rememberGenres(this, title, it) }
        }

        // Оглавление (AlReader3TreeContent): первая строка — название открытой книги
        if (ids(pkg, root, "contentmenu_expand").isNotEmpty()) {
            ids(pkg, root, "textFileList0").firstNotNullOfOrNull { it.desc() ?: it.str() }
                ?.let { ReaderScreen.seeTitle(pkg, it) }
        }

        // Список файлов: «Автор • Название» в textFileList3
        for (n in ids(pkg, root, "textFileList3").take(40)) {
            val line = n.str() ?: continue
            if (!line.contains(" • ")) continue
            ReaderScreen.rememberAuthor(this, line.substringAfter(" • ").trim(), line.substringBefore(" • ").trim())
        }

        // Библиотека: пары «название — автор» в одной строке списка
        for (n in ids(pkg, root, "textFileList0").take(40)) {
            val t = n.desc() ?: n.str() ?: continue
            if (t == "..") continue
            val parent = n.parent ?: continue
            val author = ids(pkg, parent, "textFileList3").firstNotNullOfOrNull { it.str() } ?: continue
            // В списке файлов здесь «Автор • Название», а в textFileList0 — имя файла: эта пара не годится
            if (author.contains(" • ")) continue
            ReaderScreen.rememberAuthor(this, t, author)
            ids(pkg, parent, "textFileList4").firstNotNullOfOrNull { it.str() }
                ?.let { ReaderScreen.rememberGenres(this, t, it) }
        }

        // Окно «Проценты/Страницы»
        val page = ids(pkg, root, "edittext2").firstNotNullOfOrNull { it.str() }
        val of = ids(pkg, root, "text2").firstNotNullOfOrNull { it.str() }
        if (page != null && of != null && of.contains("из")) ReaderScreen.seePages(pkg, "$page ${of.substringAfter("страница").trim()}")
    }

    // ── «Свет» (журнал разведки 3–4 ноября 2026) ─────────────────────────────
    // Почти без id, всё в описаниях. Карточка книги: «16+», автор, название,
    // «Примерная длительность чтения: …», «Читать». В читалке: navigation_textView — название
    // (в оглавлении), current_page_text и total_pages_text — страницы.

    private fun svet(pkg: String, event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (clickText(event).split(' ').any { it == "Читать" }) readPressed(pkg)
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (throttled(pkg)) return
        val root = rootInActiveWindow ?: return

        // Читалка: название из оглавления, страницы внизу
        ids(pkg, root, "navigation_textView").firstNotNullOfOrNull { it.str() }?.let { ReaderScreen.seeTitle(pkg, it) }
        val cur = ids(pkg, root, "current_page_text").firstNotNullOfOrNull { it.str() }
        val total = ids(pkg, root, "total_pages_text").firstNotNullOfOrNull { it.str() }
        if (cur != null && total != null) ReaderScreen.seePages(pkg, "$cur из $total")

        // Карточка книги: два описания перед «Примерная длительность чтения»
        val descs = mutableListOf<String>()
        collectDescs(root, descs, 0)
        val i = descs.indexOfFirst { it.startsWith("Примерная длительность") }
        if (i >= 2) {
            val t = descs[i - 1]; val a = descs[i - 2]
            if (t.length in 1..200 && a.length in 2..80) choose(pkg, t, a)
        }
    }

    private fun collectDescs(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (out.size >= 120 || depth > 25) return
        (node.desc() ?: node.str())?.let { out += it }
        for (k in 0 until node.childCount) collectDescs(node.getChild(k) ?: continue, out, depth + 1)
    }

    // ── Яндекс Книги (журнал разведки 6 ноября 2026) ─────────────────────────
    // Читалка (Reader2Activity): вверху «Закрыть читалку» (описание), за ним два текста без id —
    // название и автор; видны при открытии книги и в меню. Страниц нет, только «12% от всей книги».
    // Библиотека и «Мои книги»: описание обложки «Имя розы. Автор Умберто Эко. Обложка[. Прогресс: …]».

    private val YANDEX_COVER = Regex("^(.+?)\\. Автор (.+?)\\. Обложка")

    private fun yandex(pkg: String, event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (throttled(pkg)) return
        val root = rootInActiveWindow ?: return
        val nodes = mutableListOf<Pair<Boolean, String>>()   // (описание?, значение)
        collectYandex(root, nodes, 0)

        // Читалка: два текста сразу за «Закрыть читалку»
        val close = nodes.indexOfFirst { it.first && it.second == "Закрыть читалку" }
        if (close >= 0) {
            val texts = nodes.drop(close + 1).takeWhile { !(it.first && it.second == "Ещё") }.filter { !it.first }.map { it.second }
            val title = texts.getOrNull(0)?.takeIf { it.length in 1..200 }
            val author = texts.getOrNull(1)?.takeIf { it.length in 2..80 && !it.first().isDigit() }
            if (title != null) {
                if (author != null) ReaderScreen.rememberAuthor(this, title, author)
                ReaderScreen.seeTitle(pkg, title)
            }
        }

        // Обложки в библиотеке: название и автор
        for ((isDesc, v) in nodes) {
            if (!isDesc) continue
            val m = YANDEX_COVER.find(v) ?: continue
            ReaderScreen.rememberAuthor(this, m.groupValues[1].trim(), m.groupValues[2].trim())
        }
    }

    private fun collectYandex(node: AccessibilityNodeInfo, out: MutableList<Pair<Boolean, String>>, depth: Int) {
        if (out.size >= 200 || depth > 25) return
        node.str()?.let { out += false to it }
        node.desc()?.let { if (it != node.str()) out += true to it }
        for (k in 0 until node.childCount) collectYandex(node.getChild(k) ?: continue, out, depth + 1)
    }

    // ── ЛитРес: название, страницы, автор ─────────────────────────────────────

    private var litresAt = 0L

    private fun litres(pkg: String, event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val now = System.currentTimeMillis()
        if (now - litresAt < 1200) return
        litresAt = now
        val root = rootInActiveWindow ?: return

        // Экран чтения и меню читалки
        (ids(pkg, root, "book_name") + ids(pkg, root, "book_name_title")).firstNotNullOfOrNull { it.str() }
            ?.let { ReaderScreen.seeTitle(pkg, it) }
        ids(pkg, root, "progress_text_progress").firstNotNullOfOrNull { it.str() }
            ?.let { ReaderScreen.seePages(pkg, it) }

        // Библиотека и магазин: название и автор в одной карточке
        for (n in ids(pkg, root, "textViewBookName").take(40)) {
            val t = n.str() ?: continue
            val parent = n.parent ?: continue
            var author = ids(pkg, parent, "textViewAuthor").firstNotNullOfOrNull { it.str() }
            if (author == null) {
                // Карточка может быть на уровень выше; но не весь список, где книг много
                val grand = parent.parent
                if (grand != null && ids(pkg, grand, "textViewBookName").size == 1)
                    author = ids(pkg, grand, "textViewAuthor").firstNotNullOfOrNull { it.str() }
            }
            if (author != null) ReaderScreen.rememberAuthor(this, t, author)
        }
        // Магазин: название и автор в карточке на главной вкладке
        for (n in ids(pkg, root, "tv_book_title_resizable_view_main_tab").take(40)) {
            val t = n.str() ?: continue
            val parent = n.parent ?: continue
            val author = ids(pkg, parent, "tv_author_name_resizable_book_main_tab").firstNotNullOfOrNull { it.str() } ?: continue
            ReaderScreen.rememberAuthor(this, t, author)
        }
    }

    // ── Разведка ──────────────────────────────────────────────────────────────

    private fun scout(pkg: String, event: AccessibilityEvent) {
        val app = tracked[pkg] ?: pkg
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val parts = mutableListOf<String>()
                event.text?.forEach { t -> t?.toString()?.takeIf { it.isNotBlank() }?.let { parts += it } }
                event.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { parts += "desc=$it" }
                event.source?.let { collect(it, parts, 0, 25) }
                ScoutLog.append(this, "[$app] КЛИК ${event.className}: ${parts.distinct().joinToString(" | ").take(800)}")
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // ЛитРес уже разобран и пишет на каждой странице — его изменения не забивают журнал,
                // иначе записи других читалок вытесняются (журнал ограничен по размеру)
                if (app == "ЛитРес" && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
                val now = System.currentTimeMillis()
                if (now - (lastDumpAt[pkg] ?: 0) < 4000) return
                val root = rootInActiveWindow ?: return
                val parts = mutableListOf<String>()
                collect(root, parts, 0, 80)
                // Яндекс Книги отдают службе текст книги абзацами — страницы книги в журнал не пишем
                if (app == "Яндекс Книги" && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    parts.count { it.length > 100 } >= 4) return
                val hash = parts.hashCode()
                if (hash == lastDumpHash[pkg]) return
                lastDumpAt[pkg] = now
                lastDumpHash[pkg] = hash
                val kind = if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) "ЭКРАН" else "изменение"
                ScoutLog.append(this, "[$app] $kind ${event.className}:\n    " +
                        parts.joinToString("\n    ").take(4000))
            }
        }
    }

    /** Собирает тексты и подписи узлов: «id: текст». */
    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int, limit: Int) {
        if (out.size >= limit || depth > 25) return
        val id = node.viewIdResourceName?.substringAfter(":id/")
        val text = node.text?.toString()?.trim()?.replace('\n', ' ')
        val desc = node.contentDescription?.toString()?.trim()?.replace('\n', ' ')
        if (!text.isNullOrEmpty()) out += (if (id != null) "$id: " else "") + text.take(120)
        if (!desc.isNullOrEmpty() && desc != text) out += (if (id != null) "$id " else "") + "desc: " + desc.take(120)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1, limit)
            if (out.size >= limit) return
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        ActivitySignal.accessibilityConnected = false
        super.onDestroy()
    }
}
