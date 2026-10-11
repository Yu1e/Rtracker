package com.readingtracker.service

import java.util.concurrent.ConcurrentHashMap

/**
 * Признаки того, что в читалке что-то происходит (перелистывание, касание).
 * Заполняется службой специальных возможностей, читается службой отслеживания.
 */
object ActivitySignal {
    @Volatile
    var accessibilityConnected = false

    private val last = ConcurrentHashMap<String, Long>()
    private val counts = ConcurrentHashMap<String, Int>()

    fun touch(pkg: String) {
        last[pkg] = System.currentTimeMillis()
        counts[pkg] = (counts[pkg] ?: 0) + 1
    }

    fun last(pkg: String): Long? = last[pkg]
    fun count(pkg: String): Int = counts[pkg] ?: 0
    fun snapshot(): Map<String, Pair<Int, Long?>> =
        counts.keys.associateWith { (counts[it] ?: 0) to last[it] }
}
