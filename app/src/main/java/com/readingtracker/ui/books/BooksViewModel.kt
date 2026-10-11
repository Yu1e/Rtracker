package com.readingtracker.ui.books

import android.app.Application
import androidx.lifecycle.*
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.data.BookActions
import kotlinx.coroutines.launch

class BooksViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

    val allBooks: LiveData<List<Book>> = db.bookDao().getAllBooks()

    fun setStatus(book: Book, status: String) {
        viewModelScope.launch { BookActions.setStatus(getApplication(), book.id, status) }
    }
}
