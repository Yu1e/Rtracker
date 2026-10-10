package com.readingtracker.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import com.readingtracker.diag.Diag
import com.readingtracker.R
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import kotlinx.coroutines.*

class OverlayManager(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentView: View? = null

    private val overlayType =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    /** Show the bubble. Decides content based on whether we remember the last book. */
    fun show(
        appName: String,
        rememberedBookId: Long?,
        db: AppDatabase,
        onContinue: (Long) -> Unit,
        onNewBook: (title: String, author: String) -> Unit,
        onDismiss: () -> Unit
    ) {
        hide()

        // Компоненты Material требуют тему оформления, у службы её нет
        val inflater = LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_ReadingTracker))

        if (rememberedBookId != null) {
            showContinueOrNew(
                inflater, appName, rememberedBookId, db,
                onContinue, onNewBook, onDismiss
            )
        } else {
            showNewBookForm(inflater, appName, onNewBook, onDismiss)
        }
    }

    // ── "Continue reading X or start new?" ────────────────────────────────────

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
                    book?.let { "«${it.title}»" } ?: "предыдущую книгу"

                view.findViewById<Button>(R.id.btnContinue).setOnClickListener {
                    onContinue(bookId)
                }
                view.findViewById<Button>(R.id.btnNewBook).setOnClickListener {
                    hide()
                    showNewBookForm(inflater, appName, onNewBook, onDismiss)
                }
                view.findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
                    onDismiss()
                }

                addToWindow(view, focusable = false)
            }
        }
    }

    // ── "Enter new book" form ──────────────────────────────────────────────────

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
            if (title.isEmpty()) {
                etTitle.error = "Введите название"
                return@setOnClickListener
            }
            hideKeyboard(etTitle)
            onNewBook(title, author)
        }

        view.findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            hideKeyboard(etTitle)
            onDismiss()
        }

        // Focusable so keyboard works
        addToWindow(view, focusable = true)

        // Request focus & show keyboard
        etTitle.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etTitle, InputMethodManager.SHOW_IMPLICIT)
    }

    // ── Window helpers ─────────────────────────────────────────────────────────

    private fun addToWindow(view: View, focusable: Boolean) {
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 80
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
            Diag.set(context, Diag.ERROR, "${Diag.time()} addView: ${e}")
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
