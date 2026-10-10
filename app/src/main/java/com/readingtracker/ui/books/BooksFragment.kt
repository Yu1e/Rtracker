package com.readingtracker.ui.books

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.readingtracker.data.Book
import com.readingtracker.databinding.FragmentBooksBinding

class BooksFragment : Fragment() {

    private var _b: FragmentBooksBinding? = null
    private val b get() = _b!!
    private val vm: BooksViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentBooksBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = BooksAdapter(
            onStatusChange = { book, status -> vm.setStatus(book, status) },
            onDelete = { book -> confirmDelete(book) }
        )
        b.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerView.adapter = adapter

        vm.allBooks.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmDelete(book: Book) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить книгу?")
            .setMessage("«${book.title}» будет удалена из списка. Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ -> vm.deleteBook(book) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
