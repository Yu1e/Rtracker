package com.readingtracker.opener

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Достаёт автора и название из fb2, fb2.zip и epub. */
object BookFileParser {

    data class Meta(val title: String, val author: String)

    fun parse(input: InputStream, fileName: String?): Meta? {
        val stream = BufferedInputStream(input)
        stream.mark(4)
        val head = ByteArray(2)
        val n = stream.read(head)
        stream.reset()
        val result = if (n == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            parseZip(stream)
        } else {
            parseFb2(stream)
        }
        return result?.takeIf { it.title.isNotBlank() || it.author.isNotBlank() }
            ?: fromFileName(fileName)
    }

    // ── zip: epub или fb2.zip ─────────────────────────────────────────────────

    private fun parseZip(input: InputStream): Meta? {
        val small = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                val lower = name.lowercase()
                when {
                    lower.endsWith(".fb2") -> return parseFb2(zip)
                    lower == "meta-inf/container.xml" || lower.endsWith(".opf") ->
                        small[name] = zip.readBytes()
                }
            }
        }
        val container = small.entries.firstOrNull { it.key.lowercase() == "meta-inf/container.xml" }?.value
        val opfPath = container?.let { opfPathFromContainer(it) }
        val opf = opfPath?.let { small[it] } ?: small.entries.firstOrNull { it.key.lowercase().endsWith(".opf") }?.value
        return opf?.let { parseOpf(it) }
    }

    private fun opfPathFromContainer(bytes: ByteArray): String? {
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(bytes), null)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name.substringAfter(':') == "rootfile") {
                return p.getAttributeValue(null, "full-path")
            }
        }
        return null
    }

    private fun parseOpf(bytes: ByteArray): Meta {
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(bytes), null)
        var title = ""
        val authors = mutableListOf<String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name.substringAfter(':')) {
                "title" -> if (title.isEmpty()) title = p.nextText().trim()
                "creator" -> p.nextText().trim().takeIf { it.isNotEmpty() }?.let { authors += it }
                "manifest" -> break
            }
        }
        return Meta(title, authors.joinToString(", "))
    }

    // ── fb2 ───────────────────────────────────────────────────────────────────

    private fun parseFb2(input: InputStream): Meta? {
        val p = Xml.newPullParser()
        p.setInput(input, null)
        var inTitleInfo = false
        var inAuthor = false
        var first = ""; var middle = ""; var last = ""; var nick = ""
        val authors = mutableListOf<String>()
        var title = ""
        while (true) {
            val ev = try { p.next() } catch (e: Exception) { break }
            if (ev == XmlPullParser.END_DOCUMENT) break
            val name = p.name?.substringAfter(':')
            if (ev == XmlPullParser.START_TAG) {
                when (name) {
                    "title-info" -> inTitleInfo = true
                    "author" -> if (inTitleInfo) { inAuthor = true; first = ""; middle = ""; last = ""; nick = "" }
                    "first-name" -> if (inAuthor) first = p.nextText().trim()
                    "middle-name" -> if (inAuthor) middle = p.nextText().trim()
                    "last-name" -> if (inAuthor) last = p.nextText().trim()
                    "nickname" -> if (inAuthor) nick = p.nextText().trim()
                    "book-title" -> if (inTitleInfo) title = p.nextText().trim()
                    "body" -> break
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (name) {
                    "author" -> if (inAuthor) {
                        inAuthor = false
                        val full = listOf(first, middle, last).filter { it.isNotEmpty() }.joinToString(" ")
                        (full.ifEmpty { nick }).takeIf { it.isNotEmpty() }?.let { authors += it }
                    }
                    "title-info" -> break
                }
            }
        }
        return Meta(title, authors.joinToString(", "))
    }

    // ── Запасной вариант: «Автор - Название.fb2» ─────────────────────────────

    fun fromFileName(fileName: String?): Meta? {
        if (fileName.isNullOrBlank()) return null
        var base: String = fileName
        for (ext in listOf(".zip", ".fb2", ".epub")) base = base.removeSuffix(ext).removeSuffix(ext.uppercase())
        base = base.replace('_', ' ').trim()
        val parts = base.split(" - ", " — ", " – ", limit = 2)
        return if (parts.size == 2) Meta(parts[1].trim(), parts[0].trim()) else Meta(base, "")
    }
}
