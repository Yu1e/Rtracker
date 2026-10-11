package com.readingtracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.readingtracker.backup.DailyWorker
import com.readingtracker.diag.Diag

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        DailyWorker.schedule(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                Diag.prefs(this).edit()
                    .putString(Diag.ERROR, Diag.time() + " ПАДЕНИЕ\n" + Log.getStackTraceString(error).take(3000))
                    .commit()
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, error)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "Трекер чтения",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновое отслеживание чтения"
                setShowBadge(false)
            }
            val reminders = NotificationChannel(
                Constants.REMINDER_CHANNEL_ID,
                "Напоминания о книгах",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Вопрос «Дочитана?» о книгах без статуса"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(reminders)
        }
    }
}
