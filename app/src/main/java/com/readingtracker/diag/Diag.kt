package com.readingtracker.diag

import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import java.text.SimpleDateFormat
import java.util.*

/** Журнал состояния для экрана «Проверка». Хранится в SharedPreferences, переживает падения. */
object Diag {
    const val HEARTBEAT = "heartbeat"
    const val TRACKED = "tracked"
    const val FOREGROUND = "foreground"
    const val EVENT = "event"
    const val ERROR = "error"

    fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("diag", Context.MODE_PRIVATE)

    fun set(c: Context, key: String, value: String) {
        prefs(c).edit().putString(key, value).apply()
    }

    fun get(c: Context, key: String): String? = prefs(c).getString(key, null)

    fun clearError(c: Context) {
        prefs(c).edit().remove(ERROR).apply()
    }

    fun time(): String = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date())

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
