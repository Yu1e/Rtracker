package com.readingtracker.ui.books

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.readingtracker.Constants
import com.readingtracker.databinding.FragmentBooksBinding
import com.readingtracker.launcher.ReaderLauncher
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
            onStatusChange = { book, status -> vm.setStatus(book, status) }
        )
        b.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerView.adapter = adapter

        vm.allBooks.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        b.fabAdd.setOnClickListener { BookActivity.create(requireContext()) }
    }

    override fun onResume() {
        super.onResume()
        buildReaderChips()
    }

    /** Кнопки читалок: открывают читалку через трекер. */
    private fun buildReaderChips() {
        val ctx = requireContext()
        b.readerChips.removeAllViews()
        val readers = Constants.detectTrackedApps(ctx).entries.distinctBy { it.value }
        b.tvReadersLabel.visibility = if (readers.isEmpty()) View.GONE else View.VISIBLE
        for ((pkg, name) in readers) {
            b.readerChips.addView(Chip(ctx).apply {
                text = name
                setOnClickListener { ReaderLauncher.open(ctx, pkg) }
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
