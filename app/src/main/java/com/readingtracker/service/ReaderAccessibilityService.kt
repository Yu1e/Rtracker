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
        if (tracked[pkg] == "ЛитРес") try { litres(pkg, event) } catch (_: Exception) {}
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
        fun ids(node: AccessibilityNodeInfo, name: String): List<AccessibilityNodeInfo> =
            try { node.findAccessibilityNodeInfosByViewId("$pkg:id/$name") ?: emptyList() } catch (_: Exception) { emptyList() }
        fun AccessibilityNodeInfo.str() = text?.toString()?.replace('\n', ' ')?.trim()?.takeIf { it.isNotEmpty() }

        // Экран чтения и меню читалки
        (ids(root, "book_name") + ids(root, "book_name_title")).firstNotNullOfOrNull { it.str() }
            ?.let { ReaderScreen.seeTitle(pkg, it) }
        ids(root, "progress_text_progress").firstNotNullOfOrNull { it.str() }
            ?.let { ReaderScreen.seePages(pkg, it) }

        // Библиотека и магазин: название и автор в одной карточке
        for (n in ids(root, "textViewBookName").take(40)) {
            val t = n.str() ?: continue
            val parent = n.parent ?: continue
            var author = ids(parent, "textViewAuthor").firstNotNullOfOrNull { it.str() }
            if (author == null) {
                // Карточка может быть на уровень выше; но не весь список, где книг много
                val grand = parent.parent
                if (grand != null && ids(grand, "textViewBookName").size == 1)
                    author = ids(grand, "textViewAuthor").firstNotNullOfOrNull { it.str() }
            }
            if (author != null) ReaderScreen.rememberAuthor(this, t, author)
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
                val now = System.currentTimeMillis()
                if (now - (lastDumpAt[pkg] ?: 0) < 4000) return
                val root = rootInActiveWindow ?: return
                val parts = mutableListOf<String>()
                collect(root, parts, 0, 80)
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
