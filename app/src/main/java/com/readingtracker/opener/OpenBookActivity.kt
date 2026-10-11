package com.readingtracker.opener

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.readingtracker.Constants
import com.readingtracker.Prefs
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.BookActions
import com.readingtracker.databinding.ActivityOpenBookBinding
import com.readingtracker.diag.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Трекер как посредник: «Открыть с помощью → Трекер чтения».
 * Читает автора и название из файла, записывает книгу, передаёт файл в Alreader.
 */
class OpenBookActivity : AppCompatActivity() {

    private lateinit var b: ActivityOpenBookBinding
    private var uri: Uri? = null
    private var mime: String? = null
    private var readerPkg: String? = null
    private var readerName: String = "читалку"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOpenBookBinding.inflate(layoutInflater)
        setContentView(b.root)
        setFinishOnTouchOutside(false)

        uri = intent.data
        mime = intent.type ?: uri?.let { contentResolver.getType(it) }
        if (uri == null) { finish(); return }

        val reader = Constants.detectTrackedApps(this).entries.firstOrNull { it.value == "Alreader" }
        readerPkg = reader?.key
        readerName = reader?.value ?: "читалку"
        b.tvTarget.text = if (readerPkg != null) "Откроется в $readerName"
        else "Alreader не найден — откроется выбор приложения"

        b.btnSave.setOnClickListener { saveAndOpen() }
        b.btnOpenOnly.setOnClickListener { openInReader(); finish() }

        b.progress.visibility = View.VISIBLE
        b.form.visibility = View.INVISIBLE
        lifecycleScope.launch {
            val fileName = queryName(uri!!)
            val meta = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri!!)?.use { BookFileParser.parse(it, fileName) }
                } catch (e: Exception) {
                    Diag.set(this@OpenBookActivity, Diag.ERROR, "${Diag.time()} разбор файла: $e")
                    BookFileParser.fromFileName(fileName)
                }
            }
            b.etTitle.setText(meta?.title ?: "")
            b.etAuthor.setText(meta?.author ?: "")
            b.tvFile.text = fileName ?: ""

            val existing = meta?.let {
                AppDatabase.getInstance(this@OpenBookActivity).bookDao().findBook(it.title, it.author)
            }
            if (existing != null) {
                b.tvHeader.text = "Эта книга уже в списке"
                b.btnSave.text = "Продолжить чтение"
            }
            b.progress.visibility = View.GONE
            b.form.visibility = View.VISIBLE
        }
    }

    private fun queryName(u: Uri): String? = try {
        contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: u.lastPathSegment
    } catch (e: Exception) {
        u.lastPathSegment
    }

    private fun saveAndOpen() {
        val title = b.etTitle.text.toString().trim()
        val author = b.etAuthor.text.toString().trim()
        if (title.isEmpty() && author.isEmpty()) {
            b.etTitle.error = "Заполните название или автора"
            return
        }
        lifecycleScope.launch {
            val pkg = readerPkg ?: com.readingtracker.data.Book.PKG_OTHER
            val name = if (readerPkg != null) readerName else "Другое"
            val id = BookActions.addNewBook(this@OpenBookActivity, title, author, pkg, name)
            // Следующий вход в читалку не потребует пузырька
            readerPkg?.let { Prefs.setPendingBook(this@OpenBookActivity, it, id) }
            openInReader()
            finish()
        }
    }

    private fun openInReader() {
        val u = uri ?: return
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(u, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pkg = readerPkg
        if (pkg != null) {
            try {
                startActivity(Intent(view).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
                try {
                    // Alreader может не объявлять этот тип файла — пробуем без типа
                    startActivity(Intent(view).setDataAndType(u, null).setPackage(pkg))
                    return
                } catch (_: ActivityNotFoundException) {}
            } catch (e: SecurityException) {
                Diag.set(this, Diag.ERROR, "${Diag.time()} передача файла: $e")
            }
        }
        // Выбор приложения без нас самих
        val chooser = Intent.createChooser(view, "Открыть книгу").apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(this@OpenBookActivity, OpenBookActivity::class.java)))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_LONG).show()
        }
    }
}
