package com.readingtracker.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.diag.Diag
import com.readingtracker.R
import com.readingtracker.ui.RtTheme
import kotlinx.coroutines.*

/** Пузырёк поверх читалки: «Продолжаем?», форма новой книги или маленький значок «＋ книга». */
class OverlayManager(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentView: View? = null

    // Компоненты Material требуют тему оформления, у службы её нет
    private val themed = ContextThemeWrapper(context, RtTheme.themeRes(context))

    fun show(
        appName: String,
        rememberedBookId: Long?,
        db: AppDatabase,
        onContinue: (Long) -> Unit,
        onNewBook: (title: String, author: String) -> Unit,
        onDismiss: () -> Unit
    ) {
        hide()
        val inflater = LayoutInflater.from(themed)
        if (rememberedBookId != null) {
            showContinueOrNew(inflater, appName, rememberedBookId, db, onContinue, onNewBook, onDismiss)
        } else {
            showNewBookForm(inflater, appName, onNewBook, onDismiss)
        }
    }

    /** Форма новой книги сразу. */
    fun showForm(appName: String, onNewBook: (String, String) -> Unit, onDismiss: () -> Unit) {
        hide()
        showNewBookForm(LayoutInflater.from(themed), appName, onNewBook, onDismiss)
    }

    /**
     * Маленький значок у края экрана, когда открытой книги нет: человек выбирает книгу в читалке,
     * значок ему не мешает. Нажатие открывает форму.
     */
    fun showPill(onTap: () -> Unit) {
        hide()
        val d = context.resources.displayMetrics.density
        val eink = RtTheme.isEink(context)
        val pill = TextView(themed).apply {
            text = "＋ книга"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (eink) 16f else 13f)
            setTextColor(if (eink) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            setPadding((12 * d).toInt(), (7 * d).toInt(), (12 * d).toInt(), (7 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 18 * d
                if (eink) { setColor(0xFFFFFFFF.toInt()); setStroke((2 * d).toInt(), 0xFF000000.toInt()) }
                else setColor(0xD92A2350.toInt())
            }
            alpha = 0.92f
            contentDescription = "добавить книгу, которую читаю"
            setOnClickListener { onTap() }
        }
        addToWindow(pill, focusable = false, small = true)
    }

    private fun showContinueOrNew(
        inflater: LayoutInflater,
        appName: String,
        bookId: Long,
        db: AppDatabase,
        onContinue: (Long) -> Unit,
        onNewBook: (String, String) -> Unit,
        onDismiss: () -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val book = db.bookDao().getBookById(bookId)
            withContext(Dispatchers.Main) {
                val view = inflater.inflate(R.layout.overlay_continue, null)
                view.findViewById<TextView>(R.id.tvAppName).text = appName
                view.findViewById<TextView>(R.id.tvBookName).text =
                    book?.let { Book.displayName(it) } ?: "предыдущую книгу"
                view.findViewById<Button>(R.id.btnContinue).setOnClickListener { onContinue(bookId) }
                view.findViewById<Button>(R.id.btnNewBook).setOnClickListener {
                    hide()
                    showNewBookForm(inflater, appName, onNewBook, onDismiss)
                }
                view.findViewById<ImageButton>(R.id.btnClose).setOnClickListener { onDismiss() }
                addToWindow(view, focusable = false)
            }
        }
    }

    private fun showNewBookForm(
        inflater: LayoutInflater,
        appName: String,
        onNewBook: (String, String) -> Unit,
        onDismiss: () -> Unit
    ) {
        val view = inflater.inflate(R.layout.overlay_new_book, null)
        view.findViewById<TextView>(R.id.tvAppName).text = appName
        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etAuthor = view.findViewById<EditText>(R.id.etAuthor)

        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val title = etTitle.text.toString().trim()
            val author = etAuthor.text.toString().trim()
            // Достаточно любого одного поля
            if (title.isEmpty() && author.isEmpty()) {
                etTitle.error = "Заполните название или автора"
                return@setOnClickListener
            }
            hideKeyboard(etTitle)
            onNewBook(title, author)
        }
        view.findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            hideKeyboard(etTitle)
            onDismiss()
        }

        addToWindow(view, focusable = true)
        etTitle.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etTitle, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun addToWindow(view: View, focusable: Boolean, small: Boolean = false) {
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        val params = WindowManager.LayoutParams(
            if (small) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (small) {
                // Значок в правом верхнем углу, под строкой состояния
                gravity = Gravity.TOP or Gravity.END
                val d = context.resources.displayMetrics.density
                x = (10 * d).toInt()
                y = (56 * d).toInt()
            } else {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 80
            }
            if (focusable) {
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
        }
        try {
            wm.addView(view, params)
            currentView = view
            Diag.set(context, Diag.EVENT, "${Diag.time()} пузырёк показан")
        } catch (e: Exception) {
            Diag.set(context, Diag.ERROR, "${Diag.time()} addView: $e")
        }
    }

    fun hide() {
        currentView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            currentView = null
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
