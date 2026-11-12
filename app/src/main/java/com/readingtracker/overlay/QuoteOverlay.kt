package com.readingtracker.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.readingtracker.Prefs
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.data.Quote
import com.readingtracker.diag.Diag
import com.readingtracker.service.QuoteCatcher
import com.readingtracker.service.QuoteText
import com.readingtracker.service.ReadingTrackerService
import com.readingtracker.ui.RtTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Пузырёк «цитата» поверх читалки: текст из буфера (можно поправить), книга (можно сменить),
 * теги (последние — нажатием), «Сохранить» / «Поправлю потом» / «Не надо».
 *
 * Если трекеру виден текст страницы («Свет», Яндекс Книги, ЛитРес, Хром), границы цитаты
 * двигаются кнопками: начало и конец — на слово или до границы предложения («фраза»), туда и обратно.
 * «вся страница» — вся видимая страница.
 *
 * «длинная» — цитата на несколько страниц: окно сворачивается в полоску у края, страницы листаешь
 * сама, «↓ в конец» / «↑ в начало» добавляют видимую страницу. Новое «Копировать» в это время
 * дописывается к той же цитате (в AlReader страница трекеру не видна — только так).
 * «готово» — окно снова целиком, лишнее убирается кнопками или руками.
 */
object QuoteOverlay {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var current: View? = null

    // ── Состояние цитаты (живёт, пока окно на экране, в том числе свёрнутым) ──
    private var appCtx: Context? = null
    private var pkg = ""
    private var books: List<Book> = emptyList()
    private var bookId: Long? = null
    private var recentTags: List<String> = emptyList()
    private var tagsText = ""
    private var dehyphen = false
    /** Текст страницы (страниц): абзацы через пустую строку. */
    private var page = ""
    /** Цитата — отрезок страницы [s, e); s < 0 — цитата правлена вручную и со страницей не связана. */
    private var s = -1
    private var e = -1
    /** Текст цитаты, когда он не отрезок страницы. */
    private var freeText = ""
    private var strip = false
    private var pagesAdded = 0

    private class Pal(val bg: Int, val ink: Int, val ink2: Int, val line: Int, val accent: Int, val onAccent: Int)

    private fun pal(ctx: Context): Pal {
        val eink = RtTheme.isEink(ctx)
        val night = !eink && when (Prefs.night(ctx)) {
            "dark" -> true
            "light" -> false
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        return when {
            eink -> Pal(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF333333.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
            night -> Pal(0xFF1E1A2E.toInt(), 0xFFF3F0FF.toInt(), 0xFFBDB6E0.toInt(), 0x40FFFFFF, 0xFFC9B6FF.toInt(), 0xFF1E1838.toInt())
            else -> Pal(0xFFFFFFFF.toInt(), 0xFF1E1838.toInt(), 0xFF5A5478.toInt(), 0x33362A6B, 0xFF6343F0.toInt(), 0xFFFFFFFF.toInt())
        }
    }

    /** Окно на экране (целиком или полоской). */
    val isOpen get() = current != null
    /** Свёрнуто в полоску «длинная цитата»: новое «Копировать» дописывается. */
    val isCollecting get() = current != null && strip

    fun show(ctx: Context, pkg: String, text: String, pageParas: List<String>) {
        scope.launch {
            val db = AppDatabase.getInstance(ctx)
            val (list, id, tags) = withContext(Dispatchers.IO) {
                val all = db.bookDao().getAllBooksSync()
                val open = all.filter { Book.isOpen(it.status) }
                // Книга: та, что сейчас идёт в трекере; иначе последняя в этой читалке; иначе последняя начатая
                val id = ReadingTrackerService.currentBookId
                    ?: db.sessionDao().getLastBookIdForApp(pkg)
                    ?: open.maxByOrNull { it.startDate }?.id
                val list = (open + all.filter { it.id == id }).distinctBy { it.id }
                    .sortedByDescending { it.startDate }
                val tags = db.quoteDao().recentTagRows().flatMap { Quote.splitTags(it) }.distinct().take(14)
                Triple(list, id, tags)
            }
            hide()
            appCtx = ctx.applicationContext
            this@QuoteOverlay.pkg = pkg
            books = list; bookId = id; recentTags = tags; tagsText = ""
            dehyphen = QuoteCatcher.names[pkg] == "ЛитРес"
            page = QuoteText.cleanPage(pageParas, dehyphen)
            val r = QuoteText.locate(page, text)
            if (r != null) { s = r.first; e = r.last + 1; freeText = "" } else { s = -1; e = -1; freeText = text }
            strip = false; pagesAdded = 0
            buildCard(ctx)
        }
    }

    fun hide() {
        val v = current ?: return
        current = null
        try { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) {}
    }

    private fun quoteNow(): String = if (s >= 0) page.substring(s, e) else freeText

    /** Текст поправлен руками: ищем его на странице снова. false — не нашёлся. */
    private fun sync(typed: String): Boolean {
        if (s >= 0 && page.substring(s, e) == typed) return true
        val r = QuoteText.locate(page, typed)
        return if (r != null) { s = r.first; e = r.last + 1; true }
        else { s = -1; e = -1; freeText = typed; false }
    }

    // ── Полоска «длинная цитата» ──────────────────────────────────────────────

    /** Новое «Копировать», пока окно свёрнуто: кусок дописывается (или цитата растягивается до него). */
    fun onCopied(text: String) {
        val ctx = appCtx ?: return
        val r = if (page.isNotEmpty()) QuoteText.locate(page, text) else null
        if (r != null && s >= 0) {
            if (r.first < s) s = r.first
            if (r.last + 1 > e) e = r.last + 1
        } else {
            val now = quoteNow()
            freeText = if (now.isBlank()) text else "$now $text"
            s = -1; e = -1
        }
        pagesAdded++
        toast(ctx, "Дописано к цитате")
        buildStrip(ctx)
    }

    private fun addPage(ctx: Context, atEnd: Boolean) {
        val paras = QuoteCatcher.pageParagraphs(pkg)
        val add = QuoteText.cleanPage(paras, dehyphen)
        if (add.isEmpty()) {
            toast(ctx, "Страница не видна трекеру: выдели следующий кусок и скопируй — он допишется")
            return
        }
        if (s < 0 && freeText.isNotBlank()) sync(freeText)
        val m = QuoteText.merge(page, add, atEnd)
        if (s >= 0) {
            s += m.shift; e += m.shift
            page = m.text
            if (atEnd) e = maxOf(e, m.pageEnd) else s = minOf(s, m.pageStart)
        } else {
            // Цитата не на странице (правлена руками): новые абзацы дописываются к тексту
            val fresh = add.split("\n\n").filter { it !in page.split("\n\n") }.joinToString("\n\n")
            page = m.text
            if (fresh.isNotEmpty()) freeText = if (atEnd) "$freeText\n\n$fresh".trim() else "$fresh\n\n$freeText".trim()
        }
        pagesAdded++
        buildStrip(ctx)
    }

    private fun buildStrip(ctx: Context) {
        hide()
        strip = true
        val d = ctx.resources.displayMetrics.density
        fun px(v: Float) = (v * d).toInt()
        val p = pal(ctx)
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(10f), px(6f), px(6f), px(6f))
            background = GradientDrawable().apply { cornerRadius = 20 * d; setColor(p.bg); setStroke(px(1f), p.line) }
            elevation = 8 * d
        }
        fun chip(t: String, filled: Boolean, onTap: () -> Unit) = TextView(ctx).apply {
            text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(if (filled) p.onAccent else p.accent)
            setPadding(px(10f), px(7f), px(10f), px(7f))
            background = GradientDrawable().apply {
                cornerRadius = 16 * d
                if (filled) setColor(p.accent) else setStroke(px(1f), p.accent)
            }
            setOnClickListener { onTap() }
        }
        bar.addView(TextView(ctx).apply {
            text = if (pagesAdded > 0) "длинная · +$pagesAdded" else "длинная"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(p.ink2)
            setPadding(0, 0, px(6f), 0)
        })
        val lp = { LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(4f) } }
        bar.addView(chip("↑ в начало", false) { addPage(ctx, atEnd = false) }, lp())
        bar.addView(chip("↓ в конец", false) { addPage(ctx, atEnd = true) }, lp())
        bar.addView(chip("готово", true) { strip = false; buildCard(ctx) }, lp())

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Не забирает фокус и касания: читалка листается как обычно
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = px(8f); y = px(28f) }
        try {
            (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(bar, params)
            current = bar
        } catch (ex: Exception) {
            Diag.set(ctx, Diag.ERROR, "${Diag.time()} цитата, полоска: $ex")
        }
    }

    // ── Окно целиком ──────────────────────────────────────────────────────────

    private fun buildCard(ctx: Context) {
        hide()
        strip = false
        val d = ctx.resources.displayMetrics.density
        fun px(v: Float) = (v * d).toInt()
        val p = pal(ctx)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), px(12f), px(16f), px(14f))
            background = GradientDrawable().apply {
                cornerRadius = 16 * d; setColor(p.bg); setStroke(px(1f), p.line)
            }
            elevation = 10 * d
        }
        fun label(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
            this.text = t; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        fun button(t: String, filled: Boolean) = TextView(ctx).apply {
            this.text = t; gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(if (filled) p.onAccent else p.accent)
            setPadding(px(10f), px(10f), px(10f), px(10f))
            background = GradientDrawable().apply {
                cornerRadius = 22 * d
                if (filled) setColor(p.accent) else setStroke(px(1.3f), p.accent)
            }
            isClickable = true; isFocusable = true
        }
        fun field(hint: String, multi: Boolean) = EditText(ctx).apply {
            this.hint = hint
            setTextColor(p.ink); setHintTextColor(p.ink2)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (multi) 15f else 14f)
            background = GradientDrawable().apply { cornerRadius = 10 * d; setStroke(px(1f), p.line) }
            setPadding(px(10f), px(8f), px(10f), px(8f))
            inputType = if (multi) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                else InputType.TYPE_CLASS_TEXT
            if (multi) { minLines = 2; maxLines = 8; isVerticalScrollBarEnabled = true; gravity = Gravity.TOP or Gravity.START }
            else isSingleLine = true
        }

        // Шапка: «цитата» и крестик
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(label("цитата", 13f, p.ink2), LinearLayout.LayoutParams(0, -2, 1f))
        val close = label("✕", 18f, p.ink2).apply { setPadding(px(10f), px(2f), px(4f), px(2f)); contentDescription = "закрыть" }
        head.addView(close)
        card.addView(head)

        // Книга
        fun bookName(id: Long?) = books.firstOrNull { it.id == id }?.let { Book.displayName(it) } ?: "выбрать книгу"
        val bookView = label("${bookName(bookId)}  ▾", 15f, p.ink, bold = true).apply {
            setPadding(0, px(4f), 0, px(8f)); maxLines = 2
            contentDescription = "книга, сменить"
        }
        bookView.setOnClickListener {
            val menu = PopupMenu(ctx, bookView)
            books.forEachIndexed { i, b -> menu.menu.add(0, i, i, Book.displayName(b)) }
            menu.setOnMenuItemClickListener { item ->
                bookId = books[item.itemId].id
                bookView.text = "${bookName(bookId)}  ▾"
                true
            }
            try { menu.show() } catch (ex: Exception) { Diag.set(ctx, Diag.ERROR, "${Diag.time()} цитата, книги: $ex") }
        }
        card.addView(bookView)

        // Текст цитаты
        val etText = field("текст цитаты", multi = true).apply { setText(quoteNow()) }
        card.addView(etText, LinearLayout.LayoutParams(-1, -2))

        // Границы цитаты по тексту страницы
        if (page.isNotEmpty()) {
            fun step(op: () -> Unit) {
                if (!sync(etText.text.toString())) {
                    toast(ctx, "Текст правлен руками, на странице его не найти — кнопки тут не помогут")
                    return
                }
                op()
                etText.setText(page.substring(s, e))
            }
            fun chip(t: String, onTap: () -> Unit) = TextView(ctx).apply {
                text = t; gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(p.accent)
                setPadding(px(2f), px(6f), px(2f), px(6f))
                background = GradientDrawable().apply { cornerRadius = 12 * d; setStroke(px(1f), p.line) }
                setOnClickListener { onTap() }
            }
            fun row(name: String, vararg items: Pair<String, () -> Unit>) = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(label(name, 12f, p.ink2), LinearLayout.LayoutParams(px(52f), -2))
                for ((t, f) in items) addView(chip(t, f), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(4f) })
            }
            card.addView(row("начало",
                "＋ фраза" to { step { s = QuoteText.startPlusSentence(page, s) } },
                "＋ слово" to { step { s = QuoteText.startPlusWord(page, s) } },
                "− слово" to { step { s = QuoteText.startMinusWord(page, s, e) } },
                "− фраза" to { step { s = QuoteText.startMinusSentence(page, s, e) } }
            ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8f) })
            card.addView(row("конец",
                "− фраза" to { step { e = QuoteText.endMinusSentence(page, s, e) } },
                "− слово" to { step { e = QuoteText.endMinusWord(page, s, e) } },
                "＋ слово" to { step { e = QuoteText.endPlusWord(page, e) } },
                "＋ фраза" to { step { e = QuoteText.endPlusSentence(page, e) } }
            ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6f) })
        }

        // «вся страница» и «длинная»
        val links = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        if (page.length > quoteNow().length + 20) {
            links.addView(label("вся страница", 13f, p.accent).apply {
                setPadding(0, px(8f), px(18f), px(2f))
                setOnClickListener { s = 0; e = page.length; etText.setText(page); etText.setSelection(0) }
            })
        }
        links.addView(label("длинная: ещё страницы", 13f, p.accent).apply {
            setPadding(0, px(8f), 0, px(2f))
            setOnClickListener {
                sync(etText.text.toString())
                tagsText = currentTags?.text?.toString() ?: tagsText
                hideKeyboard(ctx, etText)
                buildStrip(ctx)
            }
        })
        card.addView(links)

        // Теги
        val etTags = field("теги через пробел", multi = false).apply { setText(tagsText) }
        currentTags = etTags
        card.addView(etTags, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8f) })
        if (recentTags.isNotEmpty()) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (t in recentTags) {
                row.addView(label("#$t", 13f, p.accent).apply {
                    setPadding(px(10f), px(5f), px(10f), px(5f))
                    background = GradientDrawable().apply { cornerRadius = 14 * d; setStroke(px(1f), p.line) }
                    setOnClickListener {
                        val now = Quote.parseTags(etTags.text.toString())
                        if (t !in now) etTags.setText((now + t).joinToString(" "))
                        etTags.setSelection(etTags.text.length)
                    }
                }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(6f) })
            }
            card.addView(HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6f) })
        }

        // Кнопки
        val save = button("Сохранить", filled = true)
        card.addView(save, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12f) })
        val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val later = button("Поправлю потом", filled = false)
        val no = button("Не надо", filled = false)
        row2.addView(later, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(8f) })
        row2.addView(no, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8f) })

        fun closeAll() { hideKeyboard(ctx, etText); hide(); currentTags = null }
        fun store(draft: Boolean) {
            val id = bookId
            val q = etText.text.toString().trim()
            if (id == null) { toast(ctx, "Выбери книгу"); return }
            if (q.isEmpty()) { toast(ctx, "Цитата пустая"); return }
            val tags = Quote.joinTags(Quote.parseTags(etTags.text.toString()))
            closeAll()
            scope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(ctx).quoteDao().insert(Quote(bookId = id, text = q, tags = tags, draft = draft))
                }
                toast(ctx, if (draft) "Цитата сохранена, поправить — в «цитатах»" else "Цитата сохранена")
            }
        }
        save.setOnClickListener { store(false) }
        later.setOnClickListener { store(true) }
        no.setOnClickListener { closeAll() }
        close.setOnClickListener { closeAll() }

        // Длинная цитата не выталкивает кнопки за экран: карточка прокручивается
        val scroll = ScrollView(ctx).apply { addView(card); isFillViewport = false }
        val holder = LinearLayout(ctx).apply {
            setPadding(px(12f), px(8f), px(12f), px(8f))
            addView(scroll, LinearLayout.LayoutParams(-1, -2))
        }
        val maxH = (ctx.resources.displayMetrics.heightPixels * 0.8f).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = px(24f)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        holder.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.height > maxH) { params.height = maxH; try { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(v, params) } catch (_: Exception) {} }
        }
        try {
            (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(holder, params)
            current = holder
        } catch (ex: Exception) {
            Diag.set(ctx, Diag.ERROR, "${Diag.time()} цитата, окно: $ex")
        }
    }

    private var currentTags: EditText? = null

    private fun hideKeyboard(ctx: Context, v: View) {
        try { (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0) } catch (_: Exception) {}
    }

    private fun toast(ctx: Context, t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
}
