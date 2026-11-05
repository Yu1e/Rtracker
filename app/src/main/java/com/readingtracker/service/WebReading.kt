package com.readingtracker.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

/**
 * Книги «В интернете»: открываются в браузере (Хром, если он есть), трекер следит за адресной строкой.
 * Адреса сравниваются без «https://», «www.» и «#…»:
 * - тот же адрес (или в строке виден только тот же сайт) — время идёт;
 * - похожий: тот же сайт и совпадает начало пути (отличаются одна-две последние части или только «?…») —
 *   трекер спрашивает «Это следующая часть книги?»;
 * - другой — пауза.
 */
object WebReading {

    const val CHROME = "com.android.chrome"

    enum class Match { SAME, SIMILAR, OTHER }

    /** Браузер: Хром, если установлен; иначе тот, что открывает ссылки по умолчанию. */
    fun browserPkg(ctx: Context): String? {
        val pm = ctx.packageManager
        if (installed(pm, CHROME)) return CHROME
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val def = pm.resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (def != null && def != "android") return def
        return pm.queryIntentActivities(view, 0).firstOrNull()?.activityInfo?.packageName
    }

    fun browserName(ctx: Context): String = if (browserPkg(ctx) == CHROME) "Хром" else "браузер"

    private fun installed(pm: PackageManager, pkg: String) =
        try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }

    fun withScheme(url: String): String {
        val u = url.trim()
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(u)) u else "https://$u"
    }

    /** Открыть книгу в браузере; трекер включается и следит за адресом. */
    fun open(ctx: Context, bookId: Long, url: String) {
        if (url.isBlank()) { Toast.makeText(ctx, "У книги нет адреса", Toast.LENGTH_SHORT).show(); return }
        val pkg = browserPkg(ctx)
        if (pkg == null) { Toast.makeText(ctx, "Браузер не найден", Toast.LENGTH_SHORT).show(); return }
        try { ReadingTrackerService.startForWeb(ctx, bookId, pkg) } catch (_: Exception) {}
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(withScheme(url))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(Intent(intent).setPackage(pkg)) }
        catch (_: Exception) {
            try { ctx.startActivity(intent) } catch (_: Exception) { Toast.makeText(ctx, "Адрес не открылся", Toast.LENGTH_SHORT).show() }
        }
    }

    /** «litnet.com/ru/reader/kniga?c=12»: без схемы, «www.», «#…» и «/» на конце; сайт строчными. */
    fun normalize(raw: String): String {
        var u = raw.trim().replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
        u = u.substringBefore('#')
        val slash = u.indexOfFirst { it == '/' || it == '?' }
        var host = if (slash < 0) u else u.substring(0, slash)
        val rest = if (slash < 0) "" else u.substring(slash)
        host = host.lowercase().removePrefix("www.").substringBefore(':')
        return (host + rest).trimEnd('/')
    }

    private fun host(n: String) = n.substringBefore('/').substringBefore('?')
    private fun path(n: String) = n.removePrefix(host(n)).substringBefore('?').trim('/')
    private fun query(n: String) = n.substringAfter('?', "")

    fun classify(bookUrl: String, current: String): Match {
        val a = normalize(bookUrl); val b = normalize(current)
        if (a.isEmpty() || b.isEmpty()) return Match.OTHER
        if (a == b) return Match.SAME
        if (host(a) != host(b)) return Match.OTHER
        // В строке виден только сайт (браузер прячет остальное) — считаем, что книга та же
        if (path(b).isEmpty() && query(b).isEmpty()) return Match.SAME
        val pa = path(a).split('/').filter { it.isNotEmpty() }
        val pb = path(b).split('/').filter { it.isNotEmpty() }
        if (pa == pb) return if (query(a) == query(b)) Match.SAME else Match.SIMILAR
        var common = 0
        while (common < minOf(pa.size, pb.size) && pa[common] == pb[common]) common++
        val longest = maxOf(pa.size, pb.size)
        return if (common >= 1 && common >= longest - 2) Match.SIMILAR else Match.OTHER
    }
}
