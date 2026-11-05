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
import com.readingtracker.diag.Diag
import com.readingtracker.overlay.QuoteOverlay

/**
 * Ловит цитаты: в читалке открыто меню с пунктом «Копировать» (меню выделения ЛитРес,
 * «Действия» AlReader, системная панель выделения в «Свете», Яндекс Книгах и Хроме),
 * затем нажатие — трекер на миг открывает невидимое окошко и читает буфер обмена
 * (на Android 10+ буфер читает только приложение в фокусе). Если в буфере свежий текст —
 * пузырёк «цитата» поверх читалки.
 *
 * Нажатия в ЛитРес приходят без текста, поэтому ориентир — само меню: было открыто,
 * в нём нажали что-то. Если нажали не «Копировать», буфер не новый и пузырька нет.
 */
object QuoteCatcher {

    private val handler = Handler(Looper.getMainLooper())

    /** Меню с «Копировать» сейчас на экране (в этой читалке). */
    private var menuPkg: String? = null
    private var menuSeenAt = 0L
    private var scanAt = 0L

    /** Последний текст, о котором уже спрашивали, — второй раз не спрашиваем. */
    private var lastText: String? = null
    private var probing = false

    /** Текст страницы на экране в минуту нажатия — для «вся страница» (Свет, Яндекс, Хром). */
    private var pageText = ""

    fun onEvent(service: AccessibilityService, pkg: String, event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = System.currentTimeMillis()
                // Изменения содержимого идут потоком: смотрим не чаще раза в 0,5 с
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - scanAt < 500) return
                scanAt = now
                val open = hasCopyMenu(service, pkg)
                if (open) { menuPkg = pkg; menuSeenAt = now }
                else if (menuPkg == pkg && now - menuSeenAt > 1500) menuPkg = null
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val text = clickText(event)
                val copyWord = text.split(' ', '\n').any { it.equals("Копировать", true) || it.equals("Copy", true) }
                val menuOpen = menuPkg == pkg && System.currentTimeMillis() - menuSeenAt < 60_000
                if (!copyWord && !menuOpen) return
                // Нажали в меню что-то другое с подписью («Поделиться», «Заметка») — не копирование
                if (!copyWord && text.isNotBlank() && text.length < 40) return
                menuPkg = null
                pageText = try { visibleText(service, pkg) } catch (_: Exception) { "" }
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

    /** Есть ли на экране пункт «Копировать» — в любом окне этой читалки (панель выделения — отдельное окно). */
    private fun hasCopyMenu(service: AccessibilityService, pkg: String): Boolean {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            for (w in service.windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val r = w.root ?: continue
                if (r.packageName?.toString() == pkg) roots += r
            }
        } catch (_: Exception) {}
        if (roots.isEmpty()) service.rootInActiveWindow?.let { roots += it }
        for (r in roots) {
            val found = try { r.findAccessibilityNodeInfosByText("Копировать") } catch (_: Exception) { null } ?: continue
            if (found.any { n ->
                    n.text?.toString()?.trim().equals("Копировать", true) ||
                        n.contentDescription?.toString()?.trim().equals("Копировать", true)
                }) return true
        }
        return false
    }

    /** Текст, который сейчас виден на странице (длинные строки — абзацы книги). */
    private fun visibleText(service: AccessibilityService, pkg: String): String {
        val root = service.rootInActiveWindow ?: return ""
        if (root.packageName?.toString() != pkg) return ""
        val out = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (out.size >= 60 || depth > 30) return
            val t = n.text?.toString()?.trim()
            if (t != null && t.length >= 40 && n.isVisibleToUser) out += t
            for (k in 0 until n.childCount) walk(n.getChild(k) ?: continue, depth + 1)
        }
        walk(root, 0)
        return out.distinct().joinToString("\n\n")
    }

    /** Невидимое окошко на миг забирает фокус: только так можно прочитать буфер обмена. */
    private fun probe(ctx: Context, pkg: String) {
        if (probing) return
        if (!Settings.canDrawOverlays(ctx)) { Diag.set(ctx, Diag.ERROR, "${Diag.time()} цитата: нет разрешения на окна поверх"); return }
        probing = true
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = View(ctx)
        val params = WindowManager.LayoutParams(1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT)
        var done = false
        fun finish(read: Boolean) {
            if (done) return
            done = true
            val text = if (read) readClip(ctx) else null
            try { wm.removeView(view) } catch (_: Exception) {}
            probing = false
            if (text != null && text.trim().length >= 2 && text != lastText) {
                lastText = text
                QuoteOverlay.show(ctx, pkg, text.trim(), pageText)
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener { has -> if (has) handler.post { finish(true) } }
        try { wm.addView(view, params) } catch (e: Exception) {
            probing = false
            Diag.set(ctx, Diag.ERROR, "${Diag.time()} цитата: $e"); return
        }
        // Фокус не пришёл — пробуем прочитать как есть (на старых Android можно и без фокуса)
        handler.postDelayed({ finish(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || view.hasWindowFocus()) }, 1200)
    }

    /** Текст из буфера, если он положен только что (не старше 20 с). */
    private fun readClip(ctx: Context): String? = try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val desc = cm.primaryClipDescription
        // Отметка времени в разных версиях Android — то от включения телефона, то обычное время: годится любое
        val ts = desc?.timestamp ?: 0L
        val fresh = ts == 0L || kotlin.math.abs(System.currentTimeMillis() - ts) < 20_000 ||
            kotlin.math.abs(android.os.SystemClock.elapsedRealtime() - ts) < 20_000
        if (!fresh) null
        else cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
    } catch (e: Exception) {
        Diag.set(ctx, Diag.ERROR, "${Diag.time()} буфер: $e"); null
    }
}
