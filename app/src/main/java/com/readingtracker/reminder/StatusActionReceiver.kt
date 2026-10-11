package com.readingtracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.readingtracker.data.BookActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Кнопки уведомления «Дочитала / Бросила / Отложила» и смахивание (= позже). */
class StatusActionReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_STATUS = "status"
        const val LATER = "later"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1)
        val status = intent.getStringExtra(EXTRA_STATUS) ?: return
        if (bookId <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (status == LATER) BookActions.askLater(context, bookId)
                else BookActions.setStatus(context, bookId, status)
            } finally {
                pending.finish()
            }
        }
    }
}
