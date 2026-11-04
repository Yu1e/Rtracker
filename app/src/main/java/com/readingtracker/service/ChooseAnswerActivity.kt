package com.readingtracker.service

import android.app.Activity
import android.os.Bundle

/**
 * «Считать» из уведомления: через окно, чтобы шторка закрылась и форма книги
 * появилась поверх читалки. Само окно невидимое и сразу закрывается.
 */
class ChooseAnswerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            ReadingTrackerService.sendChoose(this, intent?.action ?: ReadingTrackerService.ACTION_CHOOSE_COUNT)
        } catch (_: Exception) {}
        finish()
        overridePendingTransition(0, 0)
    }
}
