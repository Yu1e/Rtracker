package com.readingtracker.backup

import android.content.Context
import androidx.work.*
import com.readingtracker.diag.Diag
import com.readingtracker.reminder.ReminderNotifier
import com.readingtracker.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/** Фоновая работа несколько раз в сутки: автокопия, напоминания, виджет. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DailyWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily", ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }

    override suspend fun doWork(): Result {
        val c = applicationContext
        try { BackupManager.autoBackupIfNeeded(c) } catch (e: Exception) {
            Diag.set(c, Diag.ERROR, "${Diag.time()} автокопия: $e")
        }
        try { ReminderNotifier.check(c) } catch (e: Exception) {
            Diag.set(c, Diag.ERROR, "${Diag.time()} напоминания: $e")
        }
        WidgetUpdater.update(c)
        return Result.success()
    }
}
