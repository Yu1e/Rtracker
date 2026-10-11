package com.readingtracker.diag

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Журнал разведки: что служба видит на экранах читалок. Ограничен по размеру. */
object ScoutLog {
    private const val MAX_BYTES = 600_000L

    fun file(c: Context) = File(c.filesDir, "scout.txt")

    @Synchronized
    fun append(c: Context, line: String) {
        val f = file(c)
        if (f.exists() && f.length() > MAX_BYTES) {
            // Оставляем вторую половину журнала
            val text = f.readText()
            f.writeText(text.substring(text.length / 2))
        }
        val time = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date())
        f.appendText("$time $line\n")
    }

    fun clear(c: Context) { file(c).delete() }

    fun sizeKb(c: Context): Long = file(c).let { if (it.exists()) it.length() / 1024 else 0 }
}
