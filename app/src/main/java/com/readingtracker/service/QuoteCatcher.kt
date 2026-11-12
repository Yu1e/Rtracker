package com.readingtracker.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.readingtracker.Prefs
import com.readingtracker.diag.Diag
import com.readingtracker.diag.ScoutLog
import com.readingtracker.overlay.QuoteOverlay

/**
 * Ловит цитаты: в читалке открыто меню с пунктом «Копировать» (меню выделения ЛитРес,
 * «Действия» AlReader, системная панель выделения в «Свете», Яндекс Книгах и Хроме),
 * затем нажатие — трекер на миг открывает невидимое окошко и читает буфер обмена
 * (на Android 10+ буфер читает только приложение в фокусе). Если в буфере свежий текст —
 * пузырёк «цитата» поверх читалки.
 *
 * Два повода прочитать буфер:
 * 1) нажатие в меню («Копировать» или пункт без подписи — у ЛитРес нажатия без текста);
 * 2) меню закрылось, а нажатия не было (журнал 8 ноября: в ЛитРес нажатие «Копировать» до службы
 *    не доходит вовсе). Ждём 2 с и смотрим ещё раз: пока двигают границы выделения, меню
 *    прячется и возвращается — тогда буфер не трогаем.
 * Если нажали не «Копировать», буфер не новый и пузырька нет.
 *
 * Всё, что происходит, пишется в журнал разведки строками «[цитата] …».
 */
object QuoteCatcher {

    private val handler = Handler(Looper.getMainLooper())

    /** Названия читалок для журнала (пакет → «ЛитРес»). */
    @Volatile var names: Map<String, String> = emptyMap()

    /** Служба — чтобы окно цитаты могло взять текст страницы (длинная цитата). */
    private var serviceRef: java.lang.ref.WeakReference<AccessibilityService>? = null

    /** Меню с «Копировать» сейчас на экране (в этой читалке). */
    private var menuPkg: String? = null
    private var menuOpenedAt = 0L
    private var menuSeenAt = 0L
    private var scanAt = 0L

    /** Форсированный режим: в этом меню «Копировать» уже нажато — до его закрытия больше не жмём. */
    private var forcedDone = false
    private var overflowAt = 0L

    /** Последний текст, о котором уже спрашивали, — второй раз не спрашиваем. */
    private var lastText: String? = null
    private var probing = false
    private var probeAt = 0L

    /** Абзацы страницы на экране в минуту, когда открылось меню, — для границ цитаты и «вся страница». */
    private var pageParas: List<String> = emptyList()

    private fun log(ctx: Context, pkg: String, msg: String) =
        try { ScoutLog.append(ctx, "[цитата] ${names[pkg] ?: if (pkg == ReaderAccessibilityService.webPkg) "браузер" else pkg}: $msg") } catch (_: Exception) {}

    /** Абзацы, которые сейчас видны в читалке (для окна цитаты: «↓ в конец», «↑ в начало»). */
    fun pageParagraphs(pkg: String): List<String> {
        val svc = serviceRef?.get() ?: return emptyList()
        return try { visibleText(svc, pkg) } catch (_: Exception) { emptyList() }
    }

    fun onEvent(service: AccessibilityService, pkg: String, event: AccessibilityEvent) {
        // Цитаты выключены в настройках
        if (!Prefs.quotes(service)) return
        serviceRef = java.lang.ref.WeakReference(service)
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = System.currentTimeMillis()
                // Изменения содержимого идут потоком: смотрим не чаще раза в 0,4 с
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - scanAt < 400) return
                scanAt = now
                val menu = scanMenu(service, pkg)
                val open = menu.copy != null

                // Форсированный режим: меню выделения появилось — «Копировать» жмёт трекер.
                // Пока открыто окно цитаты (не свёрнутое), не трогаем ничего.
                val forced = Prefs.forcedQuotes(service) && !(QuoteOverlay.isOpen && !QuoteOverlay.isCollecting)
                if (forced) {
                    if (menu.copy == null && menu.overflow == null) forcedDone = false
                    else if (!forcedDone) {
                        if (pageParas.isEmpty() || menuPkg != pkg) pageParas = try { visibleText(service, pkg) } catch (_: Exception) { emptyList() }
                        if (menu.copy != null) {
                            forcedDone = true
                            menuPkg = null
                            log(service, pkg, "форсированно: нажимаю «Копировать»")
                            if (click(menu.copy)) {
                                probeAt = System.currentTimeMillis()
                                handler.postDelayed({ probe(service.applicationContext, pkg) }, 450)
                            } else log(service, pkg, "«Копировать» не нажимается")
                            return
                        }
                        val ov = menu.overflow
                        if (ov != null && now - overflowAt > 1500) {
                            overflowAt = now
                            log(service, pkg, "форсированно: открываю «Ещё»")
                            click(ov)
                            return
                        }
                    }
                }

                if (open) {
                    if (menuPkg != pkg) {
                        menuOpenedAt = now
                        // Страница под меню — пока меню открыто, она ещё на экране
                        pageParas = try { visibleText(service, pkg) } catch (_: Exception) { emptyList() }
                        log(service, pkg, "меню с «Копировать» открыто")
                    }
                    menuPkg = pkg; menuSeenAt = now
                } else if (menuPkg == pkg) {
                    // Меню закрылось. Нажатие, если было, уже прочитало буфер.
                    menuPkg = null
                    if (now - probeAt > 3000 && now - menuOpenedAt < 120_000) {
                        val closedAt = now
                        handler.postDelayed({
                            if (menuPkg == pkg) return@postDelayed            // меню вернулось — двигали выделение
                            if (probeAt > closedAt) return@postDelayed       // за это время уже прочитали
                            log(service, pkg, "меню закрылось без нажатия, читаю буфер")
                            probeAt = System.currentTimeMillis()
                            probe(service.applicationContext, pkg)
                        }, 2000)
                    }
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (System.currentTimeMillis() - probeAt < 1500) return      // это нажал сам трекер
                val text = clickText(event)
                val copyWord = text.split(' ', '\n').any { it.equals("Копировать", true) || it.equals("Copy", true) }
                val menuOpen = menuPkg == pkg && System.currentTimeMillis() - menuSeenAt < 60_000
                if (!copyWord && !menuOpen) return
                // Нажали в меню что-то другое с подписью («Поделиться», «Заметка») — не копирование
                if (!copyWord && text.isNotBlank() && text.length < 40) return
                menuPkg = null
                if (pageParas.isEmpty()) pageParas = try { visibleText(service, pkg) } catch (_: Exception) { emptyList() }
                log(service, pkg, if (copyWord) "нажали «Копировать», читаю буфер" else "нажатие в меню, читаю буфер")
                probeAt = System.currentTimeMillis()
                // Читалке нужно мгновение, чтобы положить текст в буфер
                handler.postDelayed({ probe(service.applicationContext, pkg) }, 350)
            }
        }
    }

    private fun clickText(event: AccessibilityEvent): String {
        val src = try { event.source } catch (_: Exception) { null }
        return listOfNotNull(
            event.text?.joinToString(" ") { it?.toString() ?: "" },
            event.contentDescription?.toString(),
            src?.text?.toString(), src?.contentDescription?.toString()
        ).joinToString(" ").trim()
    }

    /** Нажать узел или ближайшего нажимаемого родителя. */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 5) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent; depth++
        }
        return try { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
    }

    /** Окна этой читалки (панель выделения и всплывающие меню — отдельные окна). */
    private fun roots(service: AccessibilityService, pkg: String): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            for (w in service.windows) {
                if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD || w.type == AccessibilityWindowInfo.TYPE_SYSTEM) continue
                val r = w.root ?: continue
                if (r.packageName?.toString() == pkg) roots += r
            }
        } catch (_: Exception) {}
        if (roots.isEmpty()) service.rootInActiveWindow?.let { if (it.packageName?.toString() == pkg) roots += it }
        return roots
    }

    private class Menu(val copy: AccessibilityNodeInfo?, val overflow: AccessibilityNodeInfo?)

    /**
     * Меню выделения: пункт «Копировать»; если его не видно — кнопка «Ещё» системной панели
     * выделения (id …:id/overflow, в «Свете» «Копировать» спрятан за ней).
     */
    private fun scanMenu(service: AccessibilityService, pkg: String): Menu {
        var overflow: AccessibilityNodeInfo? = null
        for (r in roots(service, pkg)) {
            val found = try { r.findAccessibilityNodeInfosByText("Копировать") } catch (_: Exception) { null }
            found?.firstOrNull { n ->
                n.text?.toString()?.trim().equals("Копировать", true) ||
                    n.contentDescription?.toString()?.trim().equals("Копировать", true)
            }?.let { return Menu(it, null) }
            if (overflow == null) {
                val ov = try { r.findAccessibilityNodeInfosByViewId("android:id/overflow") } catch (_: Exception) { null }
                overflow = ov?.firstOrNull { it.isVisibleToUser }
                if (overflow == null) overflow = findOverflow(r, 0)
            }
        }
        return Menu(null, overflow)
    }

    private fun findOverflow(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 12) return null
        val id = n.viewIdResourceName
        if (id != null && id.endsWith(":id/overflow") && n.isVisibleToUser) {
            val d = n.contentDescription?.toString()?.trim() ?: ""
            if (d.equals("Ещё", true) || d.equals("Еще", true) || d.equals("Больше", true) || d.contains("More", true) ||
                d.contains("дополнительное меню", true)) return n
        }
        for (k in 0 until n.childCount) findOverflow(n.getChild(k) ?: continue, depth + 1)?.let { return it }
        return null
    }

    private val FOOTNOTE = Regex("^\\[?\\d{1,3}\\]?$")

    /**
     * Текст, который сейчас виден на странице, абзацами. Куски одного абзаца («Свет» режет абзац
     * на сносках и курсиве) склеиваются: следующий кусок со знака препинания или строчной буквы,
     * или предыдущий не кончается точкой — тот же абзац. Номера сносок выбрасываются.
     * Подписи кнопок читалки (узлы с id приложения) не берутся, если это не длинный текст (у ЛитРес
     * страница целиком лежит в описании одного узла).
     */
    private fun visibleText(service: AccessibilityService, pkg: String): List<String> {
        val dm = service.resources.displayMetrics
        val screen = android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
        val frags = mutableListOf<String>()
        val box = android.graphics.Rect()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (frags.size >= 500 || depth > 40) return
            if (n.isVisibleToUser) {
                n.getBoundsInScreen(box)
                if (box.height() > 0 && android.graphics.Rect.intersects(box, screen)) {
                    val native = n.viewIdResourceName?.contains(":id/") == true
                    val t = n.text?.toString()
                    val d = n.contentDescription?.toString()
                    when {
                        t != null && t.isNotBlank() && (!native || t.length >= 40) -> frags += t
                        t.isNullOrBlank() && d != null && d.length >= 40 -> frags += d
                    }
                }
            }
            for (k in 0 until n.childCount) walk(n.getChild(k) ?: continue, depth + 1)
        }
        for (r in roots(service, pkg)) walk(r, 0)

        val paras = mutableListOf<StringBuilder>()
        for (raw in frags) {
            val f = raw.replace('\n', ' ').trim()
            if (f.isEmpty() || FOOTNOTE.matches(f)) continue
            val prev = paras.lastOrNull()
            val joinTight = f.first() in ".,;:!?)»…"
            val joinSpace = f.first().isLowerCase() || f.first() == '—' || f.first() == '–' ||
                (prev != null && prev.isNotEmpty() && prev.last() !in ".!?…»\"”" )
            when {
                prev == null -> paras += StringBuilder(f)
                joinTight || (prev.isNotEmpty() && prev.last() in "(«„") -> prev.append(f)
                joinSpace -> prev.append(' ').append(f)
                else -> paras += StringBuilder(f)
            }
        }
        return paras.map { it.toString() }.distinct()
    }

    /** Невидимое окошко на миг забирает фокус: только так можно прочитать буфер обмена. */
    private fun probe(ctx: Context, pkg: String) {
        if (probing) return
        if (!Settings.canDrawOverlays(ctx)) {
            Diag.set(ctx, Diag.ERROR, "${Diag.time()} цитата: нет разрешения на окна поверх")
            log(ctx, pkg, "нет разрешения на окна поверх других приложений")
            return
        }
        probing = true
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = View(ctx)
        val params = WindowManager.LayoutParams(1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT)
        var done = false
        fun finish(focused: Boolean) {
            if (done) return
            done = true
            val canRead = focused || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            val (text, why) = if (canRead) readClip(ctx) else null to "фокус не пришёл, буфер не прочитан"
            try { wm.removeView(view) } catch (_: Exception) {}
            probing = false
            val t = text?.trim()
            when {
                t == null -> log(ctx, pkg, why)
                t.length < 2 -> log(ctx, pkg, "в буфере меньше двух знаков")
                text == lastText -> log(ctx, pkg, "тот же текст, что в прошлый раз, — пузырька нет")
                QuoteOverlay.isCollecting -> {
                    lastText = text
                    log(ctx, pkg, "длинная цитата: дописываю ${t.length} знаков")
                    QuoteOverlay.onCopied(t)
                }
                else -> {
                    lastText = text
                    log(ctx, pkg, "пузырёк, ${t.length} знаков, страница ${pageParas.sumOf { it.length }} знаков")
                    QuoteOverlay.show(ctx, pkg, t, pageParas)
                }
            }
            pageParas = emptyList()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener { has -> if (has) handler.post { finish(true) } }
        try { wm.addView(view, params) } catch (e: Exception) {
            probing = false
            Diag.set(ctx, Diag.ERROR, "${Diag.time()} цитата: $e")
            log(ctx, pkg, "окошко не открылось: $e")
            return
        }
        // Фокус не пришёл за 1,2 с — пробуем как есть (на старых Android можно и без фокуса)
        handler.postDelayed({ finish(view.hasWindowFocus()) }, 1200)
    }

    /** Текст из буфера, если он положен только что (не старше 20 с); иначе null и причина. */
    private fun readClip(ctx: Context): Pair<String?, String> = try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val desc = cm.primaryClipDescription
        if (desc == null) null to "буфер пуст или недоступен"
        else {
            // Отметка времени в разных версиях Android — то от включения телефона, то обычное время: годится любое
            val ts = desc.timestamp
            val fresh = ts == 0L || kotlin.math.abs(System.currentTimeMillis() - ts) < 20_000 ||
                kotlin.math.abs(android.os.SystemClock.elapsedRealtime() - ts) < 20_000
            if (!fresh) null to "в буфере старый текст (старше 20 с)"
            else {
                val s = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                s to "в буфере не текст"
            }
        }
    } catch (e: Exception) {
        Diag.set(ctx, Diag.ERROR, "${Diag.time()} буфер: $e")
        null to "буфер: $e"
    }
}
