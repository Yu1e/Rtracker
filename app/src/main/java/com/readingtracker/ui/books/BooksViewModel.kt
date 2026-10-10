package com.readingtracker.ui.books

import android.app.Application
import androidx.lifecycle.*
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import kotlinx.coroutines.launch

class BooksViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

    val allBooks: LiveData<List<Book>> = db.bookDao().getAllBooks()

    fun setStatus(book: Book, status: String) {
        viewModelScope.launch {
            val endDate = when (status) {
                Book.STATUS_FINISHED, Book.STATUS_ABANDONED -> System.currentTimeMillis()
                else -> null
            }
            db.bookDao().updateBook(book.copy(status = status, endDate = endDate))
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch { db.bookDao().deleteBook(book) }
    }
}
