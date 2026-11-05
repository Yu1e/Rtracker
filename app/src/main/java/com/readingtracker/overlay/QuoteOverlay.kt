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
 * «Вся страница» — заменить текст всем, что видно на странице (удобно в «Свете»: скопировать
 * слово, взять страницу и обрезать лишнее).
 */
object QuoteOverlay {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var current: View? = null

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

    fun show(ctx: Context, pkg: String, text: String, pageText: String) {
        scope.launch {
            val db = AppDatabase.getInstance(ctx)
            val (books, bookId, recentTags) = withContext(Dispatchers.IO) {
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
            build(ctx, pkg, text, pageText, books, bookId, recentTags)
        }
    }

    fun hide() {
        val v = current ?: return
        current = null
        try { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) {}
    }

    private fun build(ctx: Context, pkg: String, text: String, pageText: String,
                      books: List<Book>, initialBook: Long?, recentTags: List<String>) {
        hide()
        val d = ctx.resources.displayMetrics.density
        fun px(v: Float) = (v * d).toInt()
        val p = pal(ctx)
        var bookId = initialBook

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
            if (multi) { minLines = 2; maxLines = 7; isVerticalScrollBarEnabled = true; gravity = Gravity.TOP or Gravity.START }
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
            try { menu.show() } catch (e: Exception) { Diag.set(ctx, Diag.ERROR, "${Diag.time()} цитата, книги: $e") }
        }
        card.addView(bookView)

        // Текст цитаты
        val etText = field("текст цитаты", multi = true).apply { setText(text) }
        card.addView(etText, LinearLayout.LayoutParams(-1, -2))
        if (pageText.isNotBlank() && pageText.length > text.length + 20) {
            val whole = label("вся страница", 13f, p.accent).apply {
                setPadding(0, px(6f), 0, px(2f))
                setOnClickListener { etText.setText(pageText); etText.requestFocus(); etText.setSelection(0) }
            }
            card.addView(whole)
        }

        // Теги
        val etTags = field("теги через пробел", multi = false)
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

        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        fun closeAll() { imm.hideSoftInputFromWindow(etText.windowToken, 0); hide() }
        fun store(draft: Boolean) {
            val id = bookId
            val q = etText.text.toString().trim()
            if (id == null) { Toast.makeText(ctx, "Выбери книгу", Toast.LENGTH_SHORT).show(); return }
            if (q.isEmpty()) { Toast.makeText(ctx, "Цитата пустая", Toast.LENGTH_SHORT).show(); return }
            val tags = Quote.joinTags(Quote.parseTags(etTags.text.toString()))
            closeAll()
            scope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(ctx).quoteDao().insert(Quote(bookId = id, text = q, tags = tags, draft = draft))
                }
                Toast.makeText(ctx, if (draft) "Цитата сохранена, поправить — в «цитатах»" else "Цитата сохранена", Toast.LENGTH_SHORT).show()
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
        } catch (e: Exception) {
            Diag.set(ctx, Diag.ERROR, "${Diag.time()} цитата, окно: $e")
        }
    }
}
