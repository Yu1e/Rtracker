package com.readingtracker.ui.books

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Toast
import com.readingtracker.data.Book
import com.readingtracker.databinding.FragmentBooksBinding
import com.readingtracker.service.ManualTimer
import com.readingtracker.ui.book.BookActivity

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
            onOpen = { book -> BookActivity.open(requireContext(), book.id) },
            onStatusChange = { book, status -> vm.setStatus(book, status) },
            onTimer = { book -> toggleTimer(book) }
        )
        b.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerView.adapter = adapter

        vm.allBooks.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        b.fabAdd.setOnClickListener { BookActivity.create(requireContext()) }
        ManualTimer.addListener(timerChanged)
    }

    private val timerChanged: () -> Unit = { _b?.recyclerView?.adapter?.notifyDataSetChanged() }

    /** Ручной таймер для любой книги: если трекер не поймал чтение или читается в другой читалке. */
    private fun toggleTimer(book: Book) {
        val ctx = requireContext()
        if (ManualTimer.isRunningFor(book.id)) {
            ManualTimer.stop(ctx)
            return
        }
        if (book.status != Book.STATUS_READING) vm.setStatus(book, Book.STATUS_READING)
        ManualTimer.start(ctx, book.id)
        Toast.makeText(ctx, ManualTimer.hint(), Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        ManualTimer.removeListener(timerChanged)
        super.onDestroyView()
        _b = null
    }
}
